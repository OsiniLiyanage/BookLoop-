package lk.jiat.bookloop.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.CartAdapter;
import lk.jiat.bookloop.databinding.FragmentCartBinding;
import lk.jiat.bookloop.model.CartItem;
import lk.jiat.bookloop.model.Product;

/*
 * CartFragment.java
 * ─────────────────
 * Shows all items in the user's rental cart.
 *
 * CHANGE:
 *   - Bottom navigation bar is NOW VISIBLE while in the cart (we removed the old
 *     "hide bottom nav" code). Cart is a main destination (it's in the bottom nav),
 *     so it should behave like Home/Browse — the bottom bar stays visible.
 *   - The header still has a title and item count, but the back button is removed.
 *     Users navigate away using the bottom nav, just like every other main screen.
 *
 * WHY: Having the bottom nav disappear only makes sense for sub-pages like
 *      ProductDetails and Checkout — not for a main tab destination.
 */
public class CartFragment extends Fragment {

    private FragmentCartBinding binding;
    private List<CartItem> cartItems;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Bottom nav stays VISIBLE — cart is a top-level destination ────────
        // We do NOT hide it here. The old code hid it, making it look broken.
        // Bottom nav was already set visible by MainActivity when this fragment is loaded.

        // Load cart items from Firestore
        loadCart();

        // Proceed to checkout button
        binding.cartBtnProceed.setOnClickListener(v -> {
            CheckoutFragment checkoutFragment = new CheckoutFragment();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, checkoutFragment)
                    .addToBackStack(null)
                    .commit();
        });
    }

    // ─── Load all cart items for the current user from Firestore ─────────────
    private void loadCart() {
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (firebaseAuth.getCurrentUser() == null) {
            showEmptyState();
            return;
        }

        String uid = firebaseAuth.getCurrentUser().getUid();

        db.collection("users").document(uid).collection("cart")
                .get()
                .addOnSuccessListener(qds -> {
                    if (!qds.isEmpty()) {
                        cartItems = new ArrayList<>();

                        for (DocumentSnapshot ds : qds.getDocuments()) {
                            CartItem cartItem = ds.toObject(CartItem.class);
                            if (cartItem != null) {
                                cartItem.setDocumentId(ds.getId());
                                cartItems.add(cartItem);
                            }
                        }

                        setupRecyclerView(uid, db);
                        updateTotal();

                    } else {
                        showEmptyState();
                    }
                });
    }

    // ─── Set up RecyclerView with adapter and listeners ───────────────────────
    private void setupRecyclerView(String uid, FirebaseFirestore db) {
        binding.cartCartItems.setLayoutManager(new LinearLayoutManager(getContext()));

        CartAdapter adapter = new CartAdapter(cartItems);

        binding.cartItemCount.setText(cartItems.size() + " item" + (cartItems.size() == 1 ? "" : "s"));

        adapter.setOnCartItemChangeListener(cartItem -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("quantity", cartItem.getQuantity());
            updates.put("rentalWeeks", cartItem.getRentalWeeks());

            db.collection("users").document(uid)
                    .collection("cart")
                    .document(cartItem.getDocumentId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> updateTotal());
        });

        adapter.setOnRemoveListener(position -> {
            String documentId = cartItems.get(position).getDocumentId();

            db.collection("users").document(uid)
                    .collection("cart")
                    .document(documentId)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        cartItems.remove(position);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, cartItems.size());

                        binding.cartItemCount.setText(cartItems.size() + " item" + (cartItems.size() == 1 ? "" : "s"));

                        updateTotal();
                        Toast.makeText(getContext(), "Book removed from cart", Toast.LENGTH_SHORT).show();

                        if (cartItems.isEmpty()) showEmptyState();
                    });
        });

        binding.cartCartItems.setAdapter(adapter);
    }

    // ─── Calculate total rental cost across all cart items ───────────────────
    private void updateTotal() {
        if (cartItems == null || cartItems.isEmpty()) {
            binding.cartTextTotal.setText(String.format(Locale.US, "LKR %,.2f", 0.00));
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        List<String> productIds = new ArrayList<>();
        for (CartItem item : cartItems) {
            productIds.add(item.getProductId());
        }

        db.collection("products")
                .whereIn("productId", productIds)
                .get()
                .addOnSuccessListener(qds -> {
                    Map<String, Product> productMap = new HashMap<>();
                    qds.getDocuments().forEach(ds -> {
                        Product product = ds.toObject(Product.class);
                        if (product != null) {
                            productMap.put(product.getProductId(), product);
                        }
                    });

                    double total = 0;
                    for (CartItem cartItem : cartItems) {
                        Product product = productMap.get(cartItem.getProductId());
                        if (product != null) {
                            int weeks = cartItem.getRentalWeeks() > 0 ? cartItem.getRentalWeeks() : 1;
                            total += product.getPrice() * weeks * cartItem.getQuantity();
                        }
                    }

                    binding.cartTextTotal.setText(String.format(Locale.US, "LKR %,.2f", total));
                });
    }

    // ─── Show empty state view, hide recycler ─────────────────────────────────
    private void showEmptyState() {
        binding.cartCartItems.setVisibility(View.GONE);
        binding.cartEmptyState.setVisibility(View.VISIBLE);
        binding.cartTextTotal.setText(String.format(Locale.US, "LKR %,.2f", 0.00));
    }
}
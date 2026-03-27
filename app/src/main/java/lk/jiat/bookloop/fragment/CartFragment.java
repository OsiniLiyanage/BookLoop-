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

        // Hide bottom nav — cart is a focused checkout flow
        getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.GONE);

        // Back button — go to HomeFragment (not just popBackStack since cart may open from side nav)
        binding.cartBtnBack.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        });

        // Load cart items from Firestore
        loadCart();

        // Proceed to checkout button
        binding.cartBtnProceed.setOnClickListener(v -> {

            //Toast.makeText(getContext(), "Proceeding to checkout...", Toast.LENGTH_SHORT).show();
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
            // User not logged in — show empty state
            showEmptyState();
            return;
        }

        String uid = firebaseAuth.getCurrentUser().getUid();

        db.collection("users").document(uid).collection("cart")
                .get()
                .addOnSuccessListener(qds -> {
                    if (!qds.isEmpty()) {
                        cartItems = new ArrayList<>();

                        // Build cart list and capture document IDs for update/delete
                        for (DocumentSnapshot ds : qds.getDocuments()) {
                            CartItem cartItem = ds.toObject(CartItem.class);
                            if (cartItem != null) {
                                cartItem.setDocumentId(ds.getId()); // store doc ID for Firestore ops
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

        // Update item count badge in header
        binding.cartItemCount.setText(cartItems.size() + " item" + (cartItems.size() == 1 ? "" : "s"));

        // ── Listener: called when weeks OR copies change ──────────────────────
        adapter.setOnCartItemChangeListener(cartItem -> {
            // Save updated quantity and rentalWeeks to Firestore
            Map<String, Object> updates = new HashMap<>();
            updates.put("quantity", cartItem.getQuantity());
            updates.put("rentalWeeks", cartItem.getRentalWeeks()); // NEW — save rental weeks

            db.collection("users").document(uid)
                    .collection("cart")
                    .document(cartItem.getDocumentId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Recalculate total after any change
                        updateTotal();
                    });
        });

        // ── Listener: remove item from cart ──────────────────────────────────
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

                        // Update count badge
                        binding.cartItemCount.setText(cartItems.size() + " item" + (cartItems.size() == 1 ? "" : "s"));

                        updateTotal();
                        Toast.makeText(getContext(), "Book removed from cart", Toast.LENGTH_SHORT).show();

                        // Show empty state if no items left
                        if (cartItems.isEmpty()) showEmptyState();
                    });
        });

        binding.cartCartItems.setAdapter(adapter);
    }

    // ─── Calculate total rental cost across all cart items ───────────────────
    // total = sum of (price × weeks × copies) for each item
    private void updateTotal() {
        if (cartItems == null || cartItems.isEmpty()) {
            binding.cartTextTotal.setText(String.format(Locale.US, "LKR %,.2f", 0.00));
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Collect all product IDs to fetch prices in one query
        List<String> productIds = new ArrayList<>();
        for (CartItem item : cartItems) {
            productIds.add(item.getProductId());
        }

        db.collection("products")
                .whereIn("productId", productIds)
                .get()
                .addOnSuccessListener(qds -> {
                    // Build a map of productId → product for quick lookup
                    Map<String, Product> productMap = new HashMap<>();
                    qds.getDocuments().forEach(ds -> {
                        Product product = ds.toObject(Product.class);
                        if (product != null) {
                            productMap.put(product.getProductId(), product);
                        }
                    });

                    // Calculate grand total: price × weeks × copies for each item
                    double total = 0;
                    for (CartItem cartItem : cartItems) {
                        Product product = productMap.get(cartItem.getProductId());
                        if (product != null) {
                            // rentalWeeks defaults to 1 if somehow 0 (old cart items before update)
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

    // ─── Restore bottom nav when leaving cart ────────────────────────────────
    @Override
    public void onStop() {
        super.onStop();
        getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.GONE);
    }
}
package lk.jiat.bookloop.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.WishlistAdapter;
import lk.jiat.bookloop.databinding.FragmentWishlistBinding;
import lk.jiat.bookloop.helper.WishlistDatabase;
import lk.jiat.bookloop.model.Product;

/*
 * WishlistFragment.java
 * ─────────────────────
 * Shows saved books from local SQLite cache (WishlistDatabase).
 *
 * HOW WISHLIST WORKS:
 *   1. User opens a product → taps heart button → saved to SQLite + Firestore
 *   2. User opens Wishlist (side nav) → reads from SQLite (fast, offline)
 *   3. User can:
 *      a. Tap "View" → opens ProductDetailsFragment
 *      b. Tap "Move to Cart" → adds directly to Firestore cart, removes from wishlist
 *      c. Tap trash icon → removes from wishlist
 *
 * WHY SQLite here:
 *   Your class notes say SQLite is great for: offline access, fast local queries,
 *   structured data. Wishlist is exactly this — it works even without internet.
 *   The SQLite → WishlistDatabase pattern covers the "Data Storage: SQLite" topic.
 */
public class WishlistFragment extends Fragment {

    private static final String TAG = "WishlistFragment";
    private FragmentWishlistBinding binding;
    private WishlistDatabase wishlistDb;
    private WishlistAdapter adapter;
    private List<WishlistDatabase.WishlistItem> wishlistItems;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentWishlistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Open SQLite wishlist database
        wishlistDb = new WishlistDatabase(requireContext());

        binding.wishlistBtnBrowse.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new HomeFragment())
                        .commit());

        binding.wishlistBtnClear.setOnClickListener(v -> clearAllWishlist());

        loadWishlist();
    }

    // ── Load wishlist from SQLite (offline-first) ─────────────────────────────
    private void loadWishlist() {
        // Run DB read on background thread to avoid blocking UI
        new Thread(() -> {
            wishlistItems = wishlistDb.getAllWishlistItems();

            requireActivity().runOnUiThread(() -> {
                if (wishlistItems.isEmpty()) {
                    showEmpty();
                    syncFromFirestore(); // pull from cloud if empty locally
                    return;
                }
                showList();
            });
        }).start();
    }

    private void showList() {
        binding.wishlistEmptyState.setVisibility(View.GONE);
        binding.wishlistRecycler.setVisibility(View.VISIBLE);
        binding.wishlistBtnClear.setVisibility(View.VISIBLE);
        binding.wishlistCountLabel.setText(wishlistItems.size() + " saved book" + (wishlistItems.size() == 1 ? "" : "s"));

        adapter = new WishlistAdapter(wishlistItems, new WishlistAdapter.OnWishlistActionListener() {

            // "View" button → open ProductDetailsFragment
            @Override
            public void onRentClick(WishlistDatabase.WishlistItem item) {
                Bundle args = new Bundle();
                args.putString("productId", item.productId);
                ProductDetailsFragment fragment = new ProductDetailsFragment();
                fragment.setArguments(args);
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit();
            }

            // "Move to Cart" button → add to Firestore cart + remove from wishlist
            @Override
            public void onMoveToCartClick(WishlistDatabase.WishlistItem item, int position) {
                moveToCart(item, position);
            }

            // Remove button (trash icon) → remove from SQLite + Firestore wishlist
            @Override
            public void onRemoveClick(WishlistDatabase.WishlistItem item, int position) {
                new Thread(() -> {
                    wishlistDb.removeFromWishlist(item.productId);
                    requireActivity().runOnUiThread(() -> {
                        adapter.removeAt(position);
                        binding.wishlistCountLabel.setText(wishlistItems.size() + " saved book" + (wishlistItems.size() == 1 ? "" : "s"));
                        if (wishlistItems.isEmpty()) showEmpty();
                        Toast.makeText(getContext(), "Removed from wishlist", Toast.LENGTH_SHORT).show();
                    });
                }).start();

                removeFromFirestoreWishlist(item.productId);
            }
        });

        binding.wishlistRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.wishlistRecycler.setAdapter(adapter);
    }

    // ── Move to Cart: add to Firestore cart, then remove from wishlist ────────
    //
    // How it works:
    //   1. Create a CartItem with quantity=1, weeks=1, no attributes
    //   2. Save it to Firestore users/{uid}/cart
    //   3. On success: remove from SQLite wishlist + remove from Firestore wishlist
    //   4. Update RecyclerView — item disappears from wishlist list
    //
    private void moveToCart(WishlistDatabase.WishlistItem item, int position) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please log in to add to cart", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        // Build a basic cart document
        Map<String, Object> cartData = new HashMap<>();
        cartData.put("productId", item.productId);
        cartData.put("quantity", 1);
        cartData.put("rentalWeeks", 1);
        cartData.put("attributes", new ArrayList<>());

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("cart").document()
                .set(cartData)
                .addOnSuccessListener(unused -> {
                    // Saved to cart — now remove from wishlist
                    new Thread(() -> {
                        wishlistDb.removeFromWishlist(item.productId);
                        requireActivity().runOnUiThread(() -> {
                            adapter.removeAt(position);
                            binding.wishlistCountLabel.setText(wishlistItems.size() + " saved book" + (wishlistItems.size() == 1 ? "" : "s"));
                            if (wishlistItems.isEmpty()) showEmpty();
                            Toast.makeText(getContext(), "Moved to Cart!", Toast.LENGTH_SHORT).show();
                        });
                    }).start();

                    removeFromFirestoreWishlist(item.productId);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed to add to cart", Toast.LENGTH_SHORT).show());
    }

    private void showEmpty() {
        binding.wishlistRecycler.setVisibility(View.GONE);
        binding.wishlistEmptyState.setVisibility(View.VISIBLE);
        binding.wishlistBtnClear.setVisibility(View.GONE);
        binding.wishlistCountLabel.setText("0 saved books");
    }

    private void clearAllWishlist() {
        new Thread(() -> {
            for (WishlistDatabase.WishlistItem item : new ArrayList<>(wishlistItems)) {
                wishlistDb.removeFromWishlist(item.productId);
                removeFromFirestoreWishlist(item.productId);
            }
            requireActivity().runOnUiThread(() -> {
                wishlistItems.clear();
                showEmpty();
                Toast.makeText(getContext(), "Wishlist cleared", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // ── Remove from Firestore wishlist subcollection ───────────────────────────
    private void removeFromFirestoreWishlist(String productId) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("wishlist").document(productId)
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Firestore wishlist remove failed: " + e.getMessage()));
    }

    // ── Sync from Firestore if SQLite is empty (after reinstall etc.) ─────────
    private void syncFromFirestore() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("wishlist")
                .get()
                .addOnSuccessListener(qds -> {
                    if (qds.isEmpty()) return;

                    new Thread(() -> {
                        for (com.google.firebase.firestore.DocumentSnapshot ds : qds.getDocuments()) {
                            Product p = ds.toObject(Product.class);
                            if (p != null) {
                                String img = (p.getImages() != null && !p.getImages().isEmpty())
                                        ? p.getImages().get(0) : "";
                                wishlistDb.addToWishlist(p.getProductId(), p.getTitle(),
                                        p.getAuthor(), p.getPrice(), img, p.getCategoryId());
                            }
                        }
                        wishlistItems = wishlistDb.getAllWishlistItems();
                        requireActivity().runOnUiThread(() -> {
                            if (!wishlistItems.isEmpty()) showList();
                        });
                    }).start();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Wishlist Firestore sync failed: " + e.getMessage()));
    }
}
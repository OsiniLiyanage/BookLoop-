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
import com.google.firebase.auth.FirebaseUser;
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
 * BUG FIX — Per-user wishlist isolation:
 *   Old code called getAllWishlistItems() / removeFromWishlist() / addToWishlist()
 *   without a userId. Since SQLite is per-device (not per-account), this meant
 *   every logged-in user on the same phone saw the same 4 books in their wishlist.
 *
 *   Fix: get the current Firebase Auth uid at the start of every method that
 *   touches the database, and pass it through. WishlistDatabase v2 stores a
 *   user_id column and filters by it, so each user's data is completely separate.
 *
 *   If no user is logged in, we show an empty wishlist instead of crashing.
 */
public class WishlistFragment extends Fragment {

    private static final String TAG = "WishlistFragment";
    private FragmentWishlistBinding binding;
    private WishlistDatabase wishlistDb;
    private WishlistAdapter adapter;
    private List<WishlistDatabase.WishlistItem> wishlistItems = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentWishlistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        wishlistDb = new WishlistDatabase(requireContext());

        binding.wishlistBtnBrowse.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new HomeFragment())
                        .commit());

        binding.wishlistBtnClear.setOnClickListener(v -> clearAllWishlist());

        loadWishlist();
    }

    // ── Get current userId safely — returns null if not logged in ─────────────
    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ── Load THIS user's wishlist from SQLite ─────────────────────────────────
    private void loadWishlist() {
        final String userId = getCurrentUserId();

        // If nobody is logged in, show empty rather than crashing
        if (userId == null) {
            showEmpty();
            return;
        }

        new Thread(() -> {
            // FIX: pass userId so we only load THIS user's saved books
            List<WishlistDatabase.WishlistItem> loaded = wishlistDb.getAllWishlistItems(userId);

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || binding == null) return;
                wishlistItems = loaded;
                if (wishlistItems.isEmpty()) {
                    showEmpty();
                    syncFromFirestore(userId);
                } else {
                    showList(userId);
                }
            });
        }).start();
    }

    private void showList(final String userId) {
        binding.wishlistEmptyState.setVisibility(View.GONE);
        binding.wishlistRecycler.setVisibility(View.VISIBLE);
        binding.wishlistBtnClear.setVisibility(View.VISIBLE);
        updateCountLabel();

        adapter = new WishlistAdapter(wishlistItems, new WishlistAdapter.OnWishlistActionListener() {

            @Override
            public void onRentClick(WishlistDatabase.WishlistItem item) {
                if (!isAdded()) return;
                Bundle args = new Bundle();
                args.putString("productId", item.productId);
                ProductDetailsFragment fragment = new ProductDetailsFragment();
                fragment.setArguments(args);
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit();
            }

            @Override
            public void onMoveToCartClick(WishlistDatabase.WishlistItem item, int position) {
                if (adapter == null) return;
                moveToCart(userId, item, position);
            }

            @Override
            public void onRemoveClick(WishlistDatabase.WishlistItem item, int position) {
                if (position < 0 || position >= wishlistItems.size()) return;

                new Thread(() -> {
                    // FIX: pass userId — only removes from THIS user's wishlist
                    wishlistDb.removeFromWishlist(userId, item.productId);

                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded() || binding == null || adapter == null) return;
                        if (position >= 0 && position < wishlistItems.size()) {
                            adapter.removeAt(position);
                            updateCountLabel();
                            if (wishlistItems.isEmpty()) showEmpty();
                        }
                        Toast.makeText(getContext(), "Removed from wishlist", Toast.LENGTH_SHORT).show();
                    });
                }).start();

                removeFromFirestoreWishlist(item.productId);
            }
        });

        binding.wishlistRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.wishlistRecycler.setAdapter(adapter);
    }

    // ── Move to Cart ──────────────────────────────────────────────────────────
    private void moveToCart(final String userId, WishlistDatabase.WishlistItem item, int position) {
        if (userId == null) {
            Toast.makeText(getContext(), "Please log in to add to cart", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> cartData = new HashMap<>();
        cartData.put("productId",   item.productId);
        cartData.put("quantity",    1);
        cartData.put("rentalWeeks", 1);
        cartData.put("attributes",  new ArrayList<>());

        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("cart").document()
                .set(cartData)
                .addOnSuccessListener(unused -> {
                    new Thread(() -> {
                        // FIX: pass userId — only removes from THIS user's wishlist
                        wishlistDb.removeFromWishlist(userId, item.productId);

                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded() || binding == null || adapter == null) return;
                            if (position >= 0 && position < wishlistItems.size()) {
                                adapter.removeAt(position);
                                updateCountLabel();
                                if (wishlistItems.isEmpty()) showEmpty();
                            }
                            Toast.makeText(getContext(), "Moved to Cart!", Toast.LENGTH_SHORT).show();
                        });
                    }).start();

                    removeFromFirestoreWishlist(item.productId);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), "Failed to add to cart", Toast.LENGTH_SHORT).show();
                });
    }

    private void showEmpty() {
        if (binding == null) return;
        binding.wishlistRecycler.setVisibility(View.GONE);
        binding.wishlistEmptyState.setVisibility(View.VISIBLE);
        binding.wishlistBtnClear.setVisibility(View.GONE);
        binding.wishlistCountLabel.setText("0 saved books");
    }

    private void updateCountLabel() {
        if (binding == null) return;
        int count = wishlistItems.size();
        binding.wishlistCountLabel.setText(count + " saved book" + (count == 1 ? "" : "s"));
    }

    private void clearAllWishlist() {
        if (wishlistItems == null || wishlistItems.isEmpty()) return;
        final String userId = getCurrentUserId();
        if (userId == null) return;

        new Thread(() -> {
            for (WishlistDatabase.WishlistItem item : new ArrayList<>(wishlistItems)) {
                // FIX: pass userId — only clears THIS user's items
                wishlistDb.removeFromWishlist(userId, item.productId);
                removeFromFirestoreWishlist(item.productId);
            }

            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || binding == null) return;
                wishlistItems.clear();
                if (adapter != null) adapter.notifyDataSetChanged();
                showEmpty();
                Toast.makeText(getContext(), "Wishlist cleared", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void removeFromFirestoreWishlist(String productId) {
        final String userId = getCurrentUserId();
        if (userId == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("wishlist").document(productId)
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Firestore wishlist remove failed: " + e.getMessage()));
    }

    // ── Sync from Firestore if SQLite is empty (after reinstall etc.) ──────────
    private void syncFromFirestore(final String userId) {
        if (userId == null) return;

        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
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
                                // FIX: pass userId so synced items are tagged to THIS user
                                wishlistDb.addToWishlist(userId, p.getProductId(), p.getTitle(),
                                        p.getAuthor(), p.getPrice(), img, p.getCategoryId());
                            }
                        }

                        List<WishlistDatabase.WishlistItem> synced =
                                wishlistDb.getAllWishlistItems(userId);

                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded() || binding == null) return;
                            wishlistItems = synced;
                            if (!wishlistItems.isEmpty()) showList(userId);
                        });
                    }).start();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Wishlist Firestore sync failed: " + e.getMessage()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
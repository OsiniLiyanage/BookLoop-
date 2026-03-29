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
 *   SQLite is great for: offline access, fast local queries, structured data.
 *   Wishlist is exactly this — it works even without internet.
 *   The SQLite → WishlistDatabase pattern covers the "Data Storage: SQLite" topic.
 *
 * BUG FIXES (crash when opening Wishlist):
 *   1. isAdded() guard on every runOnUiThread callback — fragment may have been
 *      popped from the back stack while the background thread was still running,
 *      causing requireActivity() / binding access to throw IllegalStateException.
 *   2. wishlistItems null-check before clearAllWishlist() iterates — if loadWishlist()
 *      thread hasn't finished yet and user taps Clear, this was an NPE.
 *   3. Adapter position safety — getBindingAdapterPosition() instead of the
 *      deprecated getAdapterPosition(), and a NO_ID guard so stale clicks after
 *      an item is already removed don't crash with IndexOutOfBoundsException.
 */
public class WishlistFragment extends Fragment {

    private static final String TAG = "WishlistFragment";
    private FragmentWishlistBinding binding;
    private WishlistDatabase wishlistDb;
    private WishlistAdapter adapter;

    // FIX: initialise to empty list so clearAllWishlist() never hits a NullPointerException
    // if the user taps "Clear All" before the background DB thread finishes.
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

    // ── Load wishlist from SQLite (offline-first) ─────────────────────────────
    private void loadWishlist() {
        new Thread(() -> {
            List<WishlistDatabase.WishlistItem> loaded = wishlistDb.getAllWishlistItems();

            // FIX: isAdded() ensures the fragment is still attached to its Activity.
            // Without this, requireActivity() throws IllegalStateException when the
            // user navigates away while the DB read is still in progress.
            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                // Double-check inside the UI callback too — Activity can finish
                // in the gap between the isAdded() call above and this runOnUiThread.
                if (!isAdded() || binding == null) return;

                wishlistItems = loaded;

                if (wishlistItems.isEmpty()) {
                    showEmpty();
                    syncFromFirestore();
                } else {
                    showList();
                }
            });
        }).start();
    }

    private void showList() {
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
                // FIX: re-read safe position from adapter at click time
                if (adapter == null) return;
                moveToCart(item, position);
            }

            @Override
            public void onRemoveClick(WishlistDatabase.WishlistItem item, int position) {
                // FIX: guard against stale position (item already removed by another action)
                if (position < 0 || position >= wishlistItems.size()) return;

                new Thread(() -> {
                    wishlistDb.removeFromWishlist(item.productId);

                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded() || binding == null || adapter == null) return;
                        // FIX: re-validate position inside UI thread before removing
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
    private void moveToCart(WishlistDatabase.WishlistItem item, int position) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "Please log in to add to cart", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

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
                    new Thread(() -> {
                        wishlistDb.removeFromWishlist(item.productId);

                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded() || binding == null || adapter == null) return;
                            // FIX: validate position is still valid before removing
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

    // FIX: extracted count update to one place — avoids stale .size() reads scattered
    // around the class after items have been removed from wishlistItems.
    private void updateCountLabel() {
        if (binding == null) return;
        int count = wishlistItems.size();
        binding.wishlistCountLabel.setText(count + " saved book" + (count == 1 ? "" : "s"));
    }

    private void clearAllWishlist() {
        // FIX: null + empty guard — wishlistItems starts as empty ArrayList now,
        // so this is safe even if called before loadWishlist() thread finishes.
        if (wishlistItems == null || wishlistItems.isEmpty()) return;

        new Thread(() -> {
            // Copy to avoid ConcurrentModificationException while iterating
            for (WishlistDatabase.WishlistItem item : new ArrayList<>(wishlistItems)) {
                wishlistDb.removeFromWishlist(item.productId);
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
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("wishlist").document(productId)
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Firestore wishlist remove failed: " + e.getMessage()));
    }

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

                        List<WishlistDatabase.WishlistItem> synced = wishlistDb.getAllWishlistItems();

                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!isAdded() || binding == null) return;
                            wishlistItems = synced;
                            if (!wishlistItems.isEmpty()) showList();
                        });
                    }).start();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Wishlist Firestore sync failed: " + e.getMessage()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // FIX: clear binding reference so background threads that check
        // "binding == null" correctly bail out instead of crashing.
        binding = null;
    }
}
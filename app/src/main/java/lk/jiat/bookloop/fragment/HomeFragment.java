package lk.jiat.bookloop.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.CategoryAdapter;
import lk.jiat.bookloop.adapter.ProductSliderAdapter;
import lk.jiat.bookloop.adapter.SectionAdapter;
import lk.jiat.bookloop.databinding.FragmentHomeBinding;
import lk.jiat.bookloop.helper.WishlistDatabase;
import lk.jiat.bookloop.model.Category;
import lk.jiat.bookloop.model.Product;

/*
 * HomeFragment.java — fixed version
 *
 * FIXES IN THIS VERSION:
 *   1. Top Rated: removed .orderBy("rating") from Firestore query — that caused a 400 error
 *      because it required a composite index. Now we sort in Java after loading.
 *   2. Search: connected to the search bar in the toolbar. User types → results shown live
 *      using Firestore prefix search on the title field.
 *   3. Banner: ProductSliderAdapter is already fixed to resolve Storage paths to URLs.
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private FragmentHomeBinding binding;

    // Handler auto-scrolls the banner every 3 seconds
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerRunnable;
    private int bannerPage = 0;

    // SQLite for recently viewed
    private WishlistDatabase wishlistDb;

    // Search state
    private boolean searchActive = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        wishlistDb = new WishlistDatabase(requireContext());

        setupBannerSlider();
        loadCategoryGrid();
        loadTopRatedSection();
        loadNewArrivalsSection();
        loadRecentlyViewedSection();

        // "See All" categories button
        binding.homeBtnSeeAllCategories.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new CategoryFragment())
                        .addToBackStack(null)
                        .commit());
    }

    // ── 1. Banner Slider ──────────────────────────────────────────────────────
    // Uses Firebase Storage paths. ProductSliderAdapter resolves them to download URLs.
    // Upload images to Firebase Storage under "banners/" folder named:
    //   banner_1.jpg, banner_2.jpg, banner_3.jpg
    private void setupBannerSlider() {
        List<String> bannerImages = Arrays.asList(
                "banners/banner_1.jpg",
                "banners/banner_2.jpg",
                "banners/banner_3.jpg"
        );

        ProductSliderAdapter sliderAdapter = new ProductSliderAdapter(bannerImages);
        binding.homeBannerSlider.setAdapter(sliderAdapter);
        binding.homeBannerDots.attachTo(binding.homeBannerSlider);

        // Auto-scroll every 3 seconds (Multitasking: Handler + Runnable)
        bannerRunnable = () -> {
            bannerPage = (bannerPage + 1) % bannerImages.size();
            binding.homeBannerSlider.setCurrentItem(bannerPage, true);
            bannerHandler.postDelayed(bannerRunnable, 3000);
        };
        bannerHandler.postDelayed(bannerRunnable, 3000);
    }

    // ── 2. Category Grid ─────────────────────────────────────────────────────
    private void loadCategoryGrid() {
        FirebaseFirestore.getInstance()
                .collection("categories")
                .limit(4)
                .get()
                .addOnSuccessListener(qds -> {
                    if (qds.isEmpty()) return;
                    List<Category> categories = qds.toObjects(Category.class);

                    binding.homeCategoryGrid.setLayoutManager(new GridLayoutManager(getContext(), 2));
                    binding.homeCategoryGrid.setNestedScrollingEnabled(false);

                    CategoryAdapter adapter = new CategoryAdapter(categories, category -> {
                        Bundle bundle = new Bundle();
                        bundle.putString("categoryId", category.getCategoryId());
                        bundle.putString("categoryName", category.getName());
                        ListingFragment fragment = new ListingFragment();
                        fragment.setArguments(bundle);
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, fragment)
                                .addToBackStack(null)
                                .commit();
                    });

                    binding.homeCategoryGrid.setAdapter(adapter);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Category load failed: " + e.getMessage()));
    }

    // ── 3. Top Rated Books ────────────────────────────────────────────────────
    //
    // FIX: Old version used .whereGreaterThanOrEqualTo("rating", 4.0).orderBy("rating")
    //      which caused a 400 Bad Request because Firestore needs a composite index for this.
    //      New version: loads all products, then filters & sorts in Java — no index needed.
    private void loadTopRatedSection() {
        FirebaseFirestore.getInstance()
                .collection("products")
                .get()
                .addOnSuccessListener(qds -> {
                    if (qds.isEmpty()) {
                        binding.homeTopRatedSection.getRoot().setVisibility(View.GONE);
                        return;
                    }

                    // Get all products, then filter rating >= 4.0 in Java
                    List<Product> all = qds.toObjects(Product.class);
                    List<Product> topRated = new ArrayList<>();
                    for (Product p : all) {
                        if (p.getRating() >= 4.0f) {
                            topRated.add(p);
                        }
                    }

                    if (topRated.isEmpty()) {
                        // No books with rating >= 4 yet — show all books instead
                        topRated = all;
                    }

                    // Sort by rating descending in Java (replaces .orderBy in Firestore)
                    Collections.sort(topRated, (a, b) -> Float.compare(b.getRating(), a.getRating()));

                    // Take top 10
                    if (topRated.size() > 10) topRated = topRated.subList(0, 10);

                    binding.homeTopRatedSection.itemSectionTitle.setText("⭐ Top Rated Books");
                    binding.homeTopRatedSection.itemSectionContainer.setLayoutManager(
                            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                    binding.homeTopRatedSection.itemSectionContainer.setAdapter(
                            new SectionAdapter(topRated, p -> openProduct(p.getProductId())));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Top rated load failed: " + e.getMessage());
                    binding.homeTopRatedSection.getRoot().setVisibility(View.GONE);
                });
    }

    // ── 4. New Arrivals ───────────────────────────────────────────────────────
    private void loadNewArrivalsSection() {
        FirebaseFirestore.getInstance()
                .collection("products")
                .limit(10)
                .get()
                .addOnSuccessListener(qds -> {
                    if (qds.isEmpty()) {
                        binding.homeNewArrivalsSection.getRoot().setVisibility(View.GONE);
                        return;
                    }
                    List<Product> products = qds.toObjects(Product.class);
                    binding.homeNewArrivalsSection.itemSectionTitle.setText("🆕 New Arrivals");
                    binding.homeNewArrivalsSection.itemSectionContainer.setLayoutManager(
                            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                    binding.homeNewArrivalsSection.itemSectionContainer.setAdapter(
                            new SectionAdapter(products, p -> openProduct(p.getProductId())));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "New arrivals load failed: " + e.getMessage());
                    binding.homeNewArrivalsSection.getRoot().setVisibility(View.GONE);
                });
    }

    // ── 5. Recently Viewed (local SQLite) ─────────────────────────────────────
    private void loadRecentlyViewedSection() {
        new Thread(() -> {
            List<WishlistDatabase.WishlistItem> recentItems = wishlistDb.getRecentlyViewed(10);

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (recentItems.isEmpty()) {
                    binding.homeRecentlyViewedSection.getRoot().setVisibility(View.GONE);
                    return;
                }

                List<Product> stubs = new ArrayList<>();
                for (WishlistDatabase.WishlistItem item : recentItems) {
                    Product p = new Product();
                    p.setProductId(item.productId);
                    p.setTitle(item.title);
                    p.setAuthor(item.author);
                    p.setPrice(item.price);
                    if (item.imageUrl != null) {
                        p.setImages(new ArrayList<>(List.of(item.imageUrl)));
                    }
                    stubs.add(p);
                }

                binding.homeRecentlyViewedSection.itemSectionTitle.setText("🕐 Recently Viewed");
                binding.homeRecentlyViewedSection.itemSectionContainer.setLayoutManager(
                        new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                binding.homeRecentlyViewedSection.itemSectionContainer.setAdapter(
                        new SectionAdapter(stubs, p -> openProduct(p.getProductId())));
            });
        }).start();
    }

    // ── Search (called from MainActivity when user types in search bar) ───────
    //
    // Firestore prefix search: finds all products where title starts with the query.
    // Works by using: >= query  AND  <= query + "\uf8ff"
    // "\uf8ff" is the highest Unicode character — acts as a wildcard for "anything after query"
    // This is a standard Firestore search pattern (no extra index needed).
    //
    // Example: query="Har" matches "Harry Potter", "Hardware Hacker", etc.
    public void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            // Empty search — restore normal home content
            binding.homeSearchResultsSection.getRoot().setVisibility(View.GONE);
            binding.homeTopRatedSection.getRoot().setVisibility(View.VISIBLE);
            binding.homeNewArrivalsSection.getRoot().setVisibility(View.VISIBLE);
            return;
        }

        String trimmed = query.trim();

        // Hide the normal sections while searching
        binding.homeTopRatedSection.getRoot().setVisibility(View.GONE);
        binding.homeNewArrivalsSection.getRoot().setVisibility(View.GONE);
        binding.homeRecentlyViewedSection.getRoot().setVisibility(View.GONE);
        binding.homeSearchResultsSection.getRoot().setVisibility(View.VISIBLE);
        binding.homeSearchResultsSection.itemSectionTitle.setText("🔍 Results for \"" + trimmed + "\"");

        FirebaseFirestore.getInstance()
                .collection("products")
                .orderBy("title")
                .startAt(trimmed)
                .endAt(trimmed + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener(qds -> {
                    if (qds.isEmpty()) {
                        binding.homeSearchResultsSection.itemSectionTitle
                                .setText("No books found for \"" + trimmed + "\"");
                        binding.homeSearchResultsSection.itemSectionContainer
                                .setAdapter(null);
                        return;
                    }

                    List<Product> results = qds.toObjects(Product.class);
                    binding.homeSearchResultsSection.itemSectionContainer.setLayoutManager(
                            new LinearLayoutManager(getContext()));
                    binding.homeSearchResultsSection.itemSectionContainer.setAdapter(
                            new SectionAdapter(results, p -> openProduct(p.getProductId())));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Search failed: " + e.getMessage());
                    binding.homeSearchResultsSection.itemSectionTitle.setText("Search failed. Try again.");
                });
    }

    // ── Helper: open product details ─────────────────────────────────────────
    private void openProduct(String productId) {
        Bundle bundle = new Bundle();
        bundle.putString("productId", productId);
        ProductDetailsFragment fragment = new ProductDetailsFragment();
        fragment.setArguments(bundle);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    // ── Lifecycle: pause/resume banner auto-scroll ────────────────────────────
    @Override
    public void onPause() {
        super.onPause();
        bannerHandler.removeCallbacks(bannerRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bannerRunnable != null) {
            bannerHandler.postDelayed(bannerRunnable, 3000);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bannerHandler.removeCallbacks(bannerRunnable);
        binding = null;
    }
}
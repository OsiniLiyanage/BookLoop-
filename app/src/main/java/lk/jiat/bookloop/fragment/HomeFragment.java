package lk.jiat.bookloop.fragment;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
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

// HomeFragment — main landing screen of BookLoop.
// SENSOR FEATURE: Implements SensorEventListener to detect phone shake.
// WHY: When user shakes the phone, all book sections refresh from Firestore.
//      This is a natural, physical gesture — like "shake to see what's new".
// COVERS: Sensors assignment requirement (Accelerometer).
public class HomeFragment extends Fragment implements SensorEventListener {

    private static final String TAG = "HomeFragment";
    private FragmentHomeBinding binding;

    // ── Sensor fields ─────────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final float SHAKE_THRESHOLD = 12f;
    private long lastShakeTime = 0;
    private static final long SHAKE_COOLDOWN_MS = 1500;

    // ── Banner auto-scroll ────────────────────────────────────────────────────
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerRunnable;
    private int bannerPage = 0;

    // ── SQLite recently viewed ────────────────────────────────────────────────
    private WishlistDatabase wishlistDb;

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

        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            Log.w(TAG, "Accelerometer not available on this device");
        }

        setupBannerSlider();
        loadCategoryGrid();
        loadTopRatedSection();
        loadNewArrivalsSection();
        loadRecentlyViewedSection();

        // "See All" categories button → CategoryFragment
        binding.homeBtnSeeAllCategories.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new CategoryFragment())
                        .addToBackStack(null)
                        .commit());
    }

    // ── SensorEventListener ───────────────────────────────────────────────────
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt((x * x) + (y * y) + (z * z));

        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now;
                onShakeDetected();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { /* not needed */ }

    private void onShakeDetected() {
        Log.i(TAG, "Shake detected! Refreshing book sections...");
        Toast.makeText(getContext(), "🔄 Refreshing books...", Toast.LENGTH_SHORT).show();
        refreshAllSections();
    }

    private void refreshAllSections() {
        loadTopRatedSection();
        loadNewArrivalsSection();
        loadRecentlyViewedSection();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (bannerRunnable != null) {
            bannerHandler.postDelayed(bannerRunnable, 3000);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        bannerHandler.removeCallbacks(bannerRunnable);
    }

    // ── Banner Slider — loads image URLs from Firestore "banners" collection ──
    // FIX: was hardcoding Storage paths like "banners/banner_1.jpg" which never
    //      resolved to actual images. Now reads full https:// URLs from Firestore
    //      so admin can manage banners without touching the app code.
    // Firestore structure: banners/{docId} = { imageUrl: "https://...", order: 1 }
    private void setupBannerSlider() {
        FirebaseFirestore.getInstance()
                .collection("banners")
                .orderBy("order")
                .get()
                .addOnSuccessListener(qds -> {
                    if (binding == null) return;

                    List<String> urls = new ArrayList<>();
                    for (var doc : qds.getDocuments()) {
                        String url = doc.getString("imageUrl");
                        if (url != null && !url.isEmpty()) {
                            urls.add(url);
                        }
                    }

                    if (urls.isEmpty()) {
                        // No banners saved yet — hide the slider area gracefully
                        binding.homeBannerSlider.setVisibility(View.GONE);
                        binding.homeBannerDots.setVisibility(View.GONE);
                        return;
                    }

                    binding.homeBannerSlider.setVisibility(View.VISIBLE);
                    binding.homeBannerDots.setVisibility(View.VISIBLE);

                    ProductSliderAdapter sliderAdapter = new ProductSliderAdapter(urls);
                    binding.homeBannerSlider.setAdapter(sliderAdapter);
                    binding.homeBannerDots.attachTo(binding.homeBannerSlider);

                    // Auto-scroll every 3 seconds
                    final int count = urls.size();
                    bannerRunnable = () -> {
                        if (binding == null) return;
                        bannerPage = (bannerPage + 1) % count;
                        binding.homeBannerSlider.setCurrentItem(bannerPage, true);
                        bannerHandler.postDelayed(bannerRunnable, 3000);
                    };
                    bannerHandler.postDelayed(bannerRunnable, 3000);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Banners load failed: " + e.getMessage());
                    if (binding != null) {
                        binding.homeBannerSlider.setVisibility(View.GONE);
                        binding.homeBannerDots.setVisibility(View.GONE);
                    }
                });
    }

    private void loadCategoryGrid() {
        FirebaseFirestore.getInstance()
                .collection("categories")
                .limit(4)
                .get()
                .addOnSuccessListener(qds -> {
                    if (binding == null || qds.isEmpty()) return;
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

    private void loadTopRatedSection() {
        FirebaseFirestore.getInstance()
                .collection("products")
                .get()
                .addOnSuccessListener(qds -> {
                    if (binding == null) return;
                    if (qds.isEmpty()) {
                        binding.homeTopRatedSection.getRoot().setVisibility(View.GONE);
                        return;
                    }

                    List<Product> all = qds.toObjects(Product.class);
                    List<Product> topRated = new ArrayList<>();
                    for (Product p : all) {
                        if (p.getRating() >= 4.0f) topRated.add(p);
                    }
                    // If no books have a rating yet, show all books sorted by rating
                    if (topRated.isEmpty()) topRated = all;

                    Collections.sort(topRated, (a, b) -> Float.compare(b.getRating(), a.getRating()));
                    if (topRated.size() > 10) topRated = topRated.subList(0, 10);

                    final List<Product> finalList = topRated;

                    binding.homeTopRatedSection.itemSectionTitle.setText("⭐ Top Rated Books");

                    // "See All" → AllBooksFragment in top-rated mode
                    binding.homeTopRatedSection.itemSectionSeeAll.setOnClickListener(v ->
                            openAllBooks("top"));

                    binding.homeTopRatedSection.itemSectionContainer.setLayoutManager(
                            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                    binding.homeTopRatedSection.itemSectionContainer.setAdapter(
                            new SectionAdapter(finalList, p -> openProduct(p.getProductId())));
                    binding.homeTopRatedSection.getRoot().setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Top rated load failed: " + e.getMessage());
                    if (binding != null)
                        binding.homeTopRatedSection.getRoot().setVisibility(View.GONE);
                });
    }

    // FIX: New Arrivals was broken because .orderBy("createdAt").limit(10) requires
    //      a Firestore index that wasn't created. Now we fetch all products and sort
    //      by createdAt in Java — no index needed, always works.
    private void loadNewArrivalsSection() {
        FirebaseFirestore.getInstance()
                .collection("products")
                .get()
                .addOnSuccessListener(qds -> {
                    if (binding == null) return;
                    if (qds.isEmpty()) {
                        binding.homeNewArrivalsSection.getRoot().setVisibility(View.GONE);
                        return;
                    }

                    List<Product> products = qds.toObjects(Product.class);

                    // Sort by createdAt descending in Java (newest first)
                    // Products without createdAt go to the end
                    products.sort((a, b) -> {
                        if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });

                    if (products.size() > 10) products = products.subList(0, 10);

                    final List<Product> finalList = products;

                    binding.homeNewArrivalsSection.itemSectionTitle.setText("🆕 New Arrivals");

                    // "See All" → AllBooksFragment in new-arrivals mode
                    binding.homeNewArrivalsSection.itemSectionSeeAll.setOnClickListener(v ->
                            openAllBooks("new"));

                    binding.homeNewArrivalsSection.itemSectionContainer.setLayoutManager(
                            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                    binding.homeNewArrivalsSection.itemSectionContainer.setAdapter(
                            new SectionAdapter(finalList, p -> openProduct(p.getProductId())));
                    binding.homeNewArrivalsSection.getRoot().setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "New arrivals load failed: " + e.getMessage());
                    if (binding != null)
                        binding.homeNewArrivalsSection.getRoot().setVisibility(View.GONE);
                });
    }

    private void loadRecentlyViewedSection() {
        // FIX: get userId so we only show THIS user's recently viewed books.
        // Old code called getRecentlyViewed(10) without a userId which meant
        // everyone on the same device saw the same recently viewed list.
        com.google.firebase.auth.FirebaseUser firebaseUser =
                FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            // Not logged in — hide the section
            if (binding != null)
                binding.homeRecentlyViewedSection.getRoot().setVisibility(View.GONE);
            return;
        }
        final String userId = firebaseUser.getUid();

        new Thread(() -> {
            // FIX: pass userId — loads only this user's recently viewed items
            List<WishlistDatabase.WishlistItem> recentItems = wishlistDb.getRecentlyViewed(userId, 10);
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (binding == null) return;
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
                binding.homeRecentlyViewedSection.getRoot().setVisibility(View.VISIBLE);
            });
        }).start();
    }

    // Called from MainActivity search bar whenever user types
    public void performSearch(String query) {
        if (binding == null) return;

        if (query == null || query.trim().isEmpty()) {
            binding.homeSearchResultsSection.getRoot().setVisibility(View.GONE);
            binding.homeTopRatedSection.getRoot().setVisibility(View.VISIBLE);
            binding.homeNewArrivalsSection.getRoot().setVisibility(View.VISIBLE);
            return;
        }

        String trimmed = query.trim();
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
                    if (binding == null) return;
                    if (qds.isEmpty()) {
                        binding.homeSearchResultsSection.itemSectionTitle
                                .setText("No books found for \"" + trimmed + "\"");
                        binding.homeSearchResultsSection.itemSectionContainer.setAdapter(null);
                        return;
                    }
                    List<Product> results = qds.toObjects(Product.class);
                    binding.homeSearchResultsSection.itemSectionContainer.setLayoutManager(
                            new LinearLayoutManager(getContext()));
                    binding.homeSearchResultsSection.itemSectionContainer.setAdapter(
                            new SectionAdapter(results, p -> openProduct(p.getProductId())));
                })
                .addOnFailureListener(e -> {
                    if (binding == null) return;
                    Log.e(TAG, "Search failed: " + e.getMessage());
                    binding.homeSearchResultsSection.itemSectionTitle.setText("Search failed. Try again.");
                });
    }

    // ── Navigate to AllBooksFragment ──────────────────────────────────────────
    // mode = "top"  → shows Top Rated books
    // mode = "new"  → shows New Arrivals (newest first)
    // mode = null   → shows all books
    private void openAllBooks(String mode) {
        Bundle bundle = new Bundle();
        bundle.putString("mode", mode);
        AllBooksFragment fragment = new AllBooksFragment();
        fragment.setArguments(bundle);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bannerHandler.removeCallbacks(bannerRunnable);
        binding = null;
    }
}
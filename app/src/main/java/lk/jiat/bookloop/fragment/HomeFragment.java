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

// HomeFragment — main landing screen of BookLoop.
// SENSOR FEATURE: Implements SensorEventListener to detect phone shake.
// WHY: When user shakes the phone, all book sections refresh from Firestore.
//      This is a natural, physical gesture — like "shake to see what's new".
// COVERS: Sensors assignment requirement (Accelerometer).
public class HomeFragment extends Fragment implements SensorEventListener {

    private static final String TAG = "HomeFragment";
    private FragmentHomeBinding binding;

    // ── Sensor fields (from practical) ───────────────────────────────────────
    // SensorManager: the Android system service that gives access to all sensors.
    // Sensor: represents one specific sensor on the device (accelerometer here).
    private SensorManager sensorManager;
    private Sensor accelerometer;

    // SHAKE_THRESHOLD: minimum total force to count as a shake.
    // 12f is a good balance — not too sensitive, not too hard to trigger.
    // From practical: event.values give X, Y, Z separately.
    // We combine them: magnitude = sqrt(X² + Y² + Z²)
    // Earth gravity alone = ~9.8, so 12f means "clearly more than just gravity".
    private static final float SHAKE_THRESHOLD = 12f;

    // Prevents firing multiple refreshes from one shake gesture.
    // We only allow one shake every 1.5 seconds.
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

        // NEW — Get the SensorManager system service (same as practical)
        // getSystemService() returns the system-level sensor hardware manager.
        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);

        // NEW — Get the accelerometer sensor specifically.
        // TYPE_ACCELEROMETER: measures force applied to the device on all 3 axes (X, Y, Z).
        // Returns null if the device has no accelerometer (very rare).
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accelerometer == null) {
            // Device has no accelerometer — sensor feature just won't work, app still runs fine
            Log.w(TAG, "Accelerometer not available on this device");
        }

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

    // ── SensorEventListener: called every time sensor values change ───────────
    // SAME AS PRACTICAL: event.sensor.getType() tells us which sensor fired.
    // event.values[0] = X axis, event.values[1] = Y axis, event.values[2] = Z axis
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Read X, Y, Z acceleration values (same as practical's format string)
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Calculate total force across all 3 axes using Pythagorean theorem in 3D.
        // WHY: A shake moves the phone in multiple directions at once.
        //      Looking at one axis alone is unreliable — user might tilt, not shake.
        //      Combined magnitude gives a single number for "total movement force".
        // Math.sqrt returns double, (float) casts it back
        float magnitude = (float) Math.sqrt((x * x) + (y * y) + (z * z));

        // Check if magnitude exceeds our shake threshold
        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();

            // Only trigger if enough time has passed since last shake
            // WHY: A single shake fires onSensorChanged many times rapidly.
            //      Without cooldown, the refresh would fire 10+ times per shake.
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now;
                onShakeDetected();
            }
        }
    }

    // ── SensorEventListener: called when sensor accuracy changes ─────────────
    // Required by the interface — same as practical (left empty, not needed here)
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // We don't need to handle accuracy changes for shake detection
    }

    // ── Called when a valid shake is detected ────────────────────────────────
    private void onShakeDetected() {
        Log.i(TAG, "Shake detected! Refreshing book sections...");

        // Show feedback so user knows the shake worked
        Toast.makeText(getContext(), "🔄 Refreshing books...", Toast.LENGTH_SHORT).show();

        // Reload all book sections from Firestore
        // WHY refreshAllSections instead of individual calls:
        //     Keeps shake logic separate — one method to call, easy to maintain.
        refreshAllSections();
    }

    // ── Refresh all book sections (called on shake) ───────────────────────────
    private void refreshAllSections() {
        loadTopRatedSection();
        loadNewArrivalsSection();
        loadRecentlyViewedSection();
    }

    // ── Register accelerometer when screen becomes visible ────────────────────
    // WHY onResume: Same pattern as practical — register here so sensor only runs
    //     when user is actually looking at this screen. Saves battery.
    // SENSOR_DELAY_NORMAL: updates ~5 times/second. Fast enough for shake detection.
    //     (SENSOR_DELAY_GAME would be faster but wastes battery on the home screen)
    @Override
    public void onResume() {
        super.onResume();

        // Register accelerometer listener — starts receiving onSensorChanged() calls
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG, "Accelerometer registered");
        }

        // Resume banner auto-scroll
        if (bannerRunnable != null) {
            bannerHandler.postDelayed(bannerRunnable, 3000);
        }
    }

    // ── Unregister accelerometer when screen is not visible ───────────────────
    // WHY onPause: Same as practical — MUST unregister here.
    //     If you forget this, the sensor keeps running in the background
    //     and drains the battery even when the user is on a different screen.
    @Override
    public void onPause() {
        super.onPause();

        // Unregister — stops all sensor callbacks to save battery
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            Log.d(TAG, "Accelerometer unregistered");
        }

        // Pause banner auto-scroll
        bannerHandler.removeCallbacks(bannerRunnable);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Everything below is unchanged from original HomeFragment
    // ─────────────────────────────────────────────────────────────────────────

    private void setupBannerSlider() {
        List<String> bannerImages = Arrays.asList(
                "banners/banner_1.jpg",
                "banners/banner_2.jpg",
                "banners/banner_3.jpg"
        );

        ProductSliderAdapter sliderAdapter = new ProductSliderAdapter(bannerImages);
        binding.homeBannerSlider.setAdapter(sliderAdapter);
        binding.homeBannerDots.attachTo(binding.homeBannerSlider);

        bannerRunnable = () -> {
            bannerPage = (bannerPage + 1) % bannerImages.size();
            binding.homeBannerSlider.setCurrentItem(bannerPage, true);
            bannerHandler.postDelayed(bannerRunnable, 3000);
        };
        bannerHandler.postDelayed(bannerRunnable, 3000);
    }

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

    private void loadTopRatedSection() {
        FirebaseFirestore.getInstance()
                .collection("products")
                .get()
                .addOnSuccessListener(qds -> {
                    if (qds.isEmpty()) {
                        binding.homeTopRatedSection.getRoot().setVisibility(View.GONE);
                        return;
                    }

                    List<Product> all = qds.toObjects(Product.class);
                    List<Product> topRated = new ArrayList<>();
                    for (Product p : all) {
                        if (p.getRating() >= 4.0f) topRated.add(p);
                    }
                    if (topRated.isEmpty()) topRated = all;

                    Collections.sort(topRated, (a, b) -> Float.compare(b.getRating(), a.getRating()));
                    if (topRated.size() > 10) topRated = topRated.subList(0, 10);

                    binding.homeTopRatedSection.itemSectionTitle.setText("⭐ Top Rated Books");
                    binding.homeTopRatedSection.itemSectionContainer.setLayoutManager(
                            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
                    binding.homeTopRatedSection.itemSectionContainer.setAdapter(
                            new SectionAdapter(topRated, p -> openProduct(p.getProductId())));
                    binding.homeTopRatedSection.getRoot().setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Top rated load failed: " + e.getMessage());
                    binding.homeTopRatedSection.getRoot().setVisibility(View.GONE);
                });
    }

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
                    binding.homeNewArrivalsSection.getRoot().setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "New arrivals load failed: " + e.getMessage());
                    binding.homeNewArrivalsSection.getRoot().setVisibility(View.GONE);
                });
    }

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

    public void performSearch(String query) {
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
                    Log.e(TAG, "Search failed: " + e.getMessage());
                    binding.homeSearchResultsSection.itemSectionTitle.setText("Search failed. Try again.");
                });
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
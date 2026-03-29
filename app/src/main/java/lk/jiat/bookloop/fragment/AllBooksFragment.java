package lk.jiat.bookloop.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.ListingAdapter;
import lk.jiat.bookloop.model.Product;

// AllBooksFragment — shows every book in a 2-column grid.
// Launched from the "See All" buttons on Top Rated and New Arrivals sections.
// mode = "top"    → sorted by rating descending
// mode = "new"    → sorted by createdAt descending (newest first)
// mode = "search" → search results, pre-filtered by initialQuery
// mode = null     → all books sorted alphabetically
//
// BUG FIX: loadBooks() was accidentally commented out together with the old
// search bar wiring in the previous edit. The fragment was inflating, setting
// the title, then sitting there with the spinner forever because Firestore
// was never queried. Fixed by calling loadBooks() at end of onViewCreated().
public class AllBooksFragment extends Fragment {

    private static final String TAG = "AllBooksFragment";

    private RecyclerView recyclerView;
    private ListingAdapter adapter;
    private TextView titleText;
    private TextView emptyText;
    private View loadingSpinner;

    private List<Product> allProducts = new ArrayList<>();
    private String mode;
    private String initialQuery;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mode         = getArguments().getString("mode");
            initialQuery = getArguments().getString("initialQuery");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_all_books, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        titleText      = view.findViewById(R.id.all_books_title);
        emptyText      = view.findViewById(R.id.all_books_empty);
        loadingSpinner = view.findViewById(R.id.all_books_loading);
        recyclerView   = view.findViewById(R.id.all_books_recycler);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        if ("top".equals(mode)) {
            titleText.setText("⭐ Top Rated Books");
        } else if ("new".equals(mode)) {
            titleText.setText("🆕 New Arrivals");
        } else if ("search".equals(mode)) {
            titleText.setText("🔍 Search Books");
        } else {
            titleText.setText("📚 All Books");
        }

        ImageButton backBtn = view.findViewById(R.id.all_books_back);
        backBtn.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                });

        // FIX: this single line was accidentally deleted when the in-fragment
        // search bar was removed. It is the only thing that starts the Firestore
        // query — without it the screen shows nothing but the loading spinner forever.
        loadBooks();
    }

    private void loadBooks() {
        if (loadingSpinner != null) loadingSpinner.setVisibility(View.VISIBLE);
        if (emptyText != null) emptyText.setVisibility(View.GONE);

        FirebaseFirestore.getInstance()
                .collection("products")
                .get()
                .addOnSuccessListener(qds -> {
                    if (getView() == null) return;
                    if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);

                    if (qds.isEmpty()) {
                        showEmpty("No books found.");
                        return;
                    }

                    allProducts = qds.toObjects(Product.class);

                    if ("top".equals(mode)) {
                        Collections.sort(allProducts,
                                (a, b) -> Float.compare(b.getRating(), a.getRating()));

                    } else if ("new".equals(mode)) {
                        allProducts.sort((a, b) -> {
                            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                            if (a.getCreatedAt() == null) return 1;
                            if (b.getCreatedAt() == null) return -1;
                            return b.getCreatedAt().compareTo(a.getCreatedAt());
                        });

                    } else if (!"search".equals(mode)) {
                        allProducts.sort((a, b) -> {
                            if (a.getTitle() == null) return 1;
                            if (b.getTitle() == null) return -1;
                            return a.getTitle().compareToIgnoreCase(b.getTitle());
                        });
                    }

                    showProducts(allProducts);

                    if (initialQuery != null && !initialQuery.isEmpty()) {
                        filterProducts(initialQuery);
                    }
                })
                .addOnFailureListener(e -> {
                    if (getView() == null) return;
                    if (loadingSpinner != null) loadingSpinner.setVisibility(View.GONE);
                    Log.e(TAG, "Load error: " + e.getMessage());
                    showEmpty("Failed to load books. Check connection.");
                });
    }

    // Called from MainActivity toolbar text watcher
    public void filterProducts(String query) {
        if (allProducts.isEmpty()) return;

        if (TextUtils.isEmpty(query.trim())) {
            showProducts(allProducts);
            return;
        }

        String lower = query.toLowerCase().trim();
        List<Product> filtered = new ArrayList<>();
        for (Product p : allProducts) {
            boolean titleMatch  = p.getTitle()  != null && p.getTitle().toLowerCase().contains(lower);
            boolean authorMatch = p.getAuthor() != null && p.getAuthor().toLowerCase().contains(lower);
            if (titleMatch || authorMatch) filtered.add(p);
        }

        if (filtered.isEmpty()) {
            showEmpty("No books match \"" + query.trim() + "\"");
        } else {
            showProducts(filtered);
        }
    }

    private void showProducts(List<Product> products) {
        if (emptyText != null) emptyText.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);

        adapter = new ListingAdapter(products, product -> {
            Bundle bundle = new Bundle();
            bundle.putString("productId", product.getProductId());
            ProductDetailsFragment detailsFragment = new ProductDetailsFragment();
            detailsFragment.setArguments(bundle);
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, detailsFragment)
                    .addToBackStack(null)
                    .commit();
        });
        recyclerView.setAdapter(adapter);
    }

    private void showEmpty(String message) {
        recyclerView.setVisibility(View.GONE);
        if (emptyText != null) {
            emptyText.setText(message);
            emptyText.setVisibility(View.VISIBLE);
        }
    }
}
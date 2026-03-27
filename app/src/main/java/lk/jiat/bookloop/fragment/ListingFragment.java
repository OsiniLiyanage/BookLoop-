package lk.jiat.bookloop.fragment;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.ListingAdapter;
import lk.jiat.bookloop.databinding.FragmentListingBinding;
import lk.jiat.bookloop.model.Product;

// ListingFragment — shows all books for a category in a 2-column grid.
// SEARCH: the toolbar search bar (text_input_search) in MainActivity calls
//         filterProducts(query) on this fragment whenever the user types.
//         We filter the in-memory list by title OR author — no extra Firestore
//         query needed, no index error possible.
public class ListingFragment extends Fragment {

    private FragmentListingBinding binding;
    private ListingAdapter adapter;
    private String categoryId;

    // Keep a full copy of all products so we can filter without re-querying Firestore
    private List<Product> allProducts = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            categoryId = getArguments().getString("categoryId");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentListingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.recyclerViewListing.setLayoutManager(new GridLayoutManager(getContext(), 2));

        loadProducts();

        // Back button
        getActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                });
    }

    // Load all products for this category from Firestore.
    // FIX: removed .orderBy("title") — that requires a composite index when combined
    //      with .whereEqualTo(), which causes a 400 error. We sort in Java instead.
    private void loadProducts() {
        FirebaseFirestore.getInstance()
                .collection("products")
                .whereEqualTo("categoryId", categoryId)
                .get()
                .addOnSuccessListener(ds -> {
                    if (ds.isEmpty()) return;

                    allProducts = ds.toObjects(Product.class);

                    // Sort A→Z by title in Java (replaces .orderBy on Firestore)
                    allProducts.sort((a, b) -> {
                        if (a.getTitle() == null) return 1;
                        if (b.getTitle() == null) return -1;
                        return a.getTitle().compareToIgnoreCase(b.getTitle());
                    });

                    showProducts(allProducts);
                })
                .addOnFailureListener(e -> Log.e("LISTING", "Load error: " + e.getMessage()));
    }

    // Called from MainActivity every time the user types in the search bar.
    // Filters allProducts by title OR author containing the query (case-insensitive).
    public void filterProducts(String query) {
        if (allProducts.isEmpty()) return;

        if (TextUtils.isEmpty(query)) {
            // Empty search → show all products
            showProducts(allProducts);
            return;
        }

        String lower = query.toLowerCase().trim();
        List<Product> filtered = new ArrayList<>();

        for (Product p : allProducts) {
            boolean titleMatch  = p.getTitle()  != null && p.getTitle().toLowerCase().contains(lower);
            boolean authorMatch = p.getAuthor() != null && p.getAuthor().toLowerCase().contains(lower);
            if (titleMatch || authorMatch) {
                filtered.add(p);
            }
        }

        showProducts(filtered);
    }

    // Set the RecyclerView adapter with the given product list
    private void showProducts(List<Product> products) {
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
        binding.recyclerViewListing.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
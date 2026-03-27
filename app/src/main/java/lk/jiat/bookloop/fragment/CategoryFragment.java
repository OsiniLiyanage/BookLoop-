package lk.jiat.bookloop.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager; // NEW — 2-column grid

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.CategoryAdapter;
import lk.jiat.bookloop.databinding.FragmentCategoryBinding;
import lk.jiat.bookloop.model.Category;

public class CategoryFragment extends Fragment {

    private FragmentCategoryBinding binding;
    private CategoryAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCategoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // NEW — Changed from 3 columns to 2 columns so categories are bigger and easier to tap
        binding.recyclerViewCategories.setLayoutManager(new GridLayoutManager(getContext(), 2));

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("categories").get()
                .addOnSuccessListener(qds -> {
                    List<Category> categories = qds.toObjects(Category.class);

                    adapter = new CategoryAdapter(categories, category -> {
                        // Navigate to listing filtered by this category
                        Bundle bundle = new Bundle();
                        bundle.putString("categoryId", category.getCategoryId());
                        bundle.putString("categoryName", category.getName()); // NEW — pass name for title

                        ListingFragment fragment = new ListingFragment();
                        fragment.setArguments(bundle);

                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, fragment)
                                .addToBackStack(null)
                                .commit();
                    });

                    binding.recyclerViewCategories.setAdapter(adapter);
                })
                .addOnFailureListener(e -> Log.e("CategoryFragment", "Failed to load categories: " + e.getMessage()));
    }
}
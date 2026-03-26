package lk.jiat.bookloop.fragment;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.ProductSliderAdapter;
import lk.jiat.bookloop.adapter.SectionAdapter;
import lk.jiat.bookloop.databinding.FragmentProductDetailsBinding;
import lk.jiat.bookloop.model.Product;


public class ProductDetailsFragment extends Fragment {

    private FragmentProductDetailsBinding binding;
    private String productId;
    private int quantity = 1;
    private int avbQuantity;
    private Map<String, ChipGroup> attributeGroups = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            productId = getArguments().getString("productId");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentProductDetailsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.GONE);

        getActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });


        // Load Product Details

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("products")
                .whereEqualTo("productId", productId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot qds) {
                        if (!qds.isEmpty()) {
                            Product product = qds.getDocuments().get(0).toObject(Product.class);

                            ProductSliderAdapter adapter = new ProductSliderAdapter(product.getImages());
                            binding.productImageSlider.setAdapter(adapter);
//                            binding.productImageSlider.setAdapter(adapter);
//
                            binding.dotsIndicator.attachTo(binding.productImageSlider);


                            binding.productDetailsTitle.setText(product.getTitle());

                            binding.productDetailsRating.setRating(product.getRating());

                            binding.productDetailsPrice.setText("LKR " + product.getPrice());
                            binding.productDetailsAvbQty.setText(String.valueOf(product.getStockCount()));
                            avbQuantity = product.getStockCount();

                            if (product.getAttributes() != null) {

                                product.getAttributes().forEach(attribute -> {
                                    renderAttribute(attribute, binding.productDetailsAttributeContainer);

                                });

                            }

                        }
                    }
                });

                binding.productDetailsBtnMinus.setOnClickListener(v->{
                    if (quantity > 1){
                        quantity--;
                        binding.productDetailsQuantity.setText(String.valueOf(quantity));
                    }
                });

                binding.productDetailsBtnPlus.setOnClickListener(v ->{
                    if (quantity < avbQuantity ){
                        quantity++;
                        binding.productDetailsQuantity.setText(String.valueOf(quantity));
                    }
                });

                    loadTopSellProduct();
                binding.productDetailsBtnAddCart.setOnClickListener(v -> {
                    getFinalSelections();
                });
//
    }

    private void loadTopSellProduct() {

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("products")
                .whereNotEqualTo("productId", productId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot qds) {
                        if (!qds.isEmpty()) {
                            List<Product> products = qds.toObjects(Product.class);


                            LinearLayoutManager  layoutManager =
                                    new LinearLayoutManager(getContext(),LinearLayoutManager.HORIZONTAL, false);

                            binding.productDetailsTopSellSection.itemSectionContainer.setLayoutManager(layoutManager);


                            SectionAdapter adapter = new SectionAdapter(products, product -> {
                                Bundle bundle = new Bundle();
                                bundle.putString("productId",product.getProductId());

                                ProductDetailsFragment productDetailsFragment = new ProductDetailsFragment();
                                productDetailsFragment.setArguments(bundle);

                                getParentFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, productDetailsFragment)
                                        .addToBackStack(null)
                                        .commit();
                            });

                            binding.productDetailsTopSellSection.itemSectionTitle.setText("Top Selling Products");
                            binding.productDetailsTopSellSection.itemSectionContainer.setAdapter(adapter);

                        }
                    }
                });

    }
//

    private void renderAttribute(Product.Attribute attribute, ViewGroup container) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);


        //Create Label
        TextView label = new TextView(getContext());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(200, ViewGroup.LayoutParams.WRAP_CONTENT);

        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        label.setLayoutParams(layoutParams);


        label.setText(attribute.getName());


        row.addView(label);

        //Create Options
        ChipGroup group = new ChipGroup(getContext());

        group.setSelectionRequired(true);
        group.setSingleSelection(true);

        attribute.getValues().forEach(value -> {
            Chip chip = new Chip(getContext());
            chip.setCheckable(true);
            chip.setChipStrokeWidth(3f);


            if ("color".equals(attribute.getType())) {
                chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor(value)));
            } else {
                chip.setText(value);
            }

            group.addView(chip);
        });

        row.addView(group);

        container.addView(row);
        attributeGroups.put(attribute.getName(),group);

    }

    private void getFinalSelections(){

        StringBuilder result = new StringBuilder("Selected: \n");

        for(Map.Entry<String, ChipGroup> entry : attributeGroups.entrySet()){
            String attributeName = entry.getKey();
            ChipGroup chipGroup = entry.getValue();

            int checkedChipId = chipGroup.getCheckedChipId();
            if (checkedChipId != -1){
                Chip chip= getView().findViewById(checkedChipId);
                String value = chip.getTag().toString();

                result.append(attributeName).append(": ").append(value).append("\n");


            }
        }


        Log.i("Final Result", result.toString());

    }

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
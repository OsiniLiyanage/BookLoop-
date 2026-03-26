package lk.jiat.bookloop.fragment;

import android.content.Intent;
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
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.activity.SignInActivity;
import lk.jiat.bookloop.adapter.ProductSliderAdapter;
import lk.jiat.bookloop.adapter.SectionAdapter;
import lk.jiat.bookloop.databinding.FragmentProductDetailsBinding;
import lk.jiat.bookloop.model.CartItem;
import lk.jiat.bookloop.model.Product;

public class ProductDetailsFragment extends Fragment {

    private FragmentProductDetailsBinding binding;
    private String productId;

    // Rental duration in weeks
    private int rentalWeeks = 1;

    // Number of copies the user wants to rent
    private int rentalCopies = 1;

    // Max copies available from Firestore
    private int availableCopies;

    // Price per week from Firestore
    private double pricePerWeek;

    // Chip group map for attribute selections (Condition etc.)
    private Map<String, ChipGroup> attributeGroups = new HashMap<>();

    // For loading related sections
    private String currentAuthor;
    private String currentCategoryId;

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

        // Hide bottom nav when on product details page
        getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.GONE);

        // Back press returns to previous fragment
        getActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Load product data from Firestore
        loadProductDetails();

        // ── Rental WEEKS stepper ──────────────────────────────────────────────

        // Minus button (remove_24 drawable) — decrease weeks
        binding.productDetailsBtnMinus.setOnClickListener(v -> {
            if (rentalWeeks > 1) {
                rentalWeeks--;
                updateRentalUI();
            }
        });

        // Plus button (add_24 drawable) — increase weeks
        binding.productDetailsBtnPlus.setOnClickListener(v -> {
            rentalWeeks++;
            updateRentalUI();
        });

        // ── Rental COPIES stepper ─────────────────────────────────────────────

        // Minus copies — can't go below 1
        binding.productDetailsBtnCopiesMinus.setOnClickListener(v -> {
            if (rentalCopies > 1) {
                rentalCopies--;
                updateRentalUI();
            }
        });

        // Plus copies — can't exceed available stock
        binding.productDetailsBtnCopiesPlus.setOnClickListener(v -> {
            if (rentalCopies < availableCopies) {
                rentalCopies++;
                updateRentalUI();
            }
        });

        // ── Action buttons ────────────────────────────────────────────────────
//        add cart implementation
        binding.productDetailsBtnAddCart.setOnClickListener(v -> {

            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            if (firebaseAuth.getCurrentUser() == null) {
                Intent intent = new Intent(getActivity(), SignInActivity.class);
                startActivity(intent);
            } else {

                FirebaseFirestore db = FirebaseFirestore.getInstance();

                List<CartItem.Attribute> attributes = getFinalSelections();

                CartItem cartItem = new CartItem(productId, rentalCopies,rentalWeeks, attributes);

                String uid = firebaseAuth.getCurrentUser().getUid();

                db.collection("users").document(uid).collection("cart").document()
                        .set(cartItem)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unused) {
                                Toast.makeText(getContext(), "Item added to cart!", Toast.LENGTH_SHORT).show();
                            }
                        });
            }


        });

//        rental checkout implementation
        binding.productDetailsBtnBuyNow.setOnClickListener(v -> {
            getFinalSelections();

        });

        binding.productDetailsBtnWishlist.setOnClickListener(v -> {
            // TODO: wishlist toggle implementation here
            Log.i("WISHLIST", "Toggled wishlist for: " + productId);
        });
    }

    // ─── Update weeks/copies display and recalculate total ───────────────────
    private void updateRentalUI() {
        // Update weeks counter
        binding.productDetailsQuantity.setText(String.valueOf(rentalWeeks));

        // Update days label (1 week = 7 days)
        binding.productDetailsTotalDays.setText((rentalWeeks * 7) + " days");

        // Update copies counter
        binding.productDetailsCopies.setText(String.valueOf(rentalCopies));

        // Recalculate total: price × weeks × copies
        double total = pricePerWeek * rentalWeeks * rentalCopies;
        binding.productDetailsTotalPrice.setText("LKR " + (int) total);
    }

    // ─── Load product from Firestore ─────────────────────────────────────────
    private void loadProductDetails() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("products")
                .whereEqualTo("productId", productId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot qds) {
                        if (!qds.isEmpty()) {
                            Product product = qds.getDocuments().get(0).toObject(Product.class);

                            // Save for section queries
                            currentAuthor = product.getAuthor();
                            currentCategoryId = product.getCategoryId();
                            pricePerWeek = product.getPrice();
                            availableCopies = product.getStockCount();

                            // Image slider
                            ProductSliderAdapter sliderAdapter = new ProductSliderAdapter(product.getImages());
                            binding.productImageSlider.setAdapter(sliderAdapter);
                            binding.dotsIndicator.attachTo(binding.productImageSlider);

                            // Title
                            binding.productDetailsTitle.setText(product.getTitle());

                            // Author
                            String authorLabel = (product.getAuthor() != null && !product.getAuthor().isEmpty())
                                    ? "by " + product.getAuthor() : "Author Unknown";
                            binding.productDetailsAuthor.setText(authorLabel);

                            // Rating
                            binding.productDetailsRating.setRating(product.getRating());
                            binding.productDetailsRatingText.setText(String.valueOf(product.getRating()));

                            // Price per week
                            binding.productDetailsPrice.setText("LKR " + (int) pricePerWeek + " / week");

                            // Availability badge
                            binding.productDetailsStatusBadge.setText(product.isStatus() ? "Available" : "Unavailable");

                            // Available copies
                            binding.productDetailsAvbQty.setText(String.valueOf(availableCopies));

                            // Description
                            if (product.getDescription() != null) {
                                binding.productDetailsDescription.setText(product.getDescription());
                            }

                            // Set initial rental UI
                            updateRentalUI();

                            // Attributes (Condition chips etc.)
                            if (product.getAttributes() != null) {
                                product.getAttributes().forEach(attribute ->
                                        renderAttribute(attribute, binding.productDetailsAttributeContainer));
                            }

                            // Load 3 related book sections
                            loadSameCategorySection(currentCategoryId);
                            loadSameAuthorSection(currentAuthor);
                            loadTopRatedSection();
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("PRODUCT_DETAILS", "Load error: " + e.getMessage()));
    }

    // ─── Section 1: Other books in same category ─────────────────────────────
    private void loadSameCategorySection(String categoryId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("products")
                .whereEqualTo("categoryId", categoryId)
                .get()
                .addOnSuccessListener(qds -> {
                    // Filter out current book manually (avoids needing a composite index)
                    List<Product> products = qds.toObjects(Product.class);
                    products.removeIf(p -> productId.equals(p.getProductId()));

                    if (!products.isEmpty()) {
                        LinearLayoutManager lm = new LinearLayoutManager(
                                getContext(), LinearLayoutManager.HORIZONTAL, false);
                        binding.productDetailsSameCategorySection.itemSectionContainer.setLayoutManager(lm);
                        binding.productDetailsSameCategorySection.itemSectionTitle.setText("More in this Category");
                        binding.productDetailsSameCategorySection.itemSectionContainer
                                .setAdapter(new SectionAdapter(products, p -> openProductDetails(p.getProductId())));
                    } else {
                        binding.productDetailsSameCategorySection.getRoot().setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    //  If you see "FAILED_PRECONDITION" here, copy the URL from this log to create the Firestore index
                    Log.e("FIRESTORE_INDEX", "Same-category query needs an index: " + e.getMessage());
                    binding.productDetailsSameCategorySection.getRoot().setVisibility(View.GONE);
                });
    }

    // ─── Section 2: Other books by same author ────────────────────────────────
    private void loadSameAuthorSection(String author) {
        if (author == null || author.isEmpty()) {
            binding.productDetailsSameAuthorSection.getRoot().setVisibility(View.GONE);
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("products")
                .whereEqualTo("author", author)
                .get()
                .addOnSuccessListener(qds -> {
                    // Filter out current book manually
                    List<Product> products = qds.toObjects(Product.class);
                    products.removeIf(p -> productId.equals(p.getProductId()));

                    if (!products.isEmpty()) {
                        LinearLayoutManager lm = new LinearLayoutManager(
                                getContext(), LinearLayoutManager.HORIZONTAL, false);
                        binding.productDetailsSameAuthorSection.itemSectionContainer.setLayoutManager(lm);
                        binding.productDetailsSameAuthorSection.itemSectionTitle.setText("More Books by Author " + author);
                        binding.productDetailsSameAuthorSection.itemSectionContainer
                                .setAdapter(new SectionAdapter(products, p -> openProductDetails(p.getProductId())));
                    } else {
                        binding.productDetailsSameAuthorSection.getRoot().setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    // Copy index URL from this log if you see FAILED_PRECONDITION
                    Log.e("FIRESTORE_INDEX", "Same-author query needs an index: " + e.getMessage());
                    binding.productDetailsSameAuthorSection.getRoot().setVisibility(View.GONE);
                });
    }

    // ─── Section 3: Top rated books (rating >= 4.0) ───────────────────────────
    private void loadTopRatedSection() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("products")
                .whereGreaterThanOrEqualTo("rating", 4.0)
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(qds -> {
                    // Filter out current book manually
                    List<Product> products = qds.toObjects(Product.class);
                    products.removeIf(p -> productId.equals(p.getProductId()));

                    if (!products.isEmpty()) {
                        LinearLayoutManager lm = new LinearLayoutManager(
                                getContext(), LinearLayoutManager.HORIZONTAL, false);
                        binding.productDetailsTopRatedSection.itemSectionContainer.setLayoutManager(lm);
                        binding.productDetailsTopRatedSection.itemSectionTitle.setText("Top Rated Books");
                        binding.productDetailsTopRatedSection.itemSectionContainer
                                .setAdapter(new SectionAdapter(products, p -> openProductDetails(p.getProductId())));
                    } else {
                        binding.productDetailsTopRatedSection.getRoot().setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    //  IMPORTANT: If you see FAILED_PRECONDITION, Firestore will print a URL in this log.
                    //    Copy that URL and open it in your browser — it will take you directly to create the index.
                    //    The index needed is: collection=products, field=rating ASCENDING
                    Log.e("FIRESTORE_INDEX", "Top-rated query needs an index. Check URL in error: " + e.getMessage());
                    binding.productDetailsTopRatedSection.getRoot().setVisibility(View.GONE);
                });
    }

    // ─── Open another product's detail page ──────────────────────────────────
    private void openProductDetails(String newProductId) {
        Bundle bundle = new Bundle();
        bundle.putString("productId", newProductId);
        ProductDetailsFragment fragment = new ProductDetailsFragment();
        fragment.setArguments(bundle);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    // ─── Render one attribute row with chips (e.g. Condition: New/Good/Fair) ──
    private void renderAttribute(Product.Attribute attribute, ViewGroup container) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);

        // Label (e.g. "Condition")
        TextView label = new TextView(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(220, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        label.setLayoutParams(lp);
        label.setText(attribute.getName());
        label.setTextSize(13f);
        row.addView(label);

        // Chip group
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
                chip.setTag(value);
            }
            group.addView(chip);
        });

        row.addView(group);
        container.addView(row);
        attributeGroups.put(attribute.getName(), group);
    }

    // ─── Collect all chip + stepper selections before cart/rent ──────────────
    private List<CartItem.Attribute> getFinalSelections() {

        List<CartItem.Attribute> attributes = new ArrayList<>();

        StringBuilder result = new StringBuilder("=== Rental Order ===\n");
        result.append("Product ID: ").append(productId).append("\n");
        result.append("Weeks: ").append(rentalWeeks).append("\n");
        result.append("Copies: ").append(rentalCopies).append("\n");
        result.append("Total: LKR ").append((int)(pricePerWeek * rentalWeeks * rentalCopies)).append("\n");

        for (Map.Entry<String, ChipGroup> entry : attributeGroups.entrySet()) {
            String attributeName = entry.getKey();
            ChipGroup chipGroup = entry.getValue();
            int checkedId = entry.getValue().getCheckedChipId();
            if (checkedId != -1) {
                Chip chip = getView().findViewById(checkedId);
                String value = chip.getTag().toString();
                attributes.add(new CartItem.Attribute(attributeName, value));

                if (chip != null && chip.getTag() != null) {
                    result.append(entry.getKey()).append(": ").append(chip.getTag()).append("\n");
                }
            }

        }
        Log.i("RENTAL_ORDER", result.toString());
        return attributes;


    }

    // ─── Restore bottom nav when leaving this screen ──────────────────────────
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

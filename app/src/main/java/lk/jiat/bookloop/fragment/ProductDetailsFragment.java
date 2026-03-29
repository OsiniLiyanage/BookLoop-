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
import lk.jiat.bookloop.helper.WishlistDatabase;
import lk.jiat.bookloop.model.CartItem;
import lk.jiat.bookloop.model.Product;

/*
 * ProductDetailsFragment.java
 * ───────────────────────────
 * Shows all details for a single book.
 *
 * FIXES IN THIS VERSION:
 *   1. "Rent Now" button now navigates to CheckoutFragment (previously did nothing)
 *   2. Wishlist toggle — adds/removes from local SQLite + syncs to Firestore
 *   3. Recently Viewed — saved to SQLite when this page opens
 *   4. Add to Cart — saves to Firestore users/{uid}/cart
 */
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

    // SQLite wishlist database (local storage)
    private WishlistDatabase wishlistDb;

    // Track current wishlist state so we can toggle
    private boolean isInWishlist = false;

    // Store product info so we can save to wishlist without another Firestore call
    private Product currentProduct;

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

        // Open SQLite wishlist database
        wishlistDb = new WishlistDatabase(requireContext());

        // Hide bottom nav when on product details page
        getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.GONE);

        // Back press returns to previous fragment
        getActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                });

        // Load product data from Firestore
        loadProductDetails();

        // ── Rental WEEKS stepper ──────────────────────────────────────────────
        binding.productDetailsBtnMinus.setOnClickListener(v -> {
            if (rentalWeeks > 1) {
                rentalWeeks--;
                updateRentalUI();
            }
        });

        binding.productDetailsBtnPlus.setOnClickListener(v -> {
            rentalWeeks++;
            updateRentalUI();
        });

        // ── Rental COPIES stepper ─────────────────────────────────────────────
        binding.productDetailsBtnCopiesMinus.setOnClickListener(v -> {
            if (rentalCopies > 1) {
                rentalCopies--;
                updateRentalUI();
            }
        });

        binding.productDetailsBtnCopiesPlus.setOnClickListener(v -> {
            if (rentalCopies < availableCopies) {
                rentalCopies++;
                updateRentalUI();
            }
        });

        // ── Add to Cart ────────────────────────────────────────────────────────
        binding.productDetailsBtnAddCart.setOnClickListener(v -> {
            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            if (firebaseAuth.getCurrentUser() == null) {
                startActivity(new Intent(getActivity(), SignInActivity.class));
            } else {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                List<CartItem.Attribute> attributes = getFinalSelections();
                CartItem cartItem = new CartItem(productId, rentalCopies, rentalWeeks, attributes);
                String uid = firebaseAuth.getCurrentUser().getUid();

                db.collection("users").document(uid).collection("cart").document()
                        .set(cartItem)
                        .addOnSuccessListener(unused ->
                                Toast.makeText(getContext(), "Added to cart!", Toast.LENGTH_SHORT).show());
            }
        });

        // ── Rent Now → Navigate directly to Checkout ──────────────────────────
        //
        // FIX: Previously this button called getFinalSelections() and returned the
        // list without doing anything with it — the user was stuck on the same page.
        //
        // HOW IT WORKS NOW:
        //   1. Check user is logged in (redirect to SignIn if not)
        //   2. Add the selected product to cart (so CheckoutFragment can read it)
        //   3. Navigate immediately to CheckoutFragment
        //
        // WHY add to cart first?
        //   CheckoutFragment reads from Firestore users/{uid}/cart to build
        //   the order summary. By adding to cart before navigating, the checkout
        //   screen will show this book in the summary automatically.
        //
        binding.productDetailsBtnBuyNow.setOnClickListener(v -> {
            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            if (firebaseAuth.getCurrentUser() == null) {
                // Not logged in — send to sign in first
                startActivity(new Intent(getActivity(), SignInActivity.class));
                return;
            }

            // Add to cart so CheckoutFragment can read it
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            List<CartItem.Attribute> attributes = getFinalSelections();
            CartItem cartItem = new CartItem(productId, rentalCopies, rentalWeeks, attributes);
            String uid = firebaseAuth.getCurrentUser().getUid();

            db.collection("users").document(uid).collection("cart").document()
                    .set(cartItem)
                    .addOnSuccessListener(unused -> {
                        // Cart saved — now navigate to checkout
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new CheckoutFragment())
                                .addToBackStack(null)
                                .commit();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(),
                                    "Could not start checkout. Try again.", Toast.LENGTH_SHORT).show());
        });

        // ── Wishlist Toggle ───────────────────────────────────────────────────
        binding.productDetailsBtnWishlist.setOnClickListener(v -> {
            if (currentProduct == null) return;

            new Thread(() -> {
                if (isInWishlist) {
                    wishlistDb.removeFromWishlist(productId);
                    removeFromFirestoreWishlist(productId);
                    isInWishlist = false;
                } else {
                    String imageUrl = (currentProduct.getImages() != null
                            && !currentProduct.getImages().isEmpty())
                            ? currentProduct.getImages().get(0) : "";

                    wishlistDb.addToWishlist(
                            productId,
                            currentProduct.getTitle(),
                            currentProduct.getAuthor(),
                            currentProduct.getPrice(),
                            imageUrl,
                            currentProduct.getCategoryId()
                    );
                    addToFirestoreWishlist();
                    isInWishlist = true;
                }
                requireActivity().runOnUiThread(this::updateWishlistButton);
            }).start();
        });
    }

    // ── Update wishlist button icon ───────────────────────────────────────────
    private void updateWishlistButton() {
        if (isInWishlist) {
            binding.productDetailsBtnWishlist.setIconResource(R.drawable.favorite_24px);
            binding.productDetailsBtnWishlist.setIconTint(
                    ColorStateList.valueOf(getResources().getColor(R.color.md_theme_primary, null)));
            Toast.makeText(getContext(), "Saved to Wishlist!", Toast.LENGTH_SHORT).show();
        } else {
            binding.productDetailsBtnWishlist.setIconResource(R.drawable.favorite_border_24px);
            binding.productDetailsBtnWishlist.setIconTint(
                    ColorStateList.valueOf(getResources().getColor(R.color.md_theme_onSurfaceVariant, null)));
        }
    }

    private void addToFirestoreWishlist() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("productId", productId);
        data.put("title", currentProduct.getTitle());
        data.put("author", currentProduct.getAuthor());
        data.put("price", currentProduct.getPrice());
        data.put("addedAt", System.currentTimeMillis());

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("wishlist").document(productId)
                .set(data)
                .addOnFailureListener(e -> Log.e("WISHLIST", "Firestore save failed: " + e.getMessage()));
    }

    private void removeFromFirestoreWishlist(String productId) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("wishlist").document(productId)
                .delete()
                .addOnFailureListener(e -> Log.e("WISHLIST", "Firestore delete failed: " + e.getMessage()));
    }

    // ─── Update weeks/copies display and recalculate total ───────────────────
    private void updateRentalUI() {
        binding.productDetailsQuantity.setText(String.valueOf(rentalWeeks));
        binding.productDetailsTotalDays.setText((rentalWeeks * 7) + " days");
        binding.productDetailsCopies.setText(String.valueOf(rentalCopies));

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
                            currentProduct = product;

                            currentAuthor     = product.getAuthor();
                            currentCategoryId = product.getCategoryId();
                            pricePerWeek      = product.getPrice();
                            availableCopies   = product.getStockCount();

                            // Image slider — pass the list of https:// URLs directly
                            ProductSliderAdapter sliderAdapter =
                                    new ProductSliderAdapter(product.getImages());
                            binding.productImageSlider.setAdapter(sliderAdapter);
                            binding.dotsIndicator.attachTo(binding.productImageSlider);

                            binding.productDetailsTitle.setText(product.getTitle());

                            String authorLabel = (product.getAuthor() != null
                                    && !product.getAuthor().isEmpty())
                                    ? "by " + product.getAuthor() : "Author Unknown";
                            binding.productDetailsAuthor.setText(authorLabel);

                            binding.productDetailsRating.setRating(product.getRating());
                            binding.productDetailsRatingText.setText(String.valueOf(product.getRating()));

                            binding.productDetailsPrice.setText("LKR " + (int) pricePerWeek + " / week");
                            binding.productDetailsStatusBadge.setText(
                                    product.isStatus() ? "Available" : "Unavailable");
                            binding.productDetailsAvbQty.setText(String.valueOf(availableCopies));

                            if (product.getDescription() != null) {
                                binding.productDetailsDescription.setText(product.getDescription());
                            }

                            updateRentalUI();

                            if (product.getAttributes() != null) {
                                product.getAttributes().forEach(attribute ->
                                        renderAttribute(attribute, binding.productDetailsAttributeContainer));
                            }

                            // Save to Recently Viewed (SQLite)
                            String imageForRecent = (product.getImages() != null
                                    && !product.getImages().isEmpty())
                                    ? product.getImages().get(0) : "";
                            new Thread(() -> wishlistDb.saveRecentlyViewed(
                                    productId,
                                    product.getTitle(),
                                    product.getAuthor(),
                                    product.getPrice(),
                                    imageForRecent
                            )).start();

                            // Check wishlist state
                            new Thread(() -> {
                                isInWishlist = wishlistDb.isInWishlist(productId);
                                requireActivity().runOnUiThread(() -> updateWishlistButton());
                            }).start();

                            loadSameCategorySection(currentCategoryId);
                            loadSameAuthorSection(currentAuthor);
                            loadTopRatedSection();
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("PRODUCT_DETAILS", "Load error: " + e.getMessage()));
    }

    private void loadSameCategorySection(String categoryId) {
        FirebaseFirestore.getInstance().collection("products")
                .whereEqualTo("categoryId", categoryId)
                .get()
                .addOnSuccessListener(qds -> {
                    List<Product> products = qds.toObjects(Product.class);
                    products.removeIf(p -> productId.equals(p.getProductId()));

                    if (!products.isEmpty()) {
                        LinearLayoutManager lm = new LinearLayoutManager(
                                getContext(), LinearLayoutManager.HORIZONTAL, false);
                        binding.productDetailsSameCategorySection.itemSectionContainer.setLayoutManager(lm);
                        binding.productDetailsSameCategorySection.itemSectionTitle.setText("More in this Category");
                        binding.productDetailsSameCategorySection.itemSectionContainer
                                .setAdapter(new SectionAdapter(products,
                                        p -> openProductDetails(p.getProductId())));
                    } else {
                        binding.productDetailsSameCategorySection.getRoot().setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FIRESTORE_INDEX", "Same-category query needs an index: " + e.getMessage());
                    binding.productDetailsSameCategorySection.getRoot().setVisibility(View.GONE);
                });
    }

    private void loadSameAuthorSection(String author) {
        if (author == null || author.isEmpty()) {
            binding.productDetailsSameAuthorSection.getRoot().setVisibility(View.GONE);
            return;
        }

        FirebaseFirestore.getInstance().collection("products")
                .whereEqualTo("author", author)
                .get()
                .addOnSuccessListener(qds -> {
                    List<Product> products = qds.toObjects(Product.class);
                    products.removeIf(p -> productId.equals(p.getProductId()));

                    if (!products.isEmpty()) {
                        LinearLayoutManager lm = new LinearLayoutManager(
                                getContext(), LinearLayoutManager.HORIZONTAL, false);
                        binding.productDetailsSameAuthorSection.itemSectionContainer.setLayoutManager(lm);
                        binding.productDetailsSameAuthorSection.itemSectionTitle
                                .setText("More Books by " + author);
                        binding.productDetailsSameAuthorSection.itemSectionContainer
                                .setAdapter(new SectionAdapter(products,
                                        p -> openProductDetails(p.getProductId())));
                    } else {
                        binding.productDetailsSameAuthorSection.getRoot().setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FIRESTORE_INDEX", "Same-author query: " + e.getMessage());
                    binding.productDetailsSameAuthorSection.getRoot().setVisibility(View.GONE);
                });
    }

    private void loadTopRatedSection() {
        FirebaseFirestore.getInstance().collection("products")
                .whereGreaterThanOrEqualTo("rating", 4.0)
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(qds -> {
                    List<Product> products = qds.toObjects(Product.class);
                    products.removeIf(p -> productId.equals(p.getProductId()));

                    if (!products.isEmpty()) {
                        LinearLayoutManager lm = new LinearLayoutManager(
                                getContext(), LinearLayoutManager.HORIZONTAL, false);
                        binding.productDetailsTopRatedSection.itemSectionContainer.setLayoutManager(lm);
                        binding.productDetailsTopRatedSection.itemSectionTitle.setText("Top Rated Books");
                        binding.productDetailsTopRatedSection.itemSectionContainer
                                .setAdapter(new SectionAdapter(products,
                                        p -> openProductDetails(p.getProductId())));
                    } else {
                        binding.productDetailsTopRatedSection.getRoot().setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FIRESTORE_INDEX", "Top-rated query: " + e.getMessage());
                    binding.productDetailsTopRatedSection.getRoot().setVisibility(View.GONE);
                });
    }

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

    private void renderAttribute(Product.Attribute attribute, ViewGroup container) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);

        TextView label = new TextView(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(220, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        label.setLayoutParams(lp);
        label.setText(attribute.getName());
        label.setTextSize(13f);
        row.addView(label);

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

    private List<CartItem.Attribute> getFinalSelections() {
        List<CartItem.Attribute> attributes = new ArrayList<>();
        for (Map.Entry<String, ChipGroup> entry : attributeGroups.entrySet()) {
            int checkedId = entry.getValue().getCheckedChipId();
            if (checkedId != -1) {
                Chip chip = getView().findViewById(checkedId);
                if (chip != null && chip.getTag() != null) {
                    attributes.add(new CartItem.Attribute(entry.getKey(), chip.getTag().toString()));
                }
            }
        }
        return attributes;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getActivity() != null) {
            getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
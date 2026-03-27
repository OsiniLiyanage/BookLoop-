package lk.jiat.bookloop.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.CheckoutAdapter;
import lk.jiat.bookloop.databinding.FragmentCheckoutBinding;
import lk.jiat.bookloop.helper.BookLoopPreferences;
import lk.jiat.bookloop.helper.NotificationHelper;
import lk.jiat.bookloop.listener.FirestoreCallback;
import lk.jiat.bookloop.model.CartItem;
import lk.jiat.bookloop.model.Order;
import lk.jiat.bookloop.model.Product;

import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.PHResponse;
import lk.payhere.androidsdk.model.InitRequest;
import lk.payhere.androidsdk.model.StatusResponse;

/*
 * CheckoutFragment.java
 * Collects delivery address, shows order summary, then launches PayHere payment.
 *
 * FLOW:
 *   1. Screen opens → loads cart from Firestore, calculates total
 *   2. User fills in delivery details
 *   3. User taps "Proceed to Payment" → PayHere SDK opens
 *   4. On payment success → save Order to Firestore, clear cart, show notification
 *   5. Navigate to OrdersFragment so user can see their new order
 *
 * WHY PayHere: It is a Sri Lankan payment gateway that supports LKR.
 *              The SDK handles the payment UI — we just send it the amount and customer info.
 *
 * NOTE: setSandBox(true) means test mode — no real money moves.
 *       Set to false for real production payments.
 */
public class CheckoutFragment extends Fragment {

    private static final String TAG = "CheckoutFragment";

    // Flat delivery fee charged on every BookLoop order (LKR 100)
    private static final double DELIVERY_FEE = 100.0;

    private FragmentCheckoutBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth firebaseAuth;

    // Calculated totals — used both for display and for PayHere amount
    private double rentalSubtotal = 0;
    private double grandTotal     = 0;

    // True once cart data has loaded — stops the pay button working on empty data
    private boolean orderReady = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db           = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCheckoutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Hide the bottom navigation bar — checkout is a focused flow
        // The user should not be distracted by the bottom nav while paying
//        if (getActivity() != null) {
//            getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.GONE);
//        }

//        // Back button — go back to the cart screen
//        binding.checkoutBtnBack.setOnClickListener(v ->
//                getParentFragmentManager().beginTransaction()
//                        .replace(R.id.fragment_container, new CartFragment())
//                        .commit());

        // Pre-fill name and city from SharedPreferences (saved from last checkout)
        // WHY SharedPreferences: Small key-value data — perfect for "remember last typed value"
        BookLoopPreferences prefs = new BookLoopPreferences(requireContext());
        String savedName = prefs.getLastDeliveryName();
        String savedCity = prefs.getLastDeliveryCity();
        if (savedName != null) binding.checkoutName.setText(savedName);
        if (savedCity != null) binding.checkoutCity.setText(savedCity);

        // Pre-fill email from Firebase Auth (user is already logged in)
        if (firebaseAuth.getCurrentUser() != null
                && firebaseAuth.getCurrentUser().getEmail() != null) {
            binding.checkoutEmail.setText(firebaseAuth.getCurrentUser().getEmail());
        }

        // Load cart items and build the order summary section
        loadCartAndBuildSummary();

        // "Proceed to Payment" button — validates fields, then launches PayHere
        binding.checkoutBtnProceed.setOnClickListener(v -> {
            if (!orderReady) {
                toast("Loading your order, please wait...");
                return;
            }
            if (!validateFields()) return;

            // Save delivery details to SharedPreferences so they're pre-filled next visit
            prefs.saveLastDeliveryDetails(
                    binding.checkoutName.getText().toString().trim(),
                    binding.checkoutCity.getText().toString().trim()
            );

            // Launch the PayHere payment screen
            launchPayHere();
        });
    }

    // ─── Validate all required delivery fields ───────────────────────────────
    /*
     * validateFields()
     * Checks that the user has filled in everything needed before paying.
     * Returns true if all required fields are filled, false otherwise.
     * Each empty field shows a Toast message explaining what is missing.
     */
    private boolean validateFields() {
        String name    = binding.checkoutName.getText().toString().trim();
        String phone   = binding.checkoutPhone.getText().toString().trim();
        String email   = binding.checkoutEmail.getText().toString().trim();
        String address = binding.checkoutAddress1.getText().toString().trim();
        String city    = binding.checkoutCity.getText().toString().trim();

        if (name.isEmpty())    { toast("Please enter your full name");    return false; }
        if (phone.isEmpty())   { toast("Please enter your phone number"); return false; }
        if (email.isEmpty())   { toast("Please enter your email");        return false; }
        if (address.isEmpty()) { toast("Please enter your address");      return false; }
        if (city.isEmpty())    { toast("Please enter your city");         return false; }
        return true;
    }

    // ─── Load cart from Firestore and calculate totals ───────────────────────
    /*
     * loadCartAndBuildSummary()
     * Step 1: Get all cart items for this user from Firestore
     * Step 2: Fetch the product details (price, title) for each cart item
     * Step 3: Calculate:
     *           rentalSubtotal = sum of (pricePerWeek × rentalWeeks × copies)
     *           grandTotal     = rentalSubtotal + DELIVERY_FEE
     * Step 4: Show the books list in the summary card
     *
     * WHY price × rentalWeeks × copies:
     *   BookLoop charges PER WEEK, not per item. So a book at LKR 500/week,
     *   rented for 3 weeks, 2 copies = 500 × 3 × 2 = LKR 3000.
     */
    private void loadCartAndBuildSummary() {
        getCartItems(cartItems -> {
            if (cartItems == null || cartItems.isEmpty()) {
                toast("Your cart is empty");
                return;
            }

            List<String> productIds = new ArrayList<>();
            for (CartItem ci : cartItems) productIds.add(ci.getProductId());

            getProductsByIds(productIds, productMap -> {
                rentalSubtotal = 0;

                for (CartItem cartItem : cartItems) {
                    Product product = productMap.get(cartItem.getProductId());
                    if (product != null) {
                        // Make sure weeks and copies are at least 1 (defensive check)
                        int weeks  = cartItem.getRentalWeeks() > 0 ? cartItem.getRentalWeeks() : 1;
                        int copies = cartItem.getQuantity()    > 0 ? cartItem.getQuantity()    : 1;

                        // Add this item's cost: pricePerWeek × weeks × copies
                        rentalSubtotal += product.getPrice() * weeks * copies;
                    }
                }

                grandTotal = rentalSubtotal + DELIVERY_FEE;

                // Update the 3 totals shown in the Order Summary card
                binding.checkoutSubtotal.setText(String.format(Locale.US, "LKR %,.2f", rentalSubtotal));
                binding.checkoutShipping.setText(String.format(Locale.US, "LKR %,.2f", DELIVERY_FEE));
                binding.checkoutTotal.setText(String.format(Locale.US, "LKR %,.2f", grandTotal));

                // Populate the compact book list inside the order summary
                CheckoutAdapter adapter = new CheckoutAdapter(cartItems, productMap);
                binding.checkoutBooksList.setLayoutManager(new LinearLayoutManager(getContext()));
                binding.checkoutBooksList.setAdapter(adapter);

                // Mark order as ready — the payment button can now work
                orderReady = true;
            });
        });
    }

    // ─── Launch PayHere payment screen ───────────────────────────────────────
    /*
     * launchPayHere()
     * Builds an InitRequest with all the required PayHere fields and starts the
     * PayHere activity. PayHere handles the payment UI — we just pass the data.
     *
     * KEY FIELDS:
     *   - merchantId / merchantSecret: our PayHere sandbox credentials
     *   - amount: the grandTotal (rental subtotal + delivery fee)
     *   - orderId: unique ID — we use "BL-" + current timestamp
     *   - customer details: taken from the delivery form the user just filled in
     */
    private void launchPayHere() {
        InitRequest req = new InitRequest();

        // setSandBox(true) = test mode. Change to false for real payments.
        req.setSandBox(true);

        // Merchant credentials (sandbox/test values — replace with real ones for production)
        req.setMerchantId("1234773");
        req.setMerchantSecret("MjU2MDY0ODI3MjgyNDMxODUyMjI2ODg2OTg4MTMxMzQ2NDMzOTc2");

        req.setCurrency("LKR");
        req.setAmount(grandTotal);

        // Unique order ID — timestamp ensures it's different for every order
        req.setOrderId("BL-" + System.currentTimeMillis());
        req.setItemsDescription("BookLoop Rental Order");

        // Split full name into first + last for PayHere
        String fullName = binding.checkoutName.getText().toString().trim();
        String[] nameParts = fullName.split(" ", 2);
        req.getCustomer().setFirstName(nameParts[0]);
        req.getCustomer().setLastName(nameParts.length > 1 ? nameParts[1] : "");

        req.getCustomer().setEmail(binding.checkoutEmail.getText().toString().trim());
        req.getCustomer().setPhone(binding.checkoutPhone.getText().toString().trim());
        req.getCustomer().getAddress().setAddress(binding.checkoutAddress1.getText().toString().trim());
        req.getCustomer().getAddress().setCity(binding.checkoutCity.getText().toString().trim());
        req.getCustomer().getAddress().setCountry("Sri Lanka");

        // Start the PayHere activity — result comes back to payhereLauncher
        Intent intent = new Intent(getActivity(), PHMainActivity.class);
        intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);
        payhereLauncher.launch(intent);
    }

    // ─── Handle PayHere payment result ───────────────────────────────────────
    /*
     * payhereLauncher — registered with registerForActivityResult
     * Called automatically when PayHere closes and returns a result.
     *
     * RESULT_OK + isSuccess() → payment went through → save order to Firestore
     * RESULT_OK + !isSuccess() → payment was declined → show error
     * RESULT_CANCELED          → user pressed back in PayHere → show cancelled message
     */
    private final ActivityResultLauncher<Intent> payhereLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {

                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();

                    if (data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
                        PHResponse<StatusResponse> response =
                                (PHResponse<StatusResponse>) data.getSerializableExtra(
                                        PHConstants.INTENT_EXTRA_RESULT);

                        if (response != null && response.isSuccess()) {
                            // Payment succeeded — save the order to Firestore
                            Log.i(TAG, "PayHere payment successful");
                            saveOrderToFirestore(response.getData());

                        } else if (response != null) {
                            // Payment declined
                            Log.e(TAG, "PayHere declined: " + response.getData().getMessage());
                            toast("Payment failed. Please try again.");
                        }
                    }

                } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    Log.w(TAG, "User cancelled PayHere");
                    toast("Payment cancelled.");
                }
            });

    // ─── Save the confirmed order to Firestore ───────────────────────────────
    /*
     * saveOrderToFirestore()
     * After PayHere confirms payment, we store the full order details in Firestore.
     * The admin can then see this order in the Firebase console or admin dashboard.
     *
     * WHAT WE SAVE:
     *   - orderId, userId, totalAmount, status ("PAID")
     *   - orderDate (server timestamp)
     *   - orderItems: one entry per cart item, includes productTitle, rentalWeeks, etc.
     *   - shippingAddress: from the form the user filled in
     */
    private void saveOrderToFirestore(StatusResponse statusResponse) {
        if (firebaseAuth.getCurrentUser() == null) return;
        String uid = firebaseAuth.getCurrentUser().getUid();

        getCartItems(cartItems -> {
            if (cartItems == null || cartItems.isEmpty()) return;

            List<String> productIds = new ArrayList<>();
            for (CartItem ci : cartItems) productIds.add(ci.getProductId());

            getProductsByIds(productIds, productMap -> {
                // Build the Order object
                String orderId = "BL-" + System.currentTimeMillis();
                Order order = new Order();
                order.setOrderId(orderId);
                order.setUserId(uid);
                order.setTotalAmount(grandTotal);
                order.setStatus("PAID");
                order.setOrderDate(Timestamp.now());

                // Build one OrderItem for each cart entry
                List<Order.OrderItem> orderItems = new ArrayList<>();
                for (CartItem cartItem : cartItems) {
                    Product product = productMap.get(cartItem.getProductId());
                    if (product != null) {
                        int weeks = cartItem.getRentalWeeks() > 0 ? cartItem.getRentalWeeks() : 1;

                        // Convert cart attributes (e.g. Condition: Good) to order attributes
                        List<Order.OrderItem.Attribute> attrs = new ArrayList<>();
                        if (cartItem.getAttributes() != null) {
                            for (CartItem.Attribute a : cartItem.getAttributes()) {
                                attrs.add(Order.OrderItem.Attribute.builder()
                                        .name(a.getName()).value(a.getValue()).build());
                            }
                        }

                        orderItems.add(Order.OrderItem.builder()
                                .productId(cartItem.getProductId())
                                .productTitle(product.getTitle())       // stored for display
                                .productImage(product.getImages() != null
                                        && !product.getImages().isEmpty()
                                        ? product.getImages().get(0) : "")
                                .unitPrice(product.getPrice())          // LKR per week
                                .quantity(cartItem.getQuantity())       // copies
                                .rentalWeeks(weeks)                     // weeks rented
                                .attributes(attrs)
                                .build());
                    }
                }
                order.setOrderItems(orderItems);

                // Save shipping address from form
                order.setShippingAddress(Order.Address.builder()
                        .name(binding.checkoutName.getText().toString().trim())
                        .email(binding.checkoutEmail.getText().toString().trim())
                        .contact(binding.checkoutPhone.getText().toString().trim())
                        .address1(binding.checkoutAddress1.getText().toString().trim())
                        .address2(binding.checkoutAddress2.getText().toString().trim())
                        .city(binding.checkoutCity.getText().toString().trim())
                        .postcode(binding.checkoutPostcode.getText().toString().trim())
                        .build());

                // Write to Firestore — "orders" collection, auto-generated document ID
                db.collection("orders").document().set(order)
                        .addOnSuccessListener(aVoid -> {
                            Log.i(TAG, "Order saved to Firestore: " + orderId);

                            // Show a local notification confirming the order
                            // WHY: User gets instant feedback even if they close the app
                            NotificationHelper.showOrderConfirmation(
                                    requireContext(), orderId, grandTotal);

                            clearCartAndNavigate(uid);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Order save failed: " + e.getMessage());
                            toast("Order could not be saved. Contact support.");
                        });
            });
        });
    }

    // ─── Clear the cart after order is confirmed ─────────────────────────────
    private void clearCartAndNavigate(String uid) {
        db.collection("users").document(uid).collection("cart").get()
                .addOnSuccessListener(qds -> {
                    for (DocumentSnapshot ds : qds.getDocuments()) {
                        ds.getReference().delete();
                    }
                    toast("Order placed! Your books are on the way.");
                    // Go to Orders screen so the user can see their new order
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new OrdersFragment())
                            .commit();
                });
    }

    // ─── Helper: get all cart items for current user ─────────────────────────
    private void getCartItems(FirestoreCallback<List<CartItem>> callback) {
        if (firebaseAuth.getCurrentUser() == null) return;
        String uid = firebaseAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).collection("cart").get()
                .addOnSuccessListener(qds -> callback.onCallback(qds.toObjects(CartItem.class)))
                .addOnFailureListener(e -> Log.e(TAG, "Cart load failed: " + e.getMessage()));
    }

    // ─── Helper: fetch products by IDs into a Map<productId, Product> ────────
    private void getProductsByIds(List<String> ids,
                                  FirestoreCallback<Map<String, Product>> callback) {
        Map<String, Product> products = new HashMap<>();
        if (ids == null || ids.isEmpty()) { callback.onCallback(products); return; }
        db.collection("products").whereIn("productId", ids).get()
                .addOnSuccessListener(qds -> {
                    for (DocumentSnapshot ds : qds.getDocuments()) {
                        Product p = ds.toObject(Product.class);
                        if (p != null) products.put(p.getProductId(), p);
                    }
                    callback.onCallback(products);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Products load failed: " + e.getMessage()));
    }

    private void toast(String msg) {
        if (getContext() != null)
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

//    // Restore the bottom nav when the user leaves checkout
//    @Override
//    public void onStop() {
//        super.onStop();
//        if (getActivity() != null)
//            getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.VISIBLE);
//    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        if (getActivity() != null)
//            getActivity().findViewById(R.id.bottom_navigation_view).setVisibility(View.GONE);
//    }
}
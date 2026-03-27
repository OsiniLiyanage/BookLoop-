package lk.jiat.bookloop.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.transition.TransitionManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.databinding.FragmentCheckoutBinding;
import lk.jiat.bookloop.listener.FirestoreCallback;
import lk.jiat.bookloop.model.CartItem;
import lk.jiat.bookloop.model.Order;
import lk.jiat.bookloop.model.Product;
import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.PHResponse;
import lk.payhere.androidsdk.model.InitRequest;
import lk.payhere.androidsdk.model.StatusResponse;


public class CheckoutFragment extends Fragment {
    private FragmentCheckoutBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth firebaseAuth;
    private double total;
    private boolean paymentActive;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCheckoutBinding.inflate(inflater, container, false);


        binding.shippingLayoutBtn.setOnClickListener(v -> {

            if (binding.shippingLayoutBody.getVisibility() == View.GONE) {
                binding.shippingLayoutBody.setVisibility(View.VISIBLE);
                binding.shippingLayoutBtn.setRotation(180f);
            } else {
                binding.shippingLayoutBody.setVisibility(View.GONE);
                binding.shippingLayoutBtn.setRotation(0f);
            }
        });

        binding.billingLayoutBtn.setOnClickListener(v -> {
            if (binding.billingLayoutBody.getVisibility() == View.GONE) {
                binding.billingLayoutBody.setVisibility(View.VISIBLE);
                binding.billingLayoutBtn.setRotation(180f);
            } else {
                binding.billingLayoutBody.setVisibility(View.GONE);
                binding.billingLayoutBtn.setRotation(0f);
            }
        });


        binding.shippingDetailsCheckBilling.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                binding.billingLayout.setVisibility(View.GONE);
            } else {
                binding.billingLayout.setVisibility(View.VISIBLE);
                binding.billingLayoutBody.setVisibility(View.VISIBLE);
            }
        });


        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        double shippingCost = 400;

        getCartItems(cartItems -> {

            ArrayList<String> productIds = new ArrayList<>();
            cartItems.forEach(cartItem -> {
                productIds.add(cartItem.getProductId());
            });


            getProductsByIds(productIds, data -> {

                double subTotal = 0;

                for (CartItem cartItem : cartItems) {
                    Product product = data.get(cartItem.getProductId());
                    if (product != null) {
                        subTotal += product.getPrice() * cartItem.getQuantity();
                    }
                }

                total = subTotal + shippingCost;
                binding.checkoutSubtotal.setText(String.format(Locale.US, "LKR %,.2f", subTotal));
                binding.checkoutShipping.setText(String.format(Locale.US, "LKR %,.2f", shippingCost));
                binding.checkoutTotal.setText(String.format(Locale.US, "LKR %,.2f", total));
                paymentActive = true;
            });

        });


        binding.checkoutBtnProceed.setOnClickListener(v -> {

            if (paymentActive) {
                InitRequest req = new InitRequest();
                req.setSandBox(true);

                req.setMerchantId("1234773");
                req.setMerchantSecret("MjU2MDY0ODI3MjgyNDMxODUyMjI2ODg2OTg4MTMxMzQ2NDMzOTc2");
                req.setCurrency("LKR");
                req.setAmount(total);
                req.setOrderId("ESOI-001");

                req.setItemsDescription("");

                req.getCustomer().setFirstName(binding.shippingDetailsName.getText().toString());
                req.getCustomer().setLastName("Lakshan"); //need to fill coreect like line like line 151
                req.getCustomer().setEmail("navodaoshini@gmail.com");
                req.getCustomer().setPhone("+94785050506");
                req.getCustomer().getAddress().setAddress("No.1, Galle Road");
                req.getCustomer().getAddress().setCity("Colombo");
                req.getCustomer().getAddress().setCountry("Sri Lanka");

                //req.setNotifyUrl("https://bookloop.requestcatcher.com/");

                Intent intent = new Intent(getActivity(), PHMainActivity.class);
                intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);

                payhereLauncher.launch(intent);
            }

        });


    }


    private void getCartItems(FirestoreCallback<List<CartItem>> callback) {
        String uid = firebaseAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).collection("cart").get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
            @Override
            public void onSuccess(QuerySnapshot qds) {
                if (!qds.isEmpty()) {
                    List<CartItem> cartItems = qds.toObjects(CartItem.class);
                    callback.onCallback(cartItems);
                }
            }
        });
    }

    private void getProductsByIds(List<String> productIds, FirestoreCallback<Map<String, Product>> callback) {

        Map<String, Product> products = new HashMap<>();

        if (productIds == null || productIds.isEmpty()) {
            callback.onCallback(products);
            return;
        }

        db.collection("products").whereIn("productId", productIds).get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
            @Override
            public void onSuccess(QuerySnapshot qds) {

                qds.getDocuments().forEach(ds -> {
                    Product product = ds.toObject(Product.class);
                    if (product != null) {
                        products.put(product.getProductId(), product);
                    }
                });

                callback.onCallback(products);
            }
        });

    }

    private final ActivityResultLauncher<Intent> payhereLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {

        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Intent data = result.getData();
            if (data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
                PHResponse<StatusResponse> response = (PHResponse<StatusResponse>) data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT);

                if (response != null && response.isSuccess()) {

                    StatusResponse statusResponse = response.getData();


                    // Save order to firestore
                    saveOrder(statusResponse);


                    Log.i("PAYHERE", "Payment Success!");

                } else {
                    Log.e("PAYHERE", response.getData().getMessage());
                }

            }
        } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
            Log.e("PAYHERE", "Payment Canceled!");
        }

    });

    private void saveOrder(StatusResponse statusResponse) {
        getCartItems(cartItems -> {

            String uid = firebaseAuth.getCurrentUser().getUid();

            Order order = new Order();
            order.setOrderId(String.valueOf(System.currentTimeMillis()));
            order.setUserId(uid);
            order.setTotalAmount(total);
            order.setStatus("PAID");
            order.setOrderDate(Timestamp.now());


            String shipping_name = binding.shippingDetailsName.getText().toString();
            String shipping_email = binding.shippingDetailsEmail.getText().toString();
            String shipping_contact = binding.shippingDetailsContact.getText().toString();
            String shipping_address1 = binding.shippingDetailsAddress1.getText().toString();
            String shipping_address2 = binding.shippingDetailsAddress2.getText().toString();
            String shipping_city = binding.shippingDetailsCity.getText().toString();
            String shipping_postCode = binding.shippingDetailsPostcode.getText().toString();

            Order.Address shippingAddress = Order.Address.builder().name(shipping_name).email(shipping_email).contact(shipping_contact).address1(shipping_address1).address2(shipping_address2).city(shipping_city).postcode(shipping_postCode).build();

            order.setShippingAddress(shippingAddress);

            if (!binding.shippingDetailsCheckBilling.isChecked()) {
                String billing_name = binding.billingDetailsName.getText().toString();
                String billing_email = binding.billingDetailsEmail.getText().toString();
                String billing_contact = binding.billingDetailsContact.getText().toString();
                String billing_address1 = binding.billingDetailsAddress1.getText().toString();
                String billing_address2 = binding.billingDetailsAddress2.getText().toString();
                String billing_city = binding.billingDetailsCity.getText().toString();
                String billing_postCode = binding.billingDetailsPostcode.getText().toString();

                Order.Address billingAddress = Order.Address.builder().name(billing_name).email(billing_email).contact(billing_contact).address1(billing_address1).address2(billing_address2).city(billing_city).postcode(billing_postCode).build();

                order.setBillingAddress(billingAddress);
            }

            /// //////
            ArrayList<String> productIds = new ArrayList<>();
            cartItems.forEach(cartItem -> {
                productIds.add(cartItem.getProductId());
            });

            List<Order.OrderItem> orderItems = new ArrayList<>();

            getProductsByIds(productIds, data -> {

                for (CartItem cartItem : cartItems) {
                    Product product = data.get(cartItem.getProductId());

                    if (product != null) {

                        List<Order.OrderItem.Attribute> attributes = new ArrayList<>();

                        for (CartItem.Attribute at : cartItem.getAttributes()) {
                            Order.OrderItem.Attribute attribute = Order.OrderItem.Attribute.builder().name(at.getName()).value(at.getValue()).build();

                            attributes.add(attribute);
                        }

                        Order.OrderItem orderItem = Order.OrderItem.builder().productId(cartItem.getProductId()).unitPrice(product.getPrice()).quantity(cartItem.getQuantity()).attributes(attributes).build();
                        orderItems.add(orderItem);


                        ///  Add order items to Oder object
                        order.setOrderItems(orderItems);

                    }
                }
                db.collection("orders").document().set(order).addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Order Saved!", Toast.LENGTH_SHORT).show();

                    // Clear cart
                    db.collection("users").document(uid).collection("cart")
                            .get()
                            .addOnSuccessListener(qds -> {
                                qds.getDocuments().forEach(ds -> {
                                    ds.getReference().delete();
                                });
                            });


                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new HomeFragment())
                            .commit();

                });

            });


        });
    }
}

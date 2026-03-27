package lk.jiat.bookloop.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.List;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.OrdersAdapter;
import lk.jiat.bookloop.databinding.FragmentOrdersBinding;
import lk.jiat.bookloop.model.Order;

// My Rentals screen — loads all orders for the logged-in user.
//
// WHY THE OLD VERSION GAVE 400 BAD REQUEST:
//   Using .whereEqualTo("userId", uid).orderBy("orderDate") in Firestore requires a
//   COMPOSITE INDEX (userId + orderDate together). Without it, Firestore returns
//   a "Bad Request" error. Creating the index takes time and you need access to the console.
//
// HOW THIS VERSION FIXES IT:
//   We remove .orderBy() from the Firestore query entirely.
//   Instead we sort the list in Java after loading, which works without any index.
//   Result: same sorted order, no index needed, no 400 error.
public class OrdersFragment extends Fragment {

    private static final String TAG = "OrdersFragment";
    private FragmentOrdersBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentOrdersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // "Browse books" button on the empty state screen
        binding.ordersBtnBrowse.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new HomeFragment())
                        .commit());

        loadOrders();
    }

    private void loadOrders() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            showEmpty();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        // FIX: removed .orderBy("orderDate") — that caused the 400 Bad Request error
        // because Firestore requires a composite index for whereEqualTo + orderBy together.
        // We sort the result ourselves in Java below — same outcome, no index needed.
        FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(qds -> {
                    binding.ordersLoading.setVisibility(View.GONE);

                    if (qds.isEmpty()) {
                        showEmpty();
                        return;
                    }

                    List<Order> orders = qds.toObjects(Order.class);

                    // Sort newest-first in Java — replaces the missing .orderBy()
                    // Orders with null orderDate go to the end
                    orders.sort((a, b) -> {
                        if (a.getOrderDate() == null && b.getOrderDate() == null) return 0;
                        if (a.getOrderDate() == null) return 1;
                        if (b.getOrderDate() == null) return -1;
                        return b.getOrderDate().compareTo(a.getOrderDate()); // newest first
                    });

                    binding.ordersRecycler.setVisibility(View.VISIBLE);
                    binding.ordersRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
                    binding.ordersRecycler.setAdapter(new OrdersAdapter(orders));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load orders: " + e.getMessage());
                    showEmpty();
                });
    }

    private void showEmpty() {
        binding.ordersLoading.setVisibility(View.GONE);
        binding.ordersEmptyState.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
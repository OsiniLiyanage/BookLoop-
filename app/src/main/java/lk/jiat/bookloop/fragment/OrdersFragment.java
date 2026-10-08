package lk.jiat.bookloop.fragment;

import android.os.Bundle;
import android.os.Parcelable;
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
import com.google.firebase.firestore.ListenerRegistration;

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

    // Live listener handle - kept so we can STOP listening in onDestroyView()
    private ListenerRegistration ordersListener;

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

    // LIVE ORDERS: addSnapshotListener() keeps a real-time connection to Firestore.
    // Whenever an order changes (e.g. admin sets PROCESSING -> DELIVERED), Firestore
    // calls the listener again and the list refreshes by itself - no manual refresh.
    // The old .get() read the data only ONCE.
    private void loadOrders() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            showEmpty();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        // Stop any previous listener so we never have two running at once
        if (ordersListener != null) {
            ordersListener.remove();
            ordersListener = null;
        }

        // No .orderBy() here (needs a composite index) - we sort in Java below.
        ordersListener = FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("userId", uid)
                .addSnapshotListener((qds, error) -> {
                    // View may already be destroyed when a late update arrives
                    if (binding == null) return;

                    if (error != null) {
                        Log.e(TAG, "Live orders listener failed: " + error.getMessage());
                        binding.ordersLoading.setVisibility(View.GONE);
                        // Keep showing whatever list we already have; only show the
                        // empty screen if there is nothing on screen yet.
                        if (binding.ordersRecycler.getAdapter() == null) showEmpty();
                        return;
                    }
                    if (qds == null) return;

                    Log.d(TAG, "Live update: " + qds.size() + " orders (fromCache="
                            + qds.getMetadata().isFromCache() + ")");

                    binding.ordersLoading.setVisibility(View.GONE);

                    if (qds.isEmpty()) {
                        showEmpty();
                        return;
                    }

                    List<Order> orders = qds.toObjects(Order.class);

                    // Sort newest-first in Java - replaces the missing .orderBy()
                    // Orders with null orderDate go to the end
                    orders.sort((a, b) -> {
                        if (a.getOrderDate() == null && b.getOrderDate() == null) return 0;
                        if (a.getOrderDate() == null) return 1;
                        if (b.getOrderDate() == null) return -1;
                        return b.getOrderDate().compareTo(a.getOrderDate()); // newest first
                    });

                    binding.ordersEmptyState.setVisibility(View.GONE);
                    binding.ordersRecycler.setVisibility(View.VISIBLE);

                    // Create the layout manager once, and remember the scroll position
                    // so the list does not jump to the top on every live update.
                    if (binding.ordersRecycler.getLayoutManager() == null) {
                        binding.ordersRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
                    }
                    Parcelable scrollState =
                            binding.ordersRecycler.getLayoutManager().onSaveInstanceState();

                    binding.ordersRecycler.setAdapter(new OrdersAdapter(orders));

                    binding.ordersRecycler.getLayoutManager().onRestoreInstanceState(scrollState);
                });
    }

    private void showEmpty() {
        binding.ordersLoading.setVisibility(View.GONE);
        binding.ordersRecycler.setVisibility(View.GONE);
        binding.ordersEmptyState.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // STOP the live listener - otherwise it keeps running after the screen is gone
        if (ordersListener != null) {
            ordersListener.remove();
            ordersListener = null;
        }
        binding = null;
    }
}
package lk.jiat.bookloop.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.model.Order;

// NEW — Adapter for the My Orders / My Rentals list
public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.ViewHolder> {

    private final List<Order> orders;
    private final FirebaseStorage storage;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public OrdersAdapter(List<Order> orders) {
        this.orders = orders;
        this.storage = FirebaseStorage.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);

        // Order ID — show last 8 chars for readability
        String displayId = order.getOrderId() != null && order.getOrderId().length() > 8
                ? "Order #" + order.getOrderId().substring(order.getOrderId().length() - 8)
                : "Order #" + order.getOrderId();
        holder.orderId.setText(displayId);

        // Date
        if (order.getOrderDate() != null) {
            holder.orderDate.setText("Placed on " + DATE_FMT.format(order.getOrderDate().toDate()));
        }

        // Status chip colour
        holder.orderStatus.setText(order.getStatus() != null ? order.getStatus() : "PLACED");
        switch (order.getStatus() != null ? order.getStatus() : "") {
            case "PAID":
            case "PROCESSING":
                holder.orderStatus.setBackgroundColor(Color.parseColor("#E8F5E9"));
                holder.orderStatus.setTextColor(Color.parseColor("#2E7D32"));
                break;
            case "DELIVERED":
                holder.orderStatus.setBackgroundColor(Color.parseColor("#E3F2FD"));
                holder.orderStatus.setTextColor(Color.parseColor("#1565C0"));
                break;
            case "RETURNED":
                holder.orderStatus.setBackgroundColor(Color.parseColor("#FFF3E0"));
                holder.orderStatus.setTextColor(Color.parseColor("#E65100"));
                break;
            default:
                holder.orderStatus.setBackgroundColor(Color.parseColor("#F3E5F5"));
                holder.orderStatus.setTextColor(Color.parseColor("#6A1B9A"));
                break;
        }

        // Total
        holder.orderTotal.setText(String.format(Locale.US, "LKR %,.2f", order.getTotalAmount()));

        // Delivery address snippet
        if (order.getShippingAddress() != null && order.getShippingAddress().getCity() != null) {
            holder.deliveryAddress.setText("📍 " + order.getShippingAddress().getCity());
        }

        // Nested book list inside the order card
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            OrderBooksAdapter booksAdapter = new OrderBooksAdapter(order.getOrderItems(), storage);
            holder.booksRecycler.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
            holder.booksRecycler.setAdapter(booksAdapter);
        }
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView orderId, orderDate, orderStatus, orderTotal, deliveryAddress;
        RecyclerView booksRecycler;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            orderId = itemView.findViewById(R.id.order_id);
            orderDate = itemView.findViewById(R.id.order_date);
            orderStatus = itemView.findViewById(R.id.order_status);
            orderTotal = itemView.findViewById(R.id.order_total);
            deliveryAddress = itemView.findViewById(R.id.order_delivery_address);
            booksRecycler = itemView.findViewById(R.id.order_books_recycler);
        }
    }

    // ── Nested adapter for books inside each order card ──────────────────────
    // NEW — shows book thumbnail + title + copies × weeks + subtotal
    static class OrderBooksAdapter extends RecyclerView.Adapter<OrderBooksAdapter.BVH> {

        private final List<Order.OrderItem> items;
        private final FirebaseStorage storage;

        OrderBooksAdapter(List<Order.OrderItem> items, FirebaseStorage storage) {
            this.items = items;
            this.storage = storage;
        }

        @NonNull
        @Override
        public BVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order_book, parent, false);
            return new BVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull BVH holder, int position) {
            Order.OrderItem item = items.get(position);

            holder.title.setText(item.getProductTitle() != null ? item.getProductTitle() : item.getProductId());

            int weeks = item.getRentalWeeks() > 0 ? item.getRentalWeeks() : 1;
            int copies = item.getQuantity() > 0 ? item.getQuantity() : 1;
            holder.meta.setText(copies + " cop" + (copies == 1 ? "y" : "ies") + " × " + weeks + " week" + (weeks == 1 ? "" : "s"));

            // NEW — Correct subtotal using saved rentalWeeks
            double subtotal = item.getUnitPrice() * weeks * copies;
            holder.subtotal.setText(String.format(Locale.US, "LKR %,.2f", subtotal));

            // Load book thumbnail from Firebase Storage
            if (item.getProductImage() != null && !item.getProductImage().isEmpty()) {
                storage.getReference(item.getProductImage()).getDownloadUrl()
                        .addOnSuccessListener(uri ->
                                Glide.with(holder.itemView.getContext())
                                        .load(uri)
                                        .centerCrop()
                                        .into(holder.image));
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class BVH extends RecyclerView.ViewHolder {
            ImageView image;
            TextView title, meta, subtotal;
            BVH(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.order_book_image);
                title = v.findViewById(R.id.order_book_title);
                meta = v.findViewById(R.id.order_book_meta);
                subtotal = v.findViewById(R.id.order_book_subtotal);
            }
        }
    }
}
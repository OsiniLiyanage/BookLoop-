package lk.jiat.bookloop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.model.CartItem;
import lk.jiat.bookloop.model.Product;

// CheckoutAdapter — shows each book as a compact row inside the checkout order summary card.
// WHY: User needs to see what they're paying for before confirming.
// Each row shows: book title, copies × weeks, and the subtotal for that book.
public class CheckoutAdapter extends RecyclerView.Adapter<CheckoutAdapter.ViewHolder> {

    private final List<CartItem> cartItems;
    private final Map<String, Product> productMap; // productId → Product (to get title and price)

    public CheckoutAdapter(List<CartItem> cartItems, Map<String, Product> productMap) {
        this.cartItems = cartItems;
        this.productMap = productMap;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the small book row layout (NOT the full checkout layout!)
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_checkout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem cartItem = cartItems.get(position);
        Product product = productMap.get(cartItem.getProductId());

        if (product != null) {
            // Show book title
            holder.title.setText(product.getTitle());

            // Show copies and weeks — e.g. "2 copies × 3 weeks"
            int weeks = cartItem.getRentalWeeks() > 0 ? cartItem.getRentalWeeks() : 1;
            int copies = cartItem.getQuantity() > 0 ? cartItem.getQuantity() : 1;
            String meta = copies + " cop" + (copies == 1 ? "y" : "ies")
                    + " × " + weeks + " week" + (weeks == 1 ? "" : "s");
            holder.meta.setText(meta);

            // Calculate and show subtotal: price × weeks × copies
            double subtotal = product.getPrice() * weeks * copies;
            holder.subtotal.setText(String.format(Locale.US, "LKR %,.2f", subtotal));
        }
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;     // book title
        TextView meta;      // "2 copies × 3 weeks"
        TextView subtotal;  // "LKR 1,500.00"

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title    = itemView.findViewById(R.id.checkout_item_title);
            meta     = itemView.findViewById(R.id.checkout_item_meta);
            subtotal = itemView.findViewById(R.id.checkout_item_subtotal);
        }
    }
}
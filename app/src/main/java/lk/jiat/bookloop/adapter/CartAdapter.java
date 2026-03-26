package lk.jiat.bookloop.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.model.CartItem;
import lk.jiat.bookloop.model.Product;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    private List<CartItem> cartItems;
    private OnCartItemChangeListener changeListener;
    private OnRemoveListener removeListener;

    public CartAdapter(List<CartItem> cartItems) {
        this.cartItems = cartItems;
    }

    public void setOnCartItemChangeListener(OnCartItemChangeListener listener) {
        this.changeListener = listener;
    }

    public void setOnRemoveListener(OnRemoveListener listener) {
        this.removeListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem cartItem = cartItems.get(position);

        // Load product details from Firestore to fill in title, author, price, image
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("products")
                .whereEqualTo("productId", cartItem.getProductId())
                .get()
                .addOnSuccessListener(qds -> {
                    if (!qds.isEmpty()) {
                        int currentPosition = holder.getAbsoluteAdapterPosition();
                        if (currentPosition == RecyclerView.NO_POSITION) return;

                        Product product = qds.getDocuments().get(0).toObject(Product.class);

                        // ── Fill static info ──────────────────────────────────────────────────
                        holder.productTitle.setText(product.getTitle());

                        // Show author if available
                        if (product.getAuthor() != null && !product.getAuthor().isEmpty()) {
                            holder.productAuthor.setText("by " + product.getAuthor());
                        } else {
                            holder.productAuthor.setText("Unknown Author");
                        }

                        // Show price per week
                        holder.productPrice.setText(String.format(Locale.US, "LKR %,.0f/week", product.getPrice()));

                        // Load book cover image
                        if (product.getImages() != null && !product.getImages().isEmpty()) {
                            Glide.with(holder.itemView.getContext())
                                    .load(product.getImages().get(0))
                                    .centerCrop()
                                    .into(holder.productImage);
                        }

                        // ── Show condition from attributes ────────────────────────────────────
                        // Find "Condition" attribute from cart item's saved selections
                        String conditionText = "—";
                        if (cartItem.getAttributes() != null) {
                            for (CartItem.Attribute attr : cartItem.getAttributes()) {
                                if ("Condition".equals(attr.getName())) {
                                    conditionText = "Condition: " + attr.getValue();
                                    break;
                                }
                            }
                        }
                        holder.productCondition.setText(conditionText);

                        // ── Set initial stepper values from cartItem ──────────────────────────
                        holder.weeksCount.setText(String.valueOf(cartItem.getRentalWeeks()));
                        holder.productQuantity.setText(String.valueOf(cartItem.getQuantity()));

                        // Calculate and show initial subtotal
                        updateSubtotal(holder, product.getPrice(), cartItem.getRentalWeeks(), cartItem.getQuantity());

                        // ── WEEKS minus button ────────────────────────────────────────────────
                        holder.btnWeeksMinus.setOnClickListener(v -> {
                            if (cartItem.getRentalWeeks() > 1) {
                                cartItem.setRentalWeeks(cartItem.getRentalWeeks() - 1);
                                holder.weeksCount.setText(String.valueOf(cartItem.getRentalWeeks()));
                                updateSubtotal(holder, product.getPrice(), cartItem.getRentalWeeks(), cartItem.getQuantity());
                                // Notify Firestore + total recalc
                                if (changeListener != null) changeListener.onChanged(cartItem);
                            }
                        });

                        // ── WEEKS plus button ─────────────────────────────────────────────────
                        holder.btnWeeksPlus.setOnClickListener(v -> {
                            cartItem.setRentalWeeks(cartItem.getRentalWeeks() + 1);
                            holder.weeksCount.setText(String.valueOf(cartItem.getRentalWeeks()));
                            updateSubtotal(holder, product.getPrice(), cartItem.getRentalWeeks(), cartItem.getQuantity());
                            if (changeListener != null) changeListener.onChanged(cartItem);
                        });

                        // ── COPIES minus button ───────────────────────────────────────────────
                        holder.btnMinus.setOnClickListener(v -> {
                            if (cartItem.getQuantity() > 1) {
                                cartItem.setQuantity(cartItem.getQuantity() - 1);
                                holder.productQuantity.setText(String.valueOf(cartItem.getQuantity()));
                                updateSubtotal(holder, product.getPrice(), cartItem.getRentalWeeks(), cartItem.getQuantity());
                                if (changeListener != null) changeListener.onChanged(cartItem);
                            }
                        });

                        // ── COPIES plus button ────────────────────────────────────────────────
                        holder.btnPlus.setOnClickListener(v -> {
                            if (cartItem.getQuantity() < product.getStockCount()) {
                                cartItem.setQuantity(cartItem.getQuantity() + 1);
                                holder.productQuantity.setText(String.valueOf(cartItem.getQuantity()));
                                updateSubtotal(holder, product.getPrice(), cartItem.getRentalWeeks(), cartItem.getQuantity());
                                if (changeListener != null) changeListener.onChanged(cartItem);
                            }
                        });

                        // ── Remove button ─────────────────────────────────────────────────────
                        holder.btnRemove.setOnClickListener(v -> {
                            int pos = holder.getAbsoluteAdapterPosition();
                            Log.i("CART_REMOVE", "Removing item at position: " + pos);
                            if (pos != RecyclerView.NO_POSITION && removeListener != null) {
                                removeListener.onRemoved(pos);
                            }
                        });
                    }
                });
    }

    // ─── Calculates and displays subtotal for a single cart item ─────────────
    // subtotal = price per week × weeks × copies
    private void updateSubtotal(ViewHolder holder, double pricePerWeek, int weeks, int copies) {
        double subtotal = pricePerWeek * weeks * copies;
        holder.itemSubtotal.setText(String.format(Locale.US, "LKR %,.0f", subtotal));
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView productTitle;
        TextView productAuthor;     // NEW — author name
        TextView productCondition;  // NEW — shows selected condition chip value
        TextView productPrice;
        TextView productQuantity;   // copies count
        TextView weeksCount;        // NEW — rental weeks count
        TextView itemSubtotal;      // NEW — per-item subtotal
        ImageView btnPlus;          // copies +
        ImageView btnMinus;         // copies -
        ImageView btnWeeksPlus;     // NEW — weeks +
        ImageView btnWeeksMinus;    // NEW — weeks -
        ImageView btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            productImage    = itemView.findViewById(R.id.item_cart_image);
            productTitle    = itemView.findViewById(R.id.item_cart_title);
            productAuthor   = itemView.findViewById(R.id.item_cart_author);
            productCondition= itemView.findViewById(R.id.item_cart_condition);
            productPrice    = itemView.findViewById(R.id.item_cart_price);
            productQuantity = itemView.findViewById(R.id.item_cart_quantity);
            weeksCount      = itemView.findViewById(R.id.item_cart_weeks);
            itemSubtotal    = itemView.findViewById(R.id.item_cart_subtotal);
            btnPlus         = itemView.findViewById(R.id.item_cart_btn_plus);
            btnMinus        = itemView.findViewById(R.id.item_cart_btn_minus);
            btnWeeksPlus    = itemView.findViewById(R.id.item_cart_btn_weeks_plus);
            btnWeeksMinus   = itemView.findViewById(R.id.item_cart_btn_weeks_minus);
            btnRemove       = itemView.findViewById(R.id.item_cart_remove);
        }
    }

    // Called whenever weeks or copies change — CartFragment uses this to update Firestore + total
    public interface OnCartItemChangeListener {
        void onChanged(CartItem cartItem);
    }

    public interface OnRemoveListener {
        void onRemoved(int position);
    }
}
package lk.jiat.bookloop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;

import java.util.List;
import java.util.Locale;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.helper.WishlistDatabase;

/*
 * WishlistAdapter.java
 * ────────────────────
 * RecyclerView adapter for the wishlist list.
 * Data comes from local SQLite cache (WishlistDatabase).
 *
 * Each item has 3 actions:
 *   1. "View" button → opens ProductDetailsFragment (onRentClick)
 *   2. "Move to Cart" button → adds to cart, removes from wishlist (onMoveToCartClick)
 *   3. Trash icon → removes from wishlist only (onRemoveClick)
 */
public class WishlistAdapter extends RecyclerView.Adapter<WishlistAdapter.ViewHolder> {

    public interface OnWishlistActionListener {
        void onRentClick(WishlistDatabase.WishlistItem item);
        void onMoveToCartClick(WishlistDatabase.WishlistItem item, int position);
        void onRemoveClick(WishlistDatabase.WishlistItem item, int position);
    }

    private final List<WishlistDatabase.WishlistItem> items;
    private final OnWishlistActionListener listener;
    private final FirebaseStorage storage;

    public WishlistAdapter(List<WishlistDatabase.WishlistItem> items, OnWishlistActionListener listener) {
        this.items = items;
        this.listener = listener;
        this.storage = FirebaseStorage.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wishlist, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WishlistDatabase.WishlistItem item = items.get(position);

        holder.title.setText(item.title);
        holder.author.setText(item.author != null ? item.author : "");
        holder.price.setText(String.format(Locale.US, "LKR %,.0f/week", item.price));

        // Load cover image from Firebase Storage
        if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            storage.getReference(item.imageUrl).getDownloadUrl()
                    .addOnSuccessListener(uri ->
                            Glide.with(holder.itemView.getContext())
                                    .load(uri)
                                    .centerCrop()
                                    .into(holder.image));
        }

        // "View" button — open product page
        holder.btnView.setOnClickListener(v -> {
            if (listener != null) listener.onRentClick(item);
        });

        // "Move to Cart" button — add to cart then remove from wishlist
        holder.btnMoveToCart.setOnClickListener(v -> {
            if (listener != null) listener.onMoveToCartClick(item, holder.getAdapterPosition());
        });

        // Trash icon — remove from wishlist only
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) listener.onRemoveClick(item, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    // Remove item from list after deletion
    public void removeAt(int position) {
        items.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, items.size());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title, author, price;
        com.google.android.material.button.MaterialButton btnView, btnMoveToCart, btnRemove;

        ViewHolder(@NonNull View v) {
            super(v);
            image       = v.findViewById(R.id.wishlist_book_image);
            title       = v.findViewById(R.id.wishlist_book_title);
            author      = v.findViewById(R.id.wishlist_book_author);
            price       = v.findViewById(R.id.wishlist_book_price);
            btnView     = v.findViewById(R.id.wishlist_btn_rent);
            btnMoveToCart = v.findViewById(R.id.wishlist_btn_move_to_cart);
            btnRemove   = v.findViewById(R.id.wishlist_btn_remove);
        }
    }
}
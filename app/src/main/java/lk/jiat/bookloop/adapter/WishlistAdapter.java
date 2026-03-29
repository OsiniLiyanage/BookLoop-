package lk.jiat.bookloop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

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
 * BUG FIX — crash: "location should not be a full URL"
 *   FirebaseStorage.getReference(url) only accepts a Storage path like
 *   "images/cover.jpg", NOT a full https:// download URL. But when a book
 *   is saved to the SQLite wishlist, the imageUrl stored is whatever came
 *   from Firestore — which is a full https://firebasestorage.googleapis.com/...
 *   download URL. Calling storage.getReference(fullUrl) throws:
 *     IllegalArgumentException: location should not be a full URL
 *   and crashes the whole app as soon as the RecyclerView tries to bind
 *   any item that has an image.
 *
 *   Fix: removed FirebaseStorage entirely from this adapter. Since the
 *   imageUrl is already a full download URL, just pass it straight to
 *   Glide.with(...).load(url) — Glide handles https:// URLs natively,
 *   no Storage reference needed.
 *
 * BUG FIX — stale adapter position crash:
 *   getAdapterPosition() is deprecated and returns NO_ID (-1) when the
 *   ViewHolder is mid-animation after removal. Passing -1 to remove()
 *   causes IndexOutOfBoundsException. Fixed with getBindingAdapterPosition()
 *   and a NO_ID guard.
 */
public class WishlistAdapter extends RecyclerView.Adapter<WishlistAdapter.ViewHolder> {

    public interface OnWishlistActionListener {
        void onRentClick(WishlistDatabase.WishlistItem item);
        void onMoveToCartClick(WishlistDatabase.WishlistItem item, int position);
        void onRemoveClick(WishlistDatabase.WishlistItem item, int position);
    }

    private final List<WishlistDatabase.WishlistItem> items;
    private final OnWishlistActionListener listener;

    // FIX: FirebaseStorage field removed — it caused the crash. Glide handles
    // full https:// URLs directly so we don't need the Storage SDK here at all.

    public WishlistAdapter(List<WishlistDatabase.WishlistItem> items,
                           OnWishlistActionListener listener) {
        this.items    = items;
        this.listener = listener;
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

        holder.title.setText(item.title  != null ? item.title  : "");
        holder.author.setText(item.author != null ? item.author : "");
        holder.price.setText(String.format(Locale.US, "LKR %,.0f/week", item.price));

        // FIX: load image directly with Glide using the full URL.
        // The old code called storage.getReference(item.imageUrl) which threw
        // IllegalArgumentException when imageUrl was an https:// download URL.
        if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(item.imageUrl)   // imageUrl is already a full https:// URL
                    .centerCrop()
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(holder.image);
        }

        holder.btnView.setOnClickListener(v -> {
            if (listener != null) listener.onRentClick(item);
        });

        holder.btnMoveToCart.setOnClickListener(v -> {
            if (listener == null) return;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_ID) return;
            listener.onMoveToCartClick(item, pos);
        });

        holder.btnRemove.setOnClickListener(v -> {
            if (listener == null) return;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_ID) return;
            listener.onRemoveClick(item, pos);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void removeAt(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, items.size());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView  title, author, price;
        com.google.android.material.button.MaterialButton btnView, btnMoveToCart, btnRemove;

        ViewHolder(@NonNull View v) {
            super(v);
            image         = v.findViewById(R.id.wishlist_book_image);
            title         = v.findViewById(R.id.wishlist_book_title);
            author        = v.findViewById(R.id.wishlist_book_author);
            price         = v.findViewById(R.id.wishlist_book_price);
            btnView       = v.findViewById(R.id.wishlist_btn_rent);
            btnMoveToCart = v.findViewById(R.id.wishlist_btn_move_to_cart);
            btnRemove     = v.findViewById(R.id.wishlist_btn_remove);
        }
    }

}
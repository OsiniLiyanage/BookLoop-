package lk.jiat.bookloop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.model.Product;

// SectionAdapter — used for horizontal book sections (Same Category, Same Author, Top Rated)
public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.ViewHolder> {

    private List<Product> products;
    private OnListingItemClickListener listener;

    public SectionAdapter(List<Product> products, OnListingItemClickListener listener) {
        this.products = products;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product_recycler, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = products.get(position);

        holder.productTitle.setText(product.getTitle());

        // Show author if available
        if (product.getAuthor() != null && !product.getAuthor().isEmpty()) {
            holder.productAuthor.setText(product.getAuthor());
        } else {
            holder.productAuthor.setText("Unknown Author");
        }

        // Price as weekly rental
        holder.productPrice.setText("LKR " + (int) product.getPrice() + "/week");

        // Load book cover image
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(product.getImages().get(0))
                    .centerCrop()
                    .placeholder(R.color.md_theme_surfaceVariant)
                    .into(holder.productImage);
        }

        holder.itemView.setOnClickListener(v -> {
            Animation animation = AnimationUtils.loadAnimation(v.getContext(), R.anim.click_animation);
            v.startAnimation(animation);
            if (listener != null) {
                listener.onListingItemClick(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView productTitle;
        TextView productAuthor;  // NEW — author field added
        TextView productPrice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            productImage  = itemView.findViewById(R.id.item_product_r_image);
            productTitle  = itemView.findViewById(R.id.item_product_r_name);
            productAuthor = itemView.findViewById(R.id.item_product_r_author); // NEW
            productPrice  = itemView.findViewById(R.id.item_product_r_price);
        }
    }

    public interface OnListingItemClickListener {
        void onListingItemClick(Product product);
    }
}
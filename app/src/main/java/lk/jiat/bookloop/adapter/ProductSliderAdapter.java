package lk.jiat.bookloop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

import lk.jiat.bookloop.R;

/**
 * ProductSliderAdapter
 * ─────────────────────────────────────────────────────────────────────────────
 * Displays a horizontal image slider for a product's images inside a ViewPager2.
 *
 * IMAGE LOADING STRATEGY:
 *   - Admin uploads images → stored in Firebase Storage: product_images/{docId}/{file}
 *   - Download URLs saved in Firestore: products/{doc}/images: ["https://...", ...]
 *   - This adapter receives that List<String> of HTTPS URLs → loads with Glide directly
 *   - NO FirebaseStorage SDK needed here — Glide handles any https:// URL natively
 *
 * WHY fitCenter (not centerCrop):
 *   fitCenter shows the WHOLE book cover image without cutting any part off.
 *   centerCrop fills the frame but crops the edges — you lose part of the cover art.
 */
public class ProductSliderAdapter extends RecyclerView.Adapter<ProductSliderAdapter.ProductSliderViewHolder> {

    private final List<String> imageUrls;

    public ProductSliderAdapter(List<String> imageUrls) {
        // Guard against null — empty list means getItemCount() returns 0, no crash
        this.imageUrls = (imageUrls != null) ? imageUrls : new ArrayList<>();
    }

    @NonNull
    @Override
    public ProductSliderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.product_slider_item, parent, false);
        return new ProductSliderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductSliderViewHolder holder, int position) {
        String url = imageUrls.get(position);

        if (url == null || url.isEmpty()) {
            // No URL — show plain background, skip Glide
            holder.imageView.setImageDrawable(null);
            return;
        }

        // fitCenter: scales image to fit entirely within the ImageView bounds.
        // The full book cover is visible — nothing is cropped.
        // DiskCacheStrategy.ALL: caches both original + transformed for fast reloads.
        Glide.with(holder.imageView.getContext())
                .load(url)
                .fitCenter()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    public static class ProductSliderViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ProductSliderViewHolder(@NonNull View itemView) {
            super(itemView);
            this.imageView = itemView.findViewById(R.id.product_slider_item_image);
        }
    }
}
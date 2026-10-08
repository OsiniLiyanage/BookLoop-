package lk.jiat.bookloop.adapter;

import android.annotation.SuppressLint;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
 *
 * Displays a horizontal image slider for a product's images inside a ViewPager2.
 * Admin uploads images -> Firebase Storage; download URLs are saved in Firestore
 * (products/{doc}/images). This adapter loads those HTTPS URLs with Glide.
 *
 * GESTURE: double-tap on an image calls OnImageDoubleTapListener.
 *   ProductDetailsFragment uses this to add the book to the wishlist.
 *   The home banner slider passes no listener, so it is unaffected.
 *
 * WHY fitCenter: shows the WHOLE cover without cropping.
 */
public class ProductSliderAdapter extends RecyclerView.Adapter<ProductSliderAdapter.ProductSliderViewHolder> {

    // Callback fired when the user double-taps an image
    public interface OnImageDoubleTapListener {
        void onImageDoubleTap();
    }

    private final List<String> imageUrls;
    private final OnImageDoubleTapListener doubleTapListener; // may be null

    // Original constructor (used by HomeFragment banners) - no double-tap
    public ProductSliderAdapter(List<String> imageUrls) {
        this(imageUrls, null);
    }

    // New constructor (used by ProductDetailsFragment) - with double-tap
    public ProductSliderAdapter(List<String> imageUrls, OnImageDoubleTapListener listener) {
        this.imageUrls = (imageUrls != null) ? imageUrls : new ArrayList<>();
        this.doubleTapListener = listener;
    }

    @NonNull
    @Override
    public ProductSliderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.product_slider_item, parent, false);
        return new ProductSliderViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull ProductSliderViewHolder holder, int position) {
        String url = imageUrls.get(position);

        // GestureDetector turns raw touch events into gestures such as double-tap.
        if (doubleTapListener != null) {
            GestureDetector detector = new GestureDetector(holder.imageView.getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent e) {
                            return true; // must return true so we keep receiving this touch
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            doubleTapListener.onImageDoubleTap();
                            return true;
                        }
                    });

            // Feed every touch to the detector. Swiping still works: when the user
            // drags, the ViewPager2 takes over the touch and this view gets CANCEL.
            holder.imageView.setOnTouchListener((v, event) -> {
                detector.onTouchEvent(event);
                return true;
            });
        }

        if (url == null || url.isEmpty()) {
            holder.imageView.setImageDrawable(null);
            return;
        }

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
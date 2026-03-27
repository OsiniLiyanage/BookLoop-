package lk.jiat.bookloop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;

import java.util.List;

import lk.jiat.bookloop.R;

// ProductSliderAdapter — used in ProductDetailsFragment for the book image slider (ViewPager2).
//
// WHY IMAGES WERE BLANK BEFORE:
//   The images list from Firestore contains Storage paths like "books/cover.jpg".
//   Glide.load("books/cover.jpg") does nothing — it's not a real URL.
//   Glide needs an https:// URL.
//
// THE FIX:
//   For each path, call FirebaseStorage.getReference(path).getDownloadUrl()
//   to get the real https:// download URL, then pass that to Glide.
//
// HOW TO UPLOAD BOOK IMAGES:
//   1. Firebase Console → Storage → create folder "books"
//   2. Upload image, e.g. "harry_potter.jpg" → full Storage path = "books/harry_potter.jpg"
//   3. In Firestore product document, set images array to: ["books/harry_potter.jpg"]
//   4. Image loads automatically — no code change needed.
public class ProductSliderAdapter extends RecyclerView.Adapter<ProductSliderAdapter.ProductSliderViewHolder> {

    private final List<String> imagePaths;

    public ProductSliderAdapter(List<String> imagePaths) {
        this.imagePaths = imagePaths;
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
        String path = imagePaths.get(position);

        if (path == null || path.isEmpty()) {
            holder.imageView.setBackgroundColor(
                    holder.imageView.getContext().getColor(R.color.md_theme_surfaceVariant));
            return;
        }

        // Show placeholder while loading
        holder.imageView.setBackgroundColor(
                holder.imageView.getContext().getColor(R.color.md_theme_surfaceVariant));

        // Resolve Firebase Storage path → real https URL → Glide loads it
        FirebaseStorage.getInstance()
                .getReference(path)
                .getDownloadUrl()
                .addOnSuccessListener(uri ->
                        Glide.with(holder.imageView.getContext())
                                .load(uri)
                                .centerCrop()
                                .into(holder.imageView))
                .addOnFailureListener(e -> {
                    // No image uploaded for this product yet — placeholder stays
                });
    }

    @Override
    public int getItemCount() {
        return imagePaths != null ? imagePaths.size() : 0;
    }

    public static class ProductSliderViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ProductSliderViewHolder(@NonNull View itemView) {
            super(itemView);
            this.imageView = itemView.findViewById(R.id.product_slider_item_image);
        }
    }
}
package lk.jiat.bookloop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import lk.jiat.bookloop.R;

public class ProductSliderAdapter extends RecyclerView.Adapter<ProductSliderAdapter.ProductSliderViewHolder> {

    private List<String> images;

    public ProductSliderAdapter(List<String> images) {
        this.images = images;
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
        Glide.with(holder.itemView.getContext())
                .load(images.get(position))
                .centerCrop()
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return images.size();
    }


    public static class ProductSliderViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ProductSliderViewHolder(@NonNull View itemView) {
            super(itemView);
            this.imageView = itemView.findViewById(R.id.product_slider_item_image);
        }
    }
}

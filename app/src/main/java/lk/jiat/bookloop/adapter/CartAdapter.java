package lk.jiat.bookloop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.model.CartItem;
import lk.jiat.bookloop.model.Product;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    private List<CartItem> cartItems;
    private OnListingItemClickListener listener;

    public CartAdapter(List<CartItem> cartItems, OnListingItemClickListener listener) {
        this.cartItems = cartItems;
        this.listener = listener;
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

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("products").whereEqualTo("productId", cartItem.getProductId()).get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
            @Override
            public void onSuccess(QuerySnapshot qds) {
                if (!qds.isEmpty()) {
                    Product product = qds.getDocuments().get(0).toObject(Product.class);

                    holder.productTitle.setText(product.getTitle());
                    holder.productPrice.setText("LKR " + product.getPrice());
                    holder.productQuantity.setText(String.valueOf(cartItem.getQuantity()));

                    Glide.with(holder.itemView.getContext())
                            .load(product.getImages().get(0))
                            .centerCrop()
                            .into(holder.productImage);
                }
            }
        });


//        holder.itemView.setOnClickListener(v -> {
//
//            Animation animation = AnimationUtils.loadAnimation(v.getContext(), R.anim.click_animation);
//            v.startAnimation(animation);
//
//            if (listener != null) {
//                listener.onListingItemClick(product);
//            }
//        });
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView productTitle;
        TextView productPrice;
        TextView productQuantity;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.item_cart_image);
            productTitle = itemView.findViewById(R.id.item_cart_title);
            productPrice = itemView.findViewById(R.id.item_cart_price);
            productQuantity = itemView.findViewById(R.id.item_cart_quantity);
        }
    }

    public interface OnListingItemClickListener {
        void onListingItemClick(Product product);
    }
}

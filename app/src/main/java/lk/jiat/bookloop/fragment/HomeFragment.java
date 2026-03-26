package lk.jiat.bookloop.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.adapter.SectionAdapter;
import lk.jiat.bookloop.databinding.FragmentHomeBinding;
import lk.jiat.bookloop.model.Product;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadTopSellProduct();

    }

    private void loadTopSellProduct() {

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("products")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot qds) {
                        if (!qds.isEmpty()) {
                            List<Product> products = qds.toObjects(Product.class);


                            LinearLayoutManager layoutManager =
                                    new LinearLayoutManager(getContext(),LinearLayoutManager.HORIZONTAL, false);

                            binding.homeTopSellSection.itemSectionContainer.setLayoutManager(layoutManager);


                            SectionAdapter adapter = new SectionAdapter(products, product -> {

                            });

                            binding.homeTopSellSection.itemSectionTitle.setText("Top Selling Products");
                            binding.homeTopSellSection.itemSectionContainer.setAdapter(adapter);

                        }
                    }
                });

    }
}
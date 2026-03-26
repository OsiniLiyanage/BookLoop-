package lk.jiat.bookloop.model;

import com.google.firebase.firestore.Exclude;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CartItem {

    // documentId is excluded from Firestore — only used locally to identify which doc to update/delete
    @Getter(onMethod_ = {@Exclude})
    @Setter(onMethod_ = {@Exclude})
    private String documentId;

    private String productId;

    // quantity = number of copies the user wants to rent
    private int quantity;

    // rentalWeeks = how many weeks they want to rent — NEW field for book rental
    private int rentalWeeks;

    private List<Attribute> attributes;

    // Constructor used when adding to cart from ProductDetailsFragment
    public CartItem(String productId, int quantity, int rentalWeeks, List<Attribute> attributes) {
        this.productId = productId;
        this.quantity = quantity;
        this.rentalWeeks = rentalWeeks;
        this.attributes = attributes;
    }

    // Attribute stores selected chip values (e.g. Condition: "Good")
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Attribute {
        private String name;
        private String value;
    }
}
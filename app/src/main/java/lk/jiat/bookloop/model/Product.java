package lk.jiat.bookloop.model;

import com.google.firebase.Timestamp;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Product {
    private String productId;
    private String title;
    private String description;
    private double price;
    private String categoryId;
    private List<String> images;
    private int stockCount;
    private boolean status;
    private float rating;

    private String author;
    private List<Attribute> attributes;

    // createdAt: saved as Firestore Timestamp when admin creates a book.
    // Used by HomeFragment to sort New Arrivals — newest books first.
    // Firestore Timestamp maps directly to/from this field via toObjects().
    private Timestamp createdAt;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Attribute {
        private String name;
        private String type;
        private List<String> values;
    }
}
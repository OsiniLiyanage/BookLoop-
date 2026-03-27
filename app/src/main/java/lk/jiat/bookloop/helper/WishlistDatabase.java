package lk.jiat.bookloop.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

// WishlistDatabase — SQLite local cache for wishlist items and recently viewed books.
// Covers the "Data Storage – SQLite" assignment requirement.
// Wishlist is stored offline so it works without internet.
//
// IMPORTANT: This file MUST be named WishlistDatabase.java (capital D)
// because Java requires the filename to exactly match the public class name.
public class WishlistDatabase extends SQLiteOpenHelper {

    private static final String TAG        = "WishlistDB";
    private static final String DB_NAME    = "bookloop_wishlist.db";
    private static final int    DB_VERSION = 1;

    // ── Table and column names ────────────────────────────────────────────────
    private static final String TABLE_WISHLIST        = "wishlist";
    private static final String TABLE_RECENTLY_VIEWED = "recently_viewed";

    private static final String COL_ID         = "_id";
    private static final String COL_PRODUCT_ID = "product_id";
    private static final String COL_TITLE      = "title";
    private static final String COL_AUTHOR     = "author";
    private static final String COL_PRICE      = "price";
    private static final String COL_IMAGE_URL  = "image_url";
    private static final String COL_CATEGORY_ID = "category_id";
    private static final String COL_ADDED_AT   = "added_at";   // millis timestamp
    private static final String COL_VIEWED_AT  = "viewed_at";  // millis timestamp

    // ── CREATE TABLE statements ───────────────────────────────────────────────
    private static final String CREATE_WISHLIST =
            "CREATE TABLE " + TABLE_WISHLIST + " (" +
                    COL_ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_PRODUCT_ID  + " TEXT UNIQUE NOT NULL, " +
                    COL_TITLE       + " TEXT, " +
                    COL_AUTHOR      + " TEXT, " +
                    COL_PRICE       + " REAL, " +
                    COL_IMAGE_URL   + " TEXT, " +
                    COL_CATEGORY_ID + " TEXT, " +
                    COL_ADDED_AT    + " INTEGER" +
                    ");";

    private static final String CREATE_RECENTLY_VIEWED =
            "CREATE TABLE " + TABLE_RECENTLY_VIEWED + " (" +
                    COL_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_PRODUCT_ID + " TEXT UNIQUE NOT NULL, " +
                    COL_TITLE      + " TEXT, " +
                    COL_AUTHOR     + " TEXT, " +
                    COL_PRICE      + " REAL, " +
                    COL_IMAGE_URL  + " TEXT, " +
                    COL_VIEWED_AT  + " INTEGER" +
                    ");";

    // ── Constructor ───────────────────────────────────────────────────────────

    public WishlistDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_WISHLIST);
        db.execSQL(CREATE_RECENTLY_VIEWED);
        Log.i(TAG, "SQLite database created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WISHLIST);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECENTLY_VIEWED);
        onCreate(db);
    }

    // ── Wishlist operations ───────────────────────────────────────────────────

    // Add a book to the local wishlist cache
    public boolean addToWishlist(String productId, String title, String author,
                                 double price, String imageUrl, String categoryId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PRODUCT_ID,  productId);
        values.put(COL_TITLE,       title);
        values.put(COL_AUTHOR,      author);
        values.put(COL_PRICE,       price);
        values.put(COL_IMAGE_URL,   imageUrl);
        values.put(COL_CATEGORY_ID, categoryId);
        values.put(COL_ADDED_AT,    System.currentTimeMillis());

        long result = db.insertWithOnConflict(
                TABLE_WISHLIST, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        Log.i(TAG, "Added to wishlist locally: " + productId);
        return result != -1;
    }

    // Remove a book from the local wishlist cache
    public boolean removeFromWishlist(String productId) {
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(TABLE_WISHLIST,
                COL_PRODUCT_ID + "=?", new String[]{productId});
        Log.i(TAG, "Removed from wishlist locally: " + productId);
        return rows > 0;
    }

    // Check whether a product is already in the local wishlist
    public boolean isInWishlist(String productId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WISHLIST,
                new String[]{COL_PRODUCT_ID},
                COL_PRODUCT_ID + "=?", new String[]{productId},
                null, null, null);
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    // Get all wishlist items, newest first
    public List<WishlistItem> getAllWishlistItems() {
        List<WishlistItem> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WISHLIST,
                null, null, null, null, null,
                COL_ADDED_AT + " DESC");

        while (cursor.moveToNext()) {
            items.add(new WishlistItem(
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHOR)),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRICE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY_ID))
            ));
        }
        cursor.close();
        Log.i(TAG, "Loaded " + items.size() + " wishlist items from SQLite");
        return items;
    }

    // Count how many items are in the wishlist
    public int getWishlistCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_WISHLIST, null);
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    // ── Recently viewed operations ────────────────────────────────────────────

    // Save a recently viewed book — keeps only the 20 most recent
    public void saveRecentlyViewed(String productId, String title, String author,
                                   double price, String imageUrl) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PRODUCT_ID, productId);
        values.put(COL_TITLE,      title);
        values.put(COL_AUTHOR,     author);
        values.put(COL_PRICE,      price);
        values.put(COL_IMAGE_URL,  imageUrl);
        values.put(COL_VIEWED_AT,  System.currentTimeMillis());

        db.insertWithOnConflict(
                TABLE_RECENTLY_VIEWED, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        // Prune: keep only the 20 most recent
        db.execSQL("DELETE FROM " + TABLE_RECENTLY_VIEWED +
                " WHERE " + COL_ID + " NOT IN (" +
                "SELECT " + COL_ID + " FROM " + TABLE_RECENTLY_VIEWED +
                " ORDER BY " + COL_VIEWED_AT + " DESC LIMIT 20)");
    }

    // Get recently viewed books (pass limit, e.g. 10)
    public List<WishlistItem> getRecentlyViewed(int limit) {
        List<WishlistItem> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_RECENTLY_VIEWED,
                null, null, null, null, null,
                COL_VIEWED_AT + " DESC", String.valueOf(limit));

        while (cursor.moveToNext()) {
            items.add(new WishlistItem(
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHOR)),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRICE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)),
                    null
            ));
        }
        cursor.close();
        return items;
    }

    // ── Inner model class ─────────────────────────────────────────────────────

    // Lightweight local model — no Firestore dependency, used for SQLite cache
    public static class WishlistItem {
        public String productId;
        public String title;
        public String author;
        public double price;
        public String imageUrl;
        public String categoryId;

        public WishlistItem(String productId, String title, String author,
                            double price, String imageUrl, String categoryId) {
            this.productId  = productId;
            this.title      = title;
            this.author     = author;
            this.price      = price;
            this.imageUrl   = imageUrl;
            this.categoryId = categoryId;
        }
    }
}
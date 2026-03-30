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
//
// BUG FIX (version 2): Added user_id column to BOTH tables.
// Before this fix, all users on the same device shared one wishlist because
// SQLite is per-device, not per-account. Now every query filters by userId
// so each Firebase account has completely separate wishlist and recently viewed data.
public class WishlistDatabase extends SQLiteOpenHelper {

    private static final String TAG        = "WishlistDB";
    private static final String DB_NAME    = "bookloop_wishlist.db";
    // VERSION 2 — added user_id column. onUpgrade() drops and recreates both tables.
    // Existing wishlist data is cleared on upgrade (users need to re-add items).
    private static final int    DB_VERSION = 2;

    // ── Table and column names ────────────────────────────────────────────────
    private static final String TABLE_WISHLIST        = "wishlist";
    private static final String TABLE_RECENTLY_VIEWED = "recently_viewed";

    private static final String COL_ID          = "_id";
    private static final String COL_USER_ID     = "user_id";       // NEW — separates data per user
    private static final String COL_PRODUCT_ID  = "product_id";
    private static final String COL_TITLE       = "title";
    private static final String COL_AUTHOR      = "author";
    private static final String COL_PRICE       = "price";
    private static final String COL_IMAGE_URL   = "image_url";
    private static final String COL_CATEGORY_ID = "category_id";
    private static final String COL_ADDED_AT    = "added_at";
    private static final String COL_VIEWED_AT   = "viewed_at";

    // ── CREATE TABLE — now includes user_id, and UNIQUE is (user_id + product_id) ──
    // This means User A and User B can both save the same productId without conflict.
    private static final String CREATE_WISHLIST =
            "CREATE TABLE " + TABLE_WISHLIST + " (" +
                    COL_ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_USER_ID     + " TEXT NOT NULL, " +
                    COL_PRODUCT_ID  + " TEXT NOT NULL, " +
                    COL_TITLE       + " TEXT, " +
                    COL_AUTHOR      + " TEXT, " +
                    COL_PRICE       + " REAL, " +
                    COL_IMAGE_URL   + " TEXT, " +
                    COL_CATEGORY_ID + " TEXT, " +
                    COL_ADDED_AT    + " INTEGER, " +
                    "UNIQUE(" + COL_USER_ID + ", " + COL_PRODUCT_ID + ")" +
                    ");";

    private static final String CREATE_RECENTLY_VIEWED =
            "CREATE TABLE " + TABLE_RECENTLY_VIEWED + " (" +
                    COL_ID         + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_USER_ID    + " TEXT NOT NULL, " +
                    COL_PRODUCT_ID + " TEXT NOT NULL, " +
                    COL_TITLE      + " TEXT, " +
                    COL_AUTHOR     + " TEXT, " +
                    COL_PRICE      + " REAL, " +
                    COL_IMAGE_URL  + " TEXT, " +
                    COL_VIEWED_AT  + " INTEGER, " +
                    "UNIQUE(" + COL_USER_ID + ", " + COL_PRODUCT_ID + ")" +
                    ");";

    public WishlistDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_WISHLIST);
        db.execSQL(CREATE_RECENTLY_VIEWED);
        Log.i(TAG, "SQLite database v2 created (with user_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop old tables without user_id and recreate with new schema.
        // Users will need to re-add their wishlist items after this upgrade.
        Log.i(TAG, "Upgrading SQLite from v" + oldVersion + " to v" + newVersion + " — clearing old data");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WISHLIST);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECENTLY_VIEWED);
        onCreate(db);
    }

    // ── Wishlist operations — ALL now require userId ──────────────────────────

    // Add a book to THIS user's wishlist
    public boolean addToWishlist(String userId, String productId, String title, String author,
                                 double price, String imageUrl, String categoryId) {
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "addToWishlist called with null/empty userId — skipping");
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_USER_ID,     userId);
        values.put(COL_PRODUCT_ID,  productId);
        values.put(COL_TITLE,       title);
        values.put(COL_AUTHOR,      author);
        values.put(COL_PRICE,       price);
        values.put(COL_IMAGE_URL,   imageUrl);
        values.put(COL_CATEGORY_ID, categoryId);
        values.put(COL_ADDED_AT,    System.currentTimeMillis());

        // CONFLICT_REPLACE handles the case where this user already saved this book
        long result = db.insertWithOnConflict(
                TABLE_WISHLIST, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        Log.i(TAG, "Added to wishlist [user=" + userId + "]: " + productId);
        return result != -1;
    }

    // Remove a book from THIS user's wishlist only — other users not affected
    public boolean removeFromWishlist(String userId, String productId) {
        if (userId == null || userId.isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(TABLE_WISHLIST,
                COL_USER_ID + "=? AND " + COL_PRODUCT_ID + "=?",
                new String[]{userId, productId});
        Log.i(TAG, "Removed from wishlist [user=" + userId + "]: " + productId);
        return rows > 0;
    }

    // Check if THIS user has saved a specific book
    public boolean isInWishlist(String userId, String productId) {
        if (userId == null || userId.isEmpty()) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WISHLIST,
                new String[]{COL_PRODUCT_ID},
                COL_USER_ID + "=? AND " + COL_PRODUCT_ID + "=?",
                new String[]{userId, productId},
                null, null, null);
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    // Get all wishlist items for THIS user only, newest first
    public List<WishlistItem> getAllWishlistItems(String userId) {
        List<WishlistItem> items = new ArrayList<>();
        if (userId == null || userId.isEmpty()) return items;

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_WISHLIST,
                null,
                COL_USER_ID + "=?", new String[]{userId},
                null, null,
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
        Log.i(TAG, "Loaded " + items.size() + " wishlist items for user=" + userId);
        return items;
    }

    // Count wishlist items for THIS user
    public int getWishlistCount(String userId) {
        if (userId == null || userId.isEmpty()) return 0;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_WISHLIST + " WHERE " + COL_USER_ID + "=?",
                new String[]{userId});
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    // ── Recently viewed operations — ALL now require userId ───────────────────

    // Save a recently viewed book for THIS user — keeps only the 20 most recent per user
    public void saveRecentlyViewed(String userId, String productId, String title,
                                   String author, double price, String imageUrl) {
        if (userId == null || userId.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_USER_ID,    userId);
        values.put(COL_PRODUCT_ID, productId);
        values.put(COL_TITLE,      title);
        values.put(COL_AUTHOR,     author);
        values.put(COL_PRICE,      price);
        values.put(COL_IMAGE_URL,  imageUrl);
        values.put(COL_VIEWED_AT,  System.currentTimeMillis());

        db.insertWithOnConflict(
                TABLE_RECENTLY_VIEWED, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        // Prune: keep only the 20 most recent for THIS user
        db.execSQL("DELETE FROM " + TABLE_RECENTLY_VIEWED +
                        " WHERE " + COL_USER_ID + "=? AND " + COL_ID + " NOT IN (" +
                        "SELECT " + COL_ID + " FROM " + TABLE_RECENTLY_VIEWED +
                        " WHERE " + COL_USER_ID + "=?" +
                        " ORDER BY " + COL_VIEWED_AT + " DESC LIMIT 20)",
                new String[]{userId, userId});
    }

    // Get recently viewed books for THIS user
    public List<WishlistItem> getRecentlyViewed(String userId, int limit) {
        List<WishlistItem> items = new ArrayList<>();
        if (userId == null || userId.isEmpty()) return items;

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_RECENTLY_VIEWED,
                null,
                COL_USER_ID + "=?", new String[]{userId},
                null, null,
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
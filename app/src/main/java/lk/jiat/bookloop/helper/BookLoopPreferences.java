package lk.jiat.bookloop.helper;

import android.content.Context;
import android.content.SharedPreferences;

// NEW — BookLoopPreferences: Centralises all SharedPreferences access in BookLoop.
//       This satisfies the "Data Storage – Shared Preferences" assignment requirement.
//       Stores: last delivery details, notification preferences, theme, recently viewed books.
public class BookLoopPreferences {

    private static final String PREFS_NAME = "bookloop_prefs";

    // Keys
    private static final String KEY_DELIVERY_NAME = "last_delivery_name";
    private static final String KEY_DELIVERY_CITY = "last_delivery_city";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_LAST_SEARCH = "last_search_query";
    private static final String KEY_RECENTLY_VIEWED = "recently_viewed_ids"; // comma-separated productIds
    private static final String KEY_DARK_MODE = "dark_mode_enabled";
    private static final String KEY_FONT_SIZE = "font_size_pref"; // "small", "medium", "large"
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_USER_CITY = "user_city"; // saved city for map default

    private final SharedPreferences prefs;

    public BookLoopPreferences(Context context) {
        // Use application context to avoid memory leaks
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Delivery details (pre-fill checkout form) ────────────────────────────

    public void saveLastDeliveryDetails(String name, String city) {
        prefs.edit()
                .putString(KEY_DELIVERY_NAME, name)
                .putString(KEY_DELIVERY_CITY, city)
                .apply();
    }

    public String getLastDeliveryName() {
        return prefs.getString(KEY_DELIVERY_NAME, null);
    }

    public String getLastDeliveryCity() {
        return prefs.getString(KEY_DELIVERY_CITY, null);
    }

    // ── Notification preference ──────────────────────────────────────────────

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true); // default ON
    }

    // ── Search history ───────────────────────────────────────────────────────

    public void saveLastSearch(String query) {
        prefs.edit().putString(KEY_LAST_SEARCH, query).apply();
    }

    public String getLastSearch() {
        return prefs.getString(KEY_LAST_SEARCH, "");
    }

    // ── Recently viewed books (comma-separated IDs, max 10) ──────────────────

    public void addRecentlyViewed(String productId) {
        String existing = prefs.getString(KEY_RECENTLY_VIEWED, "");
        String[] ids = existing.isEmpty() ? new String[0] : existing.split(",");

        StringBuilder sb = new StringBuilder(productId);
        int count = 1;
        for (String id : ids) {
            if (!id.equals(productId) && count < 10) {
                sb.append(",").append(id);
                count++;
            }
        }
        prefs.edit().putString(KEY_RECENTLY_VIEWED, sb.toString()).apply();
    }

    public String[] getRecentlyViewedIds() {
        String val = prefs.getString(KEY_RECENTLY_VIEWED, "");
        return val.isEmpty() ? new String[0] : val.split(",");
    }

    // ── Theme preference ─────────────────────────────────────────────────────

    public void setDarkMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    // ── Font size ────────────────────────────────────────────────────────────

    public void setFontSize(String size) {
        prefs.edit().putString(KEY_FONT_SIZE, size).apply();
    }

    public String getFontSize() {
        return prefs.getString(KEY_FONT_SIZE, "medium");
    }

    // ── First launch ─────────────────────────────────────────────────────────

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }

    // ── User city for map ────────────────────────────────────────────────────

    public void saveUserCity(String city) {
        prefs.edit().putString(KEY_USER_CITY, city).apply();
    }

    public String getUserCity() {
        return prefs.getString(KEY_USER_CITY, "Colombo");
    }

    // ── Clear all ────────────────────────────────────────────────────────────

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
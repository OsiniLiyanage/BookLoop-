package lk.jiat.bookloop.helper;

import android.content.Context;
import android.content.SharedPreferences;

// BookLoopPreferences — centralised SharedPreferences helper.
//
// All keys are declared as constants here so they're never mistyped.
// New keys added in this version:
//   ✅ app_theme  ("light" / "dark" / "system")  — for the theme toggle in Settings
public class BookLoopPreferences {

    private static final String PREF_FILE = "bookloop_prefs";

    // ── Keys ──────────────────────────────────────────────────────────────────
    private static final String KEY_NOTIFICATIONS     = "notifications_enabled";
    private static final String KEY_FONT_SIZE         = "font_size_pref";
    private static final String KEY_LAST_SEARCH       = "last_search_query";
    private static final String KEY_RECENTLY_VIEWED   = "recently_viewed_ids";
    private static final String KEY_DELIVERY_NAME     = "last_delivery_name";
    private static final String KEY_DELIVERY_CITY     = "last_delivery_city";
    private static final String KEY_APP_THEME         = "app_theme";           // NEW

    private final SharedPreferences prefs;

    public BookLoopPreferences(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    // ── Font Size (kept for backwards compat, no longer shown in Settings UI) ─

    public String getFontSize() {
        return prefs.getString(KEY_FONT_SIZE, "medium");
    }

    public void setFontSize(String size) {
        prefs.edit().putString(KEY_FONT_SIZE, size).apply();
    }

    // ── App Theme — "light" / "dark" / "system" ───────────────────────────────

    public String getAppTheme() {
        return prefs.getString(KEY_APP_THEME, "system");
    }

    public void setAppTheme(String theme) {
        prefs.edit().putString(KEY_APP_THEME, theme).apply();
    }

    // ── Search history ────────────────────────────────────────────────────────

    public String getLastSearch() {
        return prefs.getString(KEY_LAST_SEARCH, "");
    }

    public void saveLastSearch(String query) {
        prefs.edit().putString(KEY_LAST_SEARCH, query).apply();
    }

    // ── Recently viewed IDs (comma-separated product IDs) ────────────────────

    public String getRecentlyViewedIds() {
        return prefs.getString(KEY_RECENTLY_VIEWED, "");
    }

    public void saveRecentlyViewedIds(String ids) {
        prefs.edit().putString(KEY_RECENTLY_VIEWED, ids).apply();
    }

    // ── Delivery details ─────────────────────────────────────────────────────

    public String getLastDeliveryName() {
        return prefs.getString(KEY_DELIVERY_NAME, "");
    }

    public String getLastDeliveryCity() {
        return prefs.getString(KEY_DELIVERY_CITY, "");
    }

    public void saveLastDeliveryDetails(String name, String city) {
        prefs.edit()
                .putString(KEY_DELIVERY_NAME, name)
                .putString(KEY_DELIVERY_CITY, city)
                .apply();
    }
}
package lk.jiat.bookloop.helper;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeApplier {
    public static void apply(Context context) {
        BookLoopPreferences prefs = new BookLoopPreferences(context);
        String theme = prefs.getAppTheme();

        switch (theme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}

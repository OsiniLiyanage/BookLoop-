package lk.jiat.bookloop.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

// NEW — ConnectivityReceiver: listens for network state changes.
//       When connectivity is restored, logs the event so the app can sync data.
//       This satisfies the "Broadcast Receivers" assignment requirement.
//       Register this in AndroidManifest.xml AND dynamically in MainActivity.
public class ConnectivityReceiver extends BroadcastReceiver {

    private static final String TAG = "ConnectivityReceiver";

    // NEW — Listener interface so Activities/Fragments can react to changes
    public interface ConnectivityListener {
        void onConnectivityChanged(boolean isConnected);
    }

    // NEW — Static listener — set from MainActivity so fragments can react
    private static ConnectivityListener listener;

    public static void setConnectivityListener(ConnectivityListener l) {
        listener = l;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        // Check if this is a connectivity change broadcast
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            boolean isConnected = isNetworkAvailable(context);

            if (isConnected) {
                Log.i(TAG, "Network connected — triggering data sync");
            } else {
                Log.w(TAG, "Network disconnected — app running in offline mode");
            }

            // NEW — Notify listener (e.g., MainActivity can show/hide offline banner)
            if (listener != null) {
                listener.onConnectivityChanged(isConnected);
            }
        }
    }

    // Helper: check current network state
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}
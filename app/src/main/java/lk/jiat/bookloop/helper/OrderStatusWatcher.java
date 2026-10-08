package lk.jiat.bookloop.helper;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

// OrderStatusWatcher — listens to the signed-in user's orders in REAL TIME and shows a
// notification when the admin changes an order's status (e.g. PROCESSING -> DELIVERED).
//
// WHY A SEPARATE CLASS (not inside OrdersFragment):
//   OrdersFragment's listener only lives while the Orders screen is open.
//   This watcher is started by MainActivity, so it keeps working on ANY screen and
//   while the app is in the background (as long as Android keeps the app process alive).
//
// HOW IT DECIDES TO NOTIFY:
//   - It remembers the last known status of every order (lastStatus map).
//   - ADDED  documents (first load, or a new order) are only remembered - no notification.
//   - MODIFIED documents whose status text really changed -> notification.
public class OrderStatusWatcher {

    private static final String TAG = "OrderStatusWatcher";

    private final Context appContext;
    private ListenerRegistration registration;

    // orderDocumentId -> last known status
    private final Map<String, String> lastStatus = new HashMap<>();

    public OrderStatusWatcher(Context context) {
        // Application context: safe to keep, cannot leak an Activity
        this.appContext = context.getApplicationContext();
    }

    // Start listening for the currently signed-in user. Safe to call more than once.
    public void start() {
        stop();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No signed-in user - watcher not started");
            return;
        }

        lastStatus.clear();

        registration = FirebaseFirestore.getInstance()
                .collection("orders")
                .whereEqualTo("userId", user.getUid())
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Order watcher failed: " + error.getMessage());
                        return;
                    }
                    if (snapshots == null) return;

                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        DocumentSnapshot doc = change.getDocument();
                        String docId = doc.getId();
                        String newStatus = doc.getString("status");

                        switch (change.getType()) {
                            case ADDED:
                                // First load or a brand-new order: just remember it
                                lastStatus.put(docId, newStatus);
                                break;

                            case MODIFIED:
                                String oldStatus = lastStatus.get(docId);
                                lastStatus.put(docId, newStatus);

                                if (oldStatus != null && newStatus != null
                                        && !newStatus.equals(oldStatus)) {
                                    Log.i(TAG, "Status changed: " + oldStatus + " -> " + newStatus
                                            + " (order doc " + docId + ")");
                                    NotificationHelper.showOrderStatusUpdate(
                                            appContext, docId, doc.getString("orderId"), newStatus);
                                }
                                break;

                            case REMOVED:
                                lastStatus.remove(docId);
                                break;
                        }
                    }
                });

        Log.d(TAG, "Order status watcher started");
    }

    // Stop listening (call on logout and when the activity is destroyed)
    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
            Log.d(TAG, "Order status watcher stopped");
        }
        lastStatus.clear();
    }
}
package lk.jiat.bookloop.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import lk.jiat.bookloop.helper.NotificationHelper;
import lk.jiat.bookloop.model.Order;

import java.util.List;
import java.util.concurrent.TimeUnit;

// NEW — RentalReminderWorker: runs in the background (WorkManager) to check for
//       upcoming rental due dates and fire reminder notifications.
//       This satisfies the "Multitasking / Background Tasks" assignment requirement.
//       Schedule this with WorkManager in MainActivity.onCreate().
public class RentalReminderWorker extends Worker {

    private static final String TAG = "RentalReminderWorker";

    public RentalReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Background rental reminder check started");

        // NEW — This runs on a background thread automatically (WorkManager handles threading)
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "User not logged in — skipping reminder check");
            return Result.success();
        }

        String uid = auth.getCurrentUser().getUid();

        try {
            // NEW — Synchronously query Firestore for PAID/DELIVERED orders
            QuerySnapshot snapshot = Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection("orders")
                            .whereEqualTo("userId", uid)
                            .whereIn("status", java.util.Arrays.asList("PAID", "DELIVERED"))
                            .get(),
                    10, TimeUnit.SECONDS);

            List<Order> orders = snapshot.toObjects(Order.class);

            for (Order order : orders) {
                if (order.getOrderItems() == null) continue;

                for (Order.OrderItem item : order.getOrderItems()) {
                    // NEW — Calculate due date: orderDate + rentalWeeks
                    if (order.getOrderDate() == null) continue;

                    int weeks = item.getRentalWeeks() > 0 ? item.getRentalWeeks() : 1;
                    long orderMillis = order.getOrderDate().toDate().getTime();
                    long dueMillis = orderMillis + (weeks * 7L * 24 * 60 * 60 * 1000);
                    long nowMillis = System.currentTimeMillis();
                    long daysUntilDue = TimeUnit.MILLISECONDS.toDays(dueMillis - nowMillis);

                    // NEW — Fire reminder if due in 1 day or overdue
                    if (daysUntilDue <= 1 && daysUntilDue >= -2) {
                        String bookTitle = item.getProductTitle() != null ? item.getProductTitle() : "Your book";
                        String dueDate = new java.text.SimpleDateFormat("dd MMM yyyy",
                                java.util.Locale.getDefault()).format(new java.util.Date(dueMillis));

                        NotificationHelper.showReturnReminder(getApplicationContext(), bookTitle, dueDate);
                        Log.i(TAG, "Reminder sent for: " + bookTitle);
                    }
                }
            }

            Log.i(TAG, "Rental reminder check complete. Processed " + orders.size() + " orders.");
            return Result.success();

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.e(TAG, "Worker failed: " + e.getMessage());
            return Result.retry(); // WorkManager will retry later
        }
    }
}
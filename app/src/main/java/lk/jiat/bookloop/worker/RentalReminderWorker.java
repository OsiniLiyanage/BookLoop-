package lk.jiat.bookloop.worker;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import lk.jiat.bookloop.helper.NotificationHelper;
import lk.jiat.bookloop.model.Order;

import java.util.List;
import java.util.concurrent.TimeUnit;

// RentalReminderWorker: runs in the BACKGROUND (WorkManager) to check for
// upcoming rental due dates and fire return-reminder notifications.
// This satisfies the "Multitasking / Background Tasks" requirement.
//
// TWO WAYS IT RUNS:
//   1) REAL  - PeriodicWorkRequest every 12 hours (scheduled in MainActivity).
//              Only reminds when a book is due within 1 day (or up to 2 days overdue).
//   2) DEMO  - OneTimeWorkRequest 10 seconds after app start, with KEY_DEMO = true.
//              Skips the date check so a reminder can be shown during the viva.
public class RentalReminderWorker extends Worker {

    private static final String TAG        = "RentalReminderWorker";
    private static final String THREAD_TAG = "ThreadDemo";   // filter this in Logcat

    // Input-data key: true = demo mode (skip the due-date check)
    public static final String KEY_DEMO = "demo_mode";

    // In demo mode never send more than this many notifications at once
    private static final int MAX_DEMO_NOTIFICATIONS = 3;

    public RentalReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean demo = getInputData().getBoolean(KEY_DEMO, false);

        // THREAD PROOF: WorkManager runs doWork() on its own background thread, not "main".
        boolean onMain = Looper.myLooper() == Looper.getMainLooper();
        Log.d(THREAD_TAG, "RentalReminderWorker running on thread: "
                + Thread.currentThread().getName() + " | isMainThread=" + onMain
                + " | demo=" + demo);
        Log.i(TAG, "Background rental reminder check started (demo=" + demo + ")");

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Log.w(TAG, "User not logged in — skipping reminder check");
            return Result.success();
        }

        String uid = auth.getCurrentUser().getUid();

        try {
            // Synchronously query Firestore (allowed here because we are on a background thread).
            // CONFIRMED + PROCESSING added: these are books that are still with the customer
            // or on the way. RETURNED is NOT included (book already given back).
            QuerySnapshot snapshot = Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection("orders")
                            .whereEqualTo("userId", uid)
                            .whereIn("status", Arrays.asList(
                                    "PAID", "CONFIRMED", "PROCESSING", "DELIVERED"))
                            .get(),
                    10, TimeUnit.SECONDS);

            List<Order> orders = snapshot.toObjects(Order.class);
            int sent = 0;

            for (Order order : orders) {
                if (order.getOrderItems() == null) continue;
                if (order.getOrderDate() == null) continue;

                for (Order.OrderItem item : order.getOrderItems()) {
                    // Due date = orderDate + rentalWeeks
                    int weeks = item.getRentalWeeks() > 0 ? item.getRentalWeeks() : 1;
                    long orderMillis = order.getOrderDate().toDate().getTime();
                    long dueMillis = orderMillis + (weeks * 7L * 24 * 60 * 60 * 1000);
                    long nowMillis = System.currentTimeMillis();
                    long daysUntilDue = TimeUnit.MILLISECONDS.toDays(dueMillis - nowMillis);

                    // REAL mode: only if due in 1 day or overdue by up to 2 days.
                    // DEMO mode: skip this check so we can show a reminder right now.
                    boolean dueSoon = daysUntilDue <= 1 && daysUntilDue >= -2;
                    if (!demo && !dueSoon) continue;

                    // In demo mode stop after a few so the screen is not flooded
                    if (demo && sent >= MAX_DEMO_NOTIFICATIONS) break;

                    String bookTitle = item.getProductTitle() != null
                            ? item.getProductTitle() : "Your book";
                    String dueDate = new java.text.SimpleDateFormat("dd MMM yyyy",
                            java.util.Locale.getDefault()).format(new java.util.Date(dueMillis));

                    // UNIQUE ID per order + book, so reminders do not overwrite each other
                    int notificationId = NotificationHelper.reminderIdFor(
                            order.getOrderId(), item.getProductId());

                    NotificationHelper.showReturnReminder(
                            getApplicationContext(), bookTitle, dueDate, notificationId);
                    sent++;
                    Log.i(TAG, "Reminder sent for: " + bookTitle + " (id=" + notificationId + ")");
                }
            }

            // DEMO ONLY: if the user has no eligible order yet, still show one sample
            // reminder so the notification itself can be demonstrated.
            if (demo && sent == 0) {
                String sampleDue = new java.text.SimpleDateFormat("dd MMM yyyy",
                        java.util.Locale.getDefault()).format(new java.util.Date());
                NotificationHelper.showReturnReminder(getApplicationContext(),
                        "Sample Book (demo)", sampleDue);
                Log.i(TAG, "Demo mode: no eligible orders, showed a sample reminder");
            }

            Log.i(TAG, "Rental reminder check complete. Orders: " + orders.size()
                    + ", reminders sent: " + sent);
            return Result.success();

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.e(TAG, "Worker failed: " + e.getMessage());
            return Result.retry(); // WorkManager will retry later
        }
    }
}
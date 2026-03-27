package lk.jiat.bookloop.helper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.activity.MainActivity;

// NotificationHelper — shows local notifications for BookLoop events.
// Covers the "Notifications" assignment requirement.
// Three channels: orders (rental confirmations), reminders (return due), new_books (new listings)
//
// IMPORTANT: This file MUST be named NotificationHelper.java (capital H)
// because Java requires the filename to exactly match the public class name.
public class NotificationHelper {

    // Notification channel IDs — used to group notifications by type
    public static final String CHANNEL_ORDERS    = "bookloop_orders";
    public static final String CHANNEL_REMINDERS = "bookloop_reminders";
    public static final String CHANNEL_NEW_BOOKS = "bookloop_new_books";

    // Unique IDs so Android can update or cancel individual notifications
    private static final int NOTIF_ORDER_CONFIRM  = 1001;
    private static final int NOTIF_RETURN_REMINDER = 1002;
    private static final int NOTIF_NEW_BOOK        = 1003;
    private static final int NOTIF_CART_REMINDER   = 1004;

    // Call this once from MainActivity.onCreate() to register all channels.
    // Android 8+ requires channels before you can show any notification.
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);

            NotificationChannel ordersChannel = new NotificationChannel(
                    CHANNEL_ORDERS, "Rental Orders", NotificationManager.IMPORTANCE_HIGH);
            ordersChannel.setDescription("Order confirmations and status updates");
            manager.createNotificationChannel(ordersChannel);

            NotificationChannel reminderChannel = new NotificationChannel(
                    CHANNEL_REMINDERS, "Return Reminders", NotificationManager.IMPORTANCE_HIGH);
            reminderChannel.setDescription("Reminders to return books before due date");
            manager.createNotificationChannel(reminderChannel);

            NotificationChannel newBooksChannel = new NotificationChannel(
                    CHANNEL_NEW_BOOKS, "New Books Available", NotificationManager.IMPORTANCE_DEFAULT);
            newBooksChannel.setDescription("Alerts when new books matching your interests are listed");
            manager.createNotificationChannel(newBooksChannel);
        }
    }

    // Show order confirmation after user places a rental order
    public static void showOrderConfirmation(Context context, String orderId, double total) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("openFragment", "orders");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ORDERS)
                .setSmallIcon(R.drawable.library_books_24px)
                .setContentTitle("Order Confirmed!")
                .setContentText(String.format("Your rental is confirmed. Total: LKR %.2f", total))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Your books are being prepared for delivery! " +
                                "Order ID: " + orderId + ". " +
                                "Expected delivery: 1-2 working days."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ORDER_CONFIRM, builder.build());
        } catch (SecurityException e) {
            // Permission not granted yet — silently skip, no crash
        }
    }

    // Show return reminder (called by background worker when book is due soon)
    public static void showReturnReminder(Context context, String bookTitle, String dueDate) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("openFragment", "orders");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.notifications_24px)
                .setContentTitle("Return Reminder")
                .setContentText("\"" + bookTitle + "\" is due back on " + dueDate)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_RETURN_REMINDER, builder.build());
        } catch (SecurityException e) {
            // Permission not granted — silently skip
        }
    }

    // Show new book available notification
    public static void showNewBookAlert(Context context, String bookTitle, String category) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("openFragment", "home");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_NEW_BOOKS)
                .setSmallIcon(R.drawable.bookmark_heart_24px)
                .setContentTitle("New Book Available!")
                .setContentText("\"" + bookTitle + "\" just added in " + category)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_NEW_BOOK, builder.build());
        } catch (SecurityException e) {
            // Permission not granted — silently skip
        }
    }

    // Cart reminder — call this when user has items in cart but hasn't checked out
    public static void showCartReminder(Context context, int itemCount) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("openFragment", "cart");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ORDERS)
                .setSmallIcon(R.drawable.local_mall_24px)
                .setContentTitle("Books in your cart!")
                .setContentText("You have " + itemCount + " book" + (itemCount == 1 ? "" : "s")
                        + " waiting. Complete your rental today!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_CART_REMINDER, builder.build());
        } catch (SecurityException e) {
            // Permission not granted — silently skip
        }
    }
}
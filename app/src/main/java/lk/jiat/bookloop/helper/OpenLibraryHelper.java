package lk.jiat.bookloop.helper;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// OpenLibraryHelper: makes HTTP GET requests to Open Library API to auto-fetch
// book metadata (title, author, cover) from an ISBN.
// This satisfies the "HTTP Network Connection" assignment requirement.
// Usage: OpenLibraryHelper.fetchBookByIsbn("9780007477548", callback)
public class OpenLibraryHelper {

    private static final String TAG = "OpenLibraryHelper";
    private static final String BASE_URL = "https://openlibrary.org/api/books";

    // Callback for async HTTP result
    public interface BookFetchCallback {
        void onSuccess(BookMetadata metadata);
        void onError(String error);
    }

    // Simple metadata model for API response
    public static class BookMetadata {
        public String title;
        public String author;
        public String coverId;      // Open Library cover ID for thumbnail
        public String publishYear;
        public String isbn;

        // FIXED: Now properly handles invalid cover IDs
        public String getCoverUrl() {
            if (coverId != null && !coverId.isEmpty() && !coverId.equals("0") && !coverId.equals("-1")) {
                return "https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg";
            }
            return null;
        }
    }

    // Thread pool for background HTTP work (keeps UI thread free)
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Fetch book metadata by ISBN from Open Library (free API, no key needed)
    public static void fetchBookByIsbn(String isbn, BookFetchCallback callback) {
        executor.execute(() -> {
            try {
                // Build request URL: ?bibkeys=ISBN:xxxx&format=json&jscmd=data
                String urlString = BASE_URL + "?bibkeys=ISBN:" + isbn +
                        "&format=json&jscmd=data";
                URL url = new URL(urlString);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000); // 10 second timeout
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Accept", "application/json");

                int responseCode = connection.getResponseCode();
                Log.i(TAG, "HTTP " + responseCode + " for ISBN: " + isbn);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read response body
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    connection.disconnect();

                    String jsonResponse = sb.toString();
                    Log.d(TAG, "API Response: " + jsonResponse);

                    // Parse JSON response
                    BookMetadata metadata = parseResponse(jsonResponse, isbn);

                    // Deliver result on main thread so callers can update UI directly
                    mainHandler.post(() -> {
                        if (metadata != null) {
                            callback.onSuccess(metadata);
                        } else {
                            callback.onError("Book not found for ISBN: " + isbn);
                        }
                    });

                } else {
                    connection.disconnect();
                    mainHandler.post(() -> callback.onError("HTTP error: " + responseCode));
                }

            } catch (Exception e) {
                Log.e(TAG, "HTTP request failed: " + e.getMessage());
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // Parse Open Library JSON response into BookMetadata
    private static BookMetadata parseResponse(String json, String isbn) {
        try {
            JSONObject root = new JSONObject(json);
            String key = "ISBN:" + isbn;

            if (!root.has(key)) return null;

            JSONObject bookData = root.getJSONObject(key);
            BookMetadata metadata = new BookMetadata();
            metadata.isbn = isbn;

            // Title
            if (bookData.has("title")) {
                metadata.title = bookData.getString("title");
            }

            // Author (first author only)
            if (bookData.has("authors")) {
                org.json.JSONArray authors = bookData.getJSONArray("authors");
                if (authors.length() > 0) {
                    metadata.author = authors.getJSONObject(0).optString("name", "");
                }
            }

            // FIXED: Cover ID handling
            // The API returns {"cover": {"small": -1, "medium": -1, "large": 123456}}
            // We need to check all three sizes and use the first valid one
            if (bookData.has("cover")) {
                JSONObject coverObj = bookData.getJSONObject("cover");

                // Try large first, then medium, then small
                int coverId = coverObj.optInt("large", -1);
                if (coverId <= 0) coverId = coverObj.optInt("medium", -1);
                if (coverId <= 0) coverId = coverObj.optInt("small", -1);

                // Only set if we found a valid cover ID
                if (coverId > 0) {
                    metadata.coverId = String.valueOf(coverId);
                    Log.d(TAG, "Found cover ID: " + metadata.coverId);
                } else {
                    metadata.coverId = null;
                    Log.d(TAG, "No valid cover found");
                }
            }

            // Publish year
            if (bookData.has("publish_date")) {
                metadata.publishYear = bookData.getString("publish_date");
            }

            Log.i(TAG, "Parsed book: " + metadata.title + " by " + metadata.author +
                    " | Cover: " + (metadata.coverId != null ? metadata.getCoverUrl() : "none"));
            return metadata;

        } catch (Exception e) {
            Log.e(TAG, "JSON parse error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
package lk.jiat.bookloop.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.databinding.FragmentHelpBinding;
import lk.jiat.bookloop.helper.OpenLibraryHelper;

// HelpFragment — Help & Support + ISBN Book Lookup Tool.
//
// ISBN LOOKUP — What is OpenLibraryHelper and how to use it:
//   OpenLibraryHelper makes a real HTTP GET request to the Open Library API
//   (https://openlibrary.org/api/books) to fetch book title, author, and cover
//   image from an ISBN number.
//
//   HOW TO USE IN THE APP:
//     1. Open the side navigation drawer → tap "Help"
//     2. Scroll down to the "Book ISBN Lookup" section
//     3. Type any ISBN-13 (e.g. 9780007477548 = Harry Potter)
//     4. Tap "Look Up" — the app makes a live HTTP call and shows the result
//
//   EXAMPLE ISBNs TO TEST:
//     9780007477548  → Harry Potter and the Philosopher's Stone
//     9780143127741  → The Alchemist by Paulo Coelho
//     9780062315007  → The Hunger Games by Suzanne Collins
//     9781501156700  → It Ends with Us by Colleen Hoover
public class HelpFragment extends Fragment {

    private FragmentHelpBinding binding;

    // ISBN lookup views (added dynamically from binding)
    private EditText isbnInput;
    private MaterialButton isbnLookupBtn;
    private ProgressBar isbnLoading;
    private LinearLayout isbnResultCard;
    private TextView isbnResultTitle;
    private TextView isbnResultAuthor;
    private TextView isbnResultYear;
    private TextView isbnResultStatus;
    private android.widget.ImageView isbnResultCover;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHelpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Existing contact buttons ──────────────────────────────────────────
        binding.helpBtnCall.setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:+94785050506"));
            startActivity(callIntent);
        });

        binding.helpBtnEmail.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:noviiozeen@gmail.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "BookLoop Support Request");
            startActivity(Intent.createChooser(emailIntent, "Send Email"));
        });

        // ── ISBN lookup — bind views ──────────────────────────────────────────
        isbnInput       = view.findViewById(R.id.help_isbn_input);
        isbnLookupBtn   = view.findViewById(R.id.help_isbn_lookup_btn);
        isbnLoading     = view.findViewById(R.id.help_isbn_loading);
        isbnResultCard  = view.findViewById(R.id.help_isbn_result_card);
        isbnResultCover = view.findViewById(R.id.help_isbn_result_cover);
        isbnResultTitle  = view.findViewById(R.id.help_isbn_result_title);
        isbnResultAuthor = view.findViewById(R.id.help_isbn_result_author);
        isbnResultYear   = view.findViewById(R.id.help_isbn_result_year);
        isbnResultStatus = view.findViewById(R.id.help_isbn_result_status);

        if (isbnLookupBtn != null) {
            isbnLookupBtn.setOnClickListener(v -> performIsbnLookup());
        }
    }

    // ── ISBN Lookup — HTTP Network Connection requirement ─────────────────────
    //
    // This is where OpenLibraryHelper is used.
    // It calls the Open Library REST API over HTTP and parses the JSON response.
    // The result (title, author, cover image) is shown in a card below the input.
    private void performIsbnLookup() {
        if (isbnInput == null) return;

        String isbn = isbnInput.getText().toString().trim()
                .replaceAll("[^0-9X]", ""); // remove dashes/spaces

        if (isbn.length() < 10) {
            Toast.makeText(getContext(),
                    "Please enter a valid ISBN (10 or 13 digits)", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading spinner, hide old result
        if (isbnLoading != null)    isbnLoading.setVisibility(View.VISIBLE);
        if (isbnResultCard != null) isbnResultCard.setVisibility(View.GONE);
        if (isbnResultStatus != null) isbnResultStatus.setText("Fetched book info!");
        isbnLookupBtn.setEnabled(false);

        // Call OpenLibraryHelper — makes real HTTP GET on background thread,
        // delivers result on main thread via Handler.post()
        OpenLibraryHelper.fetchBookByIsbn(isbn, new OpenLibraryHelper.BookFetchCallback() {

            @Override
            public void onSuccess(OpenLibraryHelper.BookMetadata metadata) {
                if (!isAdded()) return;

                if (isbnLoading != null)  isbnLoading.setVisibility(View.GONE);
                isbnLookupBtn.setEnabled(true);

                if (isbnResultCard != null)   isbnResultCard.setVisibility(View.VISIBLE);
                if (isbnResultTitle != null)  isbnResultTitle.setText(metadata.title != null ? "Title: " + metadata.title : "—");
                if (isbnResultAuthor != null) isbnResultAuthor.setText(metadata.author != null ? "Author: " + metadata.author : "Unknown author");
                if (isbnResultYear != null)   isbnResultYear.setText(metadata.publishYear != null ? "Published Year: " + metadata.publishYear : "");

                // Load cover image from Open Library CDN
                String coverUrl = metadata.getCoverUrl();
                if (isbnResultCover != null && coverUrl != null) {
                    Glide.with(HelpFragment.this)
                            .load(coverUrl)
                            .placeholder(R.drawable.library_books_24px)
                            .error(R.drawable.library_books_24px)
                            .into(isbnResultCover);
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;

                if (isbnLoading != null)    isbnLoading.setVisibility(View.GONE);
                if (isbnResultCard != null) isbnResultCard.setVisibility(View.VISIBLE);
                if (isbnResultTitle != null)  isbnResultTitle.setText("Book not found");
                if (isbnResultAuthor != null) isbnResultAuthor.setText("Try a different ISBN");
                if (isbnResultYear != null)   isbnResultYear.setText("");
                isbnLookupBtn.setEnabled(true);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
package lk.jiat.bookloop.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import lk.jiat.bookloop.databinding.FragmentHelpBinding;

/*
 * HelpFragment.java
 * ─────────────────
 * Help & Support screen.
 *
 * Features:
 *   - Call button: opens phone dialer with BookLoop support number
 *     (uses Intent.ACTION_DIAL — this is an IMPLICIT INTENT from your class notes!
 *      We don't specify which app handles it — Android picks the dialer)
 *   - Email button: opens email app to contact support
 *     (also an implicit intent — Android picks the mail app)
 *   - FAQ cards explaining key app features
 *
 * WHY Implicit Intents here:
 *   Your class notes say: "Implicit Intent — target component not specified.
 *   Android system picks which app handles the action based on intent filters."
 *   ACTION_DIAL and ACTION_SENDTO are classic examples of implicit intents.
 */
public class HelpFragment extends Fragment {

    private FragmentHelpBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHelpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Call button — Implicit Intent (ACTION_DIAL) ────────────────────────
        // This is exactly from your class notes: Implicit Intent for dialing a number.
        // We say "I want to dial this number" — Android finds the right app.
        // Unlike explicit intent, we don't say Intent(this, PhoneApp.class).
        binding.helpBtnCall.setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:+94771234567"));
            startActivity(callIntent);
        });

        // ── Email button — Implicit Intent (ACTION_SENDTO) ────────────────────
        // Another implicit intent — Android opens whatever email app is installed.
        binding.helpBtnEmail.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:support@bookloop.lk"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "BookLoop Support Request");
            startActivity(Intent.createChooser(emailIntent, "Send Email"));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
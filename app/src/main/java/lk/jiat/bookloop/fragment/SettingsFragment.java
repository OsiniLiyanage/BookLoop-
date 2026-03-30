package lk.jiat.bookloop.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.helper.BookLoopPreferences;

// SettingsFragment — fully updated settings screen.
//
// SHARED PREFERENCES (via BookLoopPreferences):
//   ✅ Push Notifications toggle  → KEY: notifications_enabled (boolean)
//   ✅ App Theme                  → KEY: app_theme ("light"/"dark"/"system")
//   ✅ Last Delivery Name/City    → pre-filled from KEY: last_delivery_name/city
//
// FIREBASE AUTH:
//   ✅ Shows signed-in email (FirebaseAuth.currentUser)
//   ✅ Change Password — re-authenticates then updates password
public class SettingsFragment extends Fragment {

    private BookLoopPreferences prefs;

    // Views
    private SwitchMaterial    switchNotifications;
    private RadioGroup        themeGroup;
    private TextView          themeLabel;
    private TextView          deliveryPreview;
    private TextView          userEmail;
    private TextInputLayout   tilCurrentPassword;
    private TextInputLayout   tilNewPassword;
    private TextInputLayout   tilConfirmPassword;
    private TextInputEditText etCurrentPassword;
    private TextInputEditText etNewPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton    btnChangePassword;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = new BookLoopPreferences(requireContext());

        bindViews(view);
        loadSavedPreferences();
        setupListeners();

        // Staggered entry animations
        animateSection(view.findViewById(R.id.settings_section_notifications), 0);
        animateSection(view.findViewById(R.id.settings_section_appearance),    80);
        animateSection(view.findViewById(R.id.settings_section_delivery),     160);
        animateSection(view.findViewById(R.id.settings_section_account),      240);
    }

    // ── Bind all views ────────────────────────────────────────────────────────

    private void bindViews(View view) {
        switchNotifications = view.findViewById(R.id.settings_switch_notifications);
        themeGroup          = view.findViewById(R.id.settings_theme_group);
        themeLabel          = view.findViewById(R.id.settings_theme_label);
        deliveryPreview     = view.findViewById(R.id.settings_delivery_preview);
        userEmail           = view.findViewById(R.id.settings_user_email);
        tilCurrentPassword  = view.findViewById(R.id.settings_til_current_password);
        tilNewPassword      = view.findViewById(R.id.settings_til_new_password);
        tilConfirmPassword  = view.findViewById(R.id.settings_til_confirm_password);
        etCurrentPassword   = view.findViewById(R.id.settings_et_current_password);
        etNewPassword       = view.findViewById(R.id.settings_et_new_password);
        etConfirmPassword   = view.findViewById(R.id.settings_et_confirm_password);
        btnChangePassword   = view.findViewById(R.id.settings_btn_change_password);
    }

    // ── Load saved preferences into UI ────────────────────────────────────────

    private void loadSavedPreferences() {
        // Notifications toggle
        if (switchNotifications != null) {
            switchNotifications.setChecked(prefs.isNotificationsEnabled());
        }

        // Theme radio — restore saved theme selection
        if (themeGroup != null) {
            String saved = prefs.getAppTheme(); // "light" / "dark" / "system"
            if ("light".equals(saved)) {
                themeGroup.check(R.id.settings_theme_light);
                if (themeLabel != null) themeLabel.setText("Light mode");
            } else if ("dark".equals(saved)) {
                themeGroup.check(R.id.settings_theme_dark);
                if (themeLabel != null) themeLabel.setText("Dark mode");
            } else {
                themeGroup.check(R.id.settings_theme_system);
                if (themeLabel != null) themeLabel.setText("System default");
            }
        }

        // Delivery preview
        if (deliveryPreview != null) {
            String name = prefs.getLastDeliveryName();
            String city = prefs.getLastDeliveryCity();
            if (name != null && !name.isEmpty()) {
                deliveryPreview.setText("Last used: " + name + " — " + (city != null ? city : ""));
            } else {
                deliveryPreview.setText("No delivery details saved yet.\nThey auto-save after your first order.");
            }
        }

        // Firebase signed-in email
        if (userEmail != null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getEmail() != null) {
                userEmail.setText(user.getEmail());
            } else {
                userEmail.setText("Not signed in");
            }
        }
    }

    // ── Wire up listeners ─────────────────────────────────────────────────────

    private void setupListeners() {

        // Notifications toggle
        if (switchNotifications != null) {
            switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.setNotificationsEnabled(isChecked);
                toast(isChecked ? "Notifications enabled" : "Notifications disabled");
            });
        }

        // Theme radio group
        if (themeGroup != null) {
            themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.settings_theme_light) {
                    prefs.setAppTheme("light");
                    if (themeLabel != null) themeLabel.setText("Light mode");
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                } else if (checkedId == R.id.settings_theme_dark) {
                    prefs.setAppTheme("dark");
                    if (themeLabel != null) themeLabel.setText("Dark mode");
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    prefs.setAppTheme("system");
                    if (themeLabel != null) themeLabel.setText("System default");
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                }
            });
        }

        // Change password button
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> {
                v.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.click_animation));
                attemptChangePassword();
            });
        }
    }

    // ── Change Password (Firebase re-auth + update) ───────────────────────────

    private void attemptChangePassword() {
        // Clear previous errors
        if (tilCurrentPassword != null) tilCurrentPassword.setError(null);
        if (tilNewPassword     != null) tilNewPassword.setError(null);
        if (tilConfirmPassword != null) tilConfirmPassword.setError(null);

        String current = etCurrentPassword != null && etCurrentPassword.getText() != null
                ? etCurrentPassword.getText().toString().trim() : "";
        String newPwd  = etNewPassword  != null && etNewPassword.getText()  != null
                ? etNewPassword.getText().toString().trim()  : "";
        String confirm = etConfirmPassword != null && etConfirmPassword.getText() != null
                ? etConfirmPassword.getText().toString().trim() : "";

        // Validate
        if (TextUtils.isEmpty(current)) {
            if (tilCurrentPassword != null) tilCurrentPassword.setError("Enter your current password");
            return;
        }
        if (TextUtils.isEmpty(newPwd)) {
            if (tilNewPassword != null) tilNewPassword.setError("Enter a new password");
            return;
        }
        if (newPwd.length() < 6) {
            if (tilNewPassword != null) tilNewPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (!newPwd.equals(confirm)) {
            if (tilConfirmPassword != null) tilConfirmPassword.setError("Passwords do not match");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            toast("Not signed in");
            return;
        }

        // Disable button while working
        if (btnChangePassword != null) {
            btnChangePassword.setEnabled(false);
            btnChangePassword.setText("Updating...");
        }

        // Re-authenticate then update
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), current);
        user.reauthenticate(credential).addOnCompleteListener(reAuthTask -> {
            if (!isAdded()) return;
            if (reAuthTask.isSuccessful()) {
                user.updatePassword(newPwd).addOnCompleteListener(updateTask -> {
                    if (!isAdded()) return;
                    resetPasswordButton();
                    if (updateTask.isSuccessful()) {
                        toast("Password updated successfully");
                        clearPasswordFields();
                    } else {
                        toast("Failed to update password. Try again.");
                    }
                });
            } else {
                resetPasswordButton();
                if (tilCurrentPassword != null) {
                    tilCurrentPassword.setError("Incorrect current password");
                }
            }
        });
    }

    private void resetPasswordButton() {
        if (btnChangePassword != null) {
            btnChangePassword.setEnabled(true);
            btnChangePassword.setText("Update Password");
        }
    }

    private void clearPasswordFields() {
        if (etCurrentPassword != null) etCurrentPassword.setText("");
        if (etNewPassword     != null) etNewPassword.setText("");
        if (etConfirmPassword != null) etConfirmPassword.setText("");
    }

    // ── Animation helper ──────────────────────────────────────────────────────

    private void animateSection(View section, long delayMs) {
        if (section == null) return;
        section.setAlpha(0f);
        section.setTranslationY(30f);
        section.postDelayed(() -> {
            if (!isAdded()) return;
            section.animate().alpha(1f).translationY(0f).setDuration(350).start();
        }, delayMs);
    }

    // ── Toast ─────────────────────────────────────────────────────────────────

    private void toast(String msg) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }
}
package lk.jiat.bookloop.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import lk.jiat.bookloop.R;


public class SignInActivity extends AppCompatActivity {

    private LottieAnimationView loginAnimationView;
    private TextInputEditText   emailInput;
    private TextInputEditText   passwordInput;
    private MaterialButton      signInButton;
    private TextView            signUpTextView;
    private ProgressBar         progressBar;
    private TextView forgotPasswordTextView;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        mAuth = FirebaseAuth.getInstance();

        loginAnimationView = findViewById(R.id.loginAnimationView);
        emailInput         = findViewById(R.id.emailInput);
        passwordInput      = findViewById(R.id.passwordInput);
        signInButton       = findViewById(R.id.signInButton);
        signUpTextView     = findViewById(R.id.signUpTextView);
        progressBar        = findViewById(R.id.progressBar);
        forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView);

        loginAnimationView.playAnimation();

        applyEntranceAnimations();

        signInButton.setOnClickListener(v -> attemptSignIn());
        signUpTextView.setOnClickListener(v -> navigateToSignUp());
        forgotPasswordTextView.setOnClickListener(v -> showForgotPasswordDialog());

    }

    private void applyEntranceAnimations() {
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        Animation fadeIn  = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        emailInput.startAnimation(slideUp);

        passwordInput.postDelayed(() ->
                passwordInput.startAnimation(slideUp), 100);

        signInButton.postDelayed(() ->
                signInButton.startAnimation(fadeIn), 200);

        signUpTextView.postDelayed(() ->
                signUpTextView.startAnimation(fadeIn), 300);
    }

    private void attemptSignIn() {
        String email    = emailInput.getText() != null ? emailInput.getText().toString().trim() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            shakeView(emailInput);
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            emailInput.requestFocus();
            shakeView(emailInput);
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            passwordInput.requestFocus();
            shakeView(passwordInput);
            return;
        }

        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            passwordInput.requestFocus();
            shakeView(passwordInput);
            return;
        }

        signInUser(email, password);
    }

    private void signInUser(String email, String password) {
        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                        navigateToMain();
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Authentication failed";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        shakeView(emailInput);
                        shakeView(passwordInput);
                    }
                });
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void navigateToSignUp() {
        startActivity(new Intent(this, SignUpActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void shakeView(View view) {
        view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
    }

    private void showForgotPasswordDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Reset Password");
        builder.setMessage("Enter your email to receive a password reset link.");

        final EditText input = new EditText(this);
        input.setHint("Email");
        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String email = input.getText().toString().trim();
            if (!TextUtils.isEmpty(email)) {
                sendPasswordResetEmail(email);
            } else {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void sendPasswordResetEmail(String email) {
        showLoading(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Reset link sent to " + email,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Failed to send reset email",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }



    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        signInButton.setEnabled(!show);
        emailInput.setEnabled(!show);
        passwordInput.setEnabled(!show);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loginAnimationView != null) loginAnimationView.cancelAnimation();
    }
}
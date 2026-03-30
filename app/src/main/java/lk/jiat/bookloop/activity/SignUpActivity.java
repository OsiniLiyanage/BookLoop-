package lk.jiat.bookloop.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.databinding.ActivitySignUpBinding;
import lk.jiat.bookloop.model.User;

/**
 * SignUpActivity — all original Firebase logic preserved exactly.
 * Changes:
 *   - Layout updated to match new design (TextInputLayout, Lottie).
 *   - Added entrance animations (same style as SignInActivity).
 *   - Added ProgressBar show/hide during account creation.
 *   - ProgressBar ID in layout: signup_progress_bar (no binding field — accessed directly).
 *
 * ViewBinding IDs unchanged (signup_input_name, signup_input_email,
 * signup_input_password, signup_input_retype_password, signup_btn_signup, signup_btn_signin).
 */
public class SignUpActivity extends AppCompatActivity {

    private ActivitySignUpBinding binding;
    private FirebaseAuth          firebaseAuth;
    private FirebaseFirestore     firebaseFirestore;
    private LottieAnimationView   signupAnimationView;
    private ProgressBar           progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth      = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        signupAnimationView = findViewById(R.id.signupAnimationView);
        progressBar         = findViewById(R.id.signup_progress_bar);

        if (signupAnimationView != null) signupAnimationView.playAnimation();

        applyEntranceAnimations();

        // ── Navigate to SignInActivity ────────────────────────────
        binding.signupBtnSignin.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, SignInActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
        });

        // ── Create account ────────────────────────────────────────
        binding.signupBtnSignup.setOnClickListener(view -> {

            String name            = binding.signupInputName.getText().toString().trim();
            String email           = binding.signupInputEmail.getText().toString().trim();
            String password        = binding.signupInputPassword.getText().toString().trim();
            String retypePassword  = binding.signupInputRetypePassword.getText().toString().trim();

            // ── Validation (identical to original) ────────────────
            if (name.isEmpty()) {
                binding.signupInputName.setError("Name is required");
                binding.signupInputName.requestFocus();
                return;
            }
            if (email.isEmpty()) {
                binding.signupInputEmail.setError("Email is required");
                binding.signupInputEmail.requestFocus();
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.signupInputEmail.setError("Enter valid email");
                binding.signupInputEmail.requestFocus();
                return;
            }
            if (password.isEmpty()) {
                binding.signupInputPassword.setError("Password is required");
                binding.signupInputPassword.requestFocus();
                return;
            }
            if (password.length() < 6) {
                binding.signupInputPassword.setError("Password must be at least 6 characters");
                binding.signupInputPassword.requestFocus();
                return;
            }
            if (!retypePassword.equals(password)) {
                binding.signupInputRetypePassword.setError("Passwords do not match");
                binding.signupInputRetypePassword.requestFocus();
                return;
            }

            // ── Firebase: create user ─────────────────────────────
            showLoading(true);

            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                String uid  = task.getResult().getUser().getUid();
                                User   user = User.builder()
                                        .uid(uid).name(name).email(email).build();

                                firebaseFirestore.collection("users").document(uid).set(user)
                                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                                            @Override
                                            public void onSuccess(Void unused) {
                                                showLoading(false);
                                                Toast.makeText(getApplicationContext(),
                                                        "Account created! Welcome to BookLoop!",
                                                        Toast.LENGTH_SHORT).show();
                                                startActivity(new Intent(
                                                        SignUpActivity.this, SignInActivity.class));
                                                finish();
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                showLoading(false);
                                                Toast.makeText(getApplicationContext(),
                                                        "Failed to save profile",
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            } else {
                                showLoading(false);
                                String msg = task.getException() != null
                                        ? task.getException().getMessage()
                                        : "Registration failed";
                                Toast.makeText(getApplicationContext(), msg,
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        });
    }

    private void applyEntranceAnimations() {
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        Animation fadeIn  = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        binding.signupInputName.startAnimation(slideUp);
        binding.signupInputEmail.postDelayed(
                () -> binding.signupInputEmail.startAnimation(slideUp), 80);
        binding.signupInputPassword.postDelayed(
                () -> binding.signupInputPassword.startAnimation(slideUp), 160);
        binding.signupInputRetypePassword.postDelayed(
                () -> binding.signupInputRetypePassword.startAnimation(slideUp), 240);
        binding.signupBtnSignup.postDelayed(
                () -> binding.signupBtnSignup.startAnimation(fadeIn), 320);
    }

    private void showLoading(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.signupBtnSignup.setEnabled(!show);
        binding.signupInputName.setEnabled(!show);
        binding.signupInputEmail.setEnabled(!show);
        binding.signupInputPassword.setEnabled(!show);
        binding.signupInputRetypePassword.setEnabled(!show);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (signupAnimationView != null) signupAnimationView.cancelAnimation();
    }
}
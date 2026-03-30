package lk.jiat.bookloop.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.FirebaseAuth;

import lk.jiat.bookloop.R;


// HOW LOTTIE WORKS:
//   LottieAnimationView loads a JSON animation file from res/raw/.
//   The animation runs once, then fires addAnimatorListener to navigate.
//   No Handler.postDelayed needed — the animation itself controls timing.
//

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide action bar on splash
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        setContentView(R.layout.activity_splash);

        LottieAnimationView lottie = findViewById(R.id.splash_lottie);

        // Play once — when animation ends, route the user
        lottie.setRepeatCount(2);
        lottie.playAnimation();

        lottie.addAnimatorListener(new android.animation.Animator.AnimatorListener() {
            @Override public void onAnimationStart(android.animation.Animator a) {}
            @Override public void onAnimationCancel(android.animation.Animator a) {}
            @Override public void onAnimationRepeat(android.animation.Animator a) {}

            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                // Check login state and navigate
                navigateNext();
            }
        });
    }

    private void navigateNext() {
        // If user is already signed in, go directly to MainActivity
        // If not, go to SignInActivity
        boolean loggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;

        Intent intent = new Intent(this,
                loggedIn ? MainActivity.class : SignInActivity.class);

        startActivity(intent);
        // Slide out: new screen slides in from right, splash slides out to left
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
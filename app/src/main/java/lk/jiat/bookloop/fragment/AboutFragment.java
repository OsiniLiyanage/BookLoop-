package lk.jiat.bookloop.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;

import lk.jiat.bookloop.R;

// AboutFragment — shows app information, how it works, tech stack, and contact.
//
// ANIMATIONS USED:
//   - Lottie book animation (re-uses splash_animation.json from res/raw/)
//   - Cards slide up with staggered delays using slide_up.xml + fade_in.xml
//   These animations are from the anim/ folder you already have.
public class AboutFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Start the Lottie hero animation
        LottieAnimationView lottie = view.findViewById(R.id.about_lottie);
        if (lottie != null) {
            lottie.playAnimation();
        }

        // Staggered card entrance animations — each card slides up 100ms after the previous
        // This creates a cascade effect that makes the page feel alive and polished.
        // Uses slide_up.xml from res/anim/ (the file in your screenshot).
        animateCard(view.findViewById(R.id.about_card_what),  0);
        animateCard(view.findViewById(R.id.about_card_how),   100);
        animateCard(view.findViewById(R.id.about_card_tech),  200);
        animateCard(view.findViewById(R.id.about_card_contact), 300);
    }

    // Animates a card with a slide-up + fade-in after the given delay (ms)
    private void animateCard(View card, long delayMs) {
        if (card == null) return;
        card.setVisibility(View.INVISIBLE);
        card.postDelayed(() -> {
            if (!isAdded()) return;
            card.setVisibility(View.VISIBLE);
            card.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up));
        }, delayMs);
    }
}
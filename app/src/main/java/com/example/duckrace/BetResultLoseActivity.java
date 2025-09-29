package com.example.duckrace;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class BetResultLoseActivity extends AppCompatActivity {

    private MediaPlayer music;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bet_result_lose);

        ImageView imgSad = findViewById(R.id.imgSad);
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvAmount = findViewById(R.id.tvAmount);
        Button btnTryAgain = findViewById(R.id.btnTryAgain);

        int amount = getIntent().getIntExtra("amount", 0);
        String duckName = getIntent().getStringExtra("duck");
        if (duckName == null)
            duckName = "Your duck";
        tvTitle.setText("Lost! " + duckName + " was overtaken");
        tvAmount.setText("-0");

        // Animate: wobble the icon and fade the amount in
        ObjectAnimator rotate = ObjectAnimator.ofFloat(imgSad, "rotation", -8f, 8f, 0f);
        rotate.setDuration(900);
        rotate.setInterpolator(new DecelerateInterpolator());
        rotate.start();

        ObjectAnimator alpha = ObjectAnimator.ofFloat(tvAmount, "alpha", 0f, 1f);
        alpha.setDuration(600);
        alpha.start();

        // Count-down animation for coins
        android.animation.ValueAnimator counter = android.animation.ValueAnimator.ofInt(0, amount);
        counter.setDuration(Math.min(2000, 400 + amount * 10L));
        counter.addUpdateListener(a -> tvAmount.setText("-" + (Integer) a.getAnimatedValue()));
        counter.start();

        btnTryAgain.setOnClickListener(v -> finish());

        // Subtle screen shake
        imgSad.postDelayed(() -> {
            imgSad.animate().translationXBy(-8).setDuration(40)
                    .withEndAction(() -> imgSad.animate().translationXBy(16).setDuration(80)
                            .withEndAction(() -> imgSad.animate().translationXBy(-8).setDuration(40).start()).start())
                    .start();
        }, 250);

        // Deduct coins with a slight delay to sync with animation
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && amount > 0) {
            tvAmount.postDelayed(() -> {
                FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                        .update("coins", FieldValue.increment(-amount))
                        .addOnSuccessListener(
                                aVoid -> Toast.makeText(this, "-" + amount + " coins", Toast.LENGTH_SHORT).show());
            }, 500);
        }

        // Music on lose (uses res/raw/lose.mp3)
        try {
            music = MediaPlayer.create(this, R.raw.lose);
            if (music != null) {
                music.setVolume(0.8f, 0.8f);
                music.start();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (music != null) {
            try {
                music.release();
            } catch (Exception ignored) {
            }
            music = null;
        }
    }
}

package com.example.duckrace;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class BetResultWinActivity extends AppCompatActivity {

    private MediaPlayer music;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bet_result_win);

        ImageView imgTrophy = findViewById(R.id.imgTrophy);
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvAmount = findViewById(R.id.tvAmount);
        View confettiLayer = findViewById(R.id.confettiLayer);
        Button btnContinue = findViewById(R.id.btnContinue);

        int amount = getIntent().getIntExtra("amount", 0);
        String duckName = getIntent().getStringExtra("duck");
        if (duckName == null)
            duckName = "Your duck";
        tvTitle.setText(duckName + " wins!");
        tvAmount.setText("+0");

        // Animate: bounce trophy and count-up amount
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(imgTrophy, "scaleX", 0.6f, 1.1f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(imgTrophy, "scaleY", 0.6f, 1.1f, 1.0f);
        scaleX.setDuration(900);
        scaleY.setDuration(900);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleX.start();
        scaleY.start();

        // Count-up animation for coins
        ValueAnimator counter = ValueAnimator.ofInt(0, amount);
        counter.setDuration(Math.min(2000, 400 + amount * 10L));
        counter.addUpdateListener(a -> tvAmount.setText("+" + (Integer) a.getAnimatedValue()));
        counter.start();

        // Simple confetti: spawn small views that fall
        spawnConfetti((ViewGroup) confettiLayer, 60);

        // Music on win (uses res/raw/win.mp3)
        try {
            music = MediaPlayer.create(this, R.raw.win);
            if (music != null) {
                music.setVolume(0.8f, 0.8f);
                music.start();
            }
        } catch (Exception ignored) {
        }

        btnContinue.setOnClickListener(v -> finish());
    }

    private void spawnConfetti(ViewGroup layer, int count) {
        int width = layer.getWidth();
        int height = layer.getHeight();
        if (width == 0 || height == 0) {
            layer.post(() -> spawnConfetti(layer, count));
            return;
        }
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.confetti_particle);
            int size = (int) (8 + Math.random() * 10);
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(size, size);
            dot.setLayoutParams(lp);
            int startX = (int) (Math.random() * width);
            dot.setX(startX);
            dot.setY(-size);
            layer.addView(dot);

            dot.animate()
                    .alpha(0.9f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        dot.animate()
                                .translationY(height + size)
                                .translationX(startX + (float) ((Math.random() - 0.5) * 200))
                                .rotationBy((float) ((Math.random() - 0.5) * 360))
                                .setDuration(1200 + (long) (Math.random() * 800))
                                .withEndAction(() -> layer.removeView(dot))
                                .start();
                    })
                    .start();
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

package com.example.duckrace;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.WindowManager;
import android.media.MediaPlayer;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private Spinner spDuckCount;
    private Button btnStart, btnReset, btnPlayerName, btnConfirmBet;
    private ImageButton btnAddCoins;
    private LinearLayout lanesContainer;
    private View trackFrame, finishLine;
    private TextView tvCountdown, tvCoins;

    private final List<DuckRunner> runners = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean raceRunning = false;
    private boolean raceFinished = false;

    // Tham số "vật lý" (đơn vị: px/s và px/s^2)

    private float MIN_SPEED = 120f;
    private float MAX_SPEED = 260f;
    private float BOOST_ACCEL = 220f;
    private float FRICTION = 140f;
    private float RANDOM_JITTER_ACCEL = 180f; // gia tốc ngẫu nhiên mỗi tick để tạo kịch tính

    private float finishX = 0f;

    private final Random random = new Random();

    // MediaPlayer cho âm thanh
    private MediaPlayer backgroundMusic;
    private MediaPlayer raceStartSound;
    private MediaPlayer goSound;
    private MediaPlayer[] duckQuackSounds;
    private MediaPlayer raceFinishSound;

    // Timer cho tiếng vịt kêu liên tục
    private Handler quackHandler = new Handler(Looper.getMainLooper());
    private Runnable quackRunnable;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration userReg;

    // Tien cuoc
    private final List<Bet> currentBets = new ArrayList<>();

    private static class Bet implements java.io.Serializable {
        String duckName;
        int amount;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Thiết lập full screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(params);
        }
        hideSystemUI();

        setContentView(R.layout.activity_main);

        spDuckCount = findViewById(R.id.spDuckCount);
        btnStart = findViewById(R.id.btnStart);
        btnReset = findViewById(R.id.btnReset);
        lanesContainer = findViewById(R.id.lanesContainer);
        trackFrame = findViewById(R.id.trackFrame);
        finishLine = findViewById(R.id.finishLine);
        tvCountdown = findViewById(R.id.tvCountdown);
        btnPlayerName = findViewById(R.id.btnPlayerName);
        tvCoins = findViewById(R.id.tvCoins);
        btnAddCoins = findViewById(R.id.btnAddCoin);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        btnConfirmBet = findViewById(R.id.btnConfirmBet);
        btnConfirmBet.setOnClickListener(v -> confirmBetAndStartRace());

        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            setupUserRealtime(current.getUid());
        } else {
            btnPlayerName.setText("Player");
            tvCoins.setText("0");
        }
        btnPlayerName.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc muốn đăng xuất không?")
                    .setPositiveButton("đăng xuất", (d, w) -> {
                        FirebaseAuth.getInstance().signOut();
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Spinner 3..8 vịt
        Integer[] counts = new Integer[] { 3, 4, 5 };
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, counts);
        spDuckCount.setAdapter(adapter);
        spDuckCount.setSelection(2); // mặc định 5 vịt

        // Khởi tạo làn đầu tiên
        buildLanes((Integer) spDuckCount.getSelectedItem());

        spDuckCount.setOnItemSelectedListener(
                new SimpleItemSelectedListener(() -> buildLanes((Integer) spDuckCount.getSelectedItem())));

        btnStart.setOnClickListener(v -> {
            if (raceRunning)
                return;
            startCountdownThenRace();
        });

        btnReset.setOnClickListener(v -> resetRace());

        // Nút nạp xu
        btnAddCoins.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TopUpActivity.class);
            startActivity(intent);
        });

        // Khởi tạo âm thanh
        initializeSounds();

        // Bắt đầu tiếng vịt kêu liên tục ngay khi mở app
        startContinuousQuacking();
    }

    private void initializeSounds() {
        try {
            int backgroundMusicId = getResources().getIdentifier("background_music", "raw", getPackageName());
            if (backgroundMusicId != 0) {
                backgroundMusic = MediaPlayer.create(this, backgroundMusicId);
                if (backgroundMusic != null) {
                    backgroundMusic.setLooping(true);
                    backgroundMusic.setVolume(0.6f, 0.6f);
                }
            }

            int raceStartId = getResources().getIdentifier("countdown", "raw", getPackageName());
            if (raceStartId != 0) {
                raceStartSound = MediaPlayer.create(this, raceStartId);
                if (raceStartSound != null) {
                    raceStartSound.setVolume(0.7f, 0.7f);
                }
            }

            int goId = getResources().getIdentifier("go", "raw", getPackageName());
            if (goId != 0) {
                goSound = MediaPlayer.create(this, goId);
                if (goSound != null) {
                    goSound.setVolume(0.8f, 0.8f);
                }
            }

            duckQuackSounds = new MediaPlayer[3];
            String[] quackFiles = { "duck_quack_1", "duck_quack_2", "duck_quack_3" };

            for (int i = 0; i < quackFiles.length; i++) {
                int quackId = getResources().getIdentifier(quackFiles[i], "raw", getPackageName());
                if (quackId != 0) {
                    duckQuackSounds[i] = MediaPlayer.create(this, quackId);
                    if (duckQuackSounds[i] != null) {
                        duckQuackSounds[i].setVolume(0.75f, 0.75f);
                    }
                }
            }

            int raceFinishId = getResources().getIdentifier("race_finish", "raw", getPackageName());
            if (raceFinishId != 0) {
                raceFinishSound = MediaPlayer.create(this, raceFinishId);
                if (raceFinishSound != null) {
                    raceFinishSound.setVolume(0.8f, 0.8f);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper: set drawable và chạy/dừng animation (GIF/AnimationDrawable)
    private void setDrawablePlay(ImageView iv, int resId, boolean play) {
        iv.setImageResource(resId);
        Drawable d = iv.getDrawable();
        if (d instanceof AnimationDrawable) {
            AnimationDrawable a = (AnimationDrawable) d;
            if (play)
                a.start();
            else
                a.stop();
        } else if (Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable) {
            AnimatedImageDrawable a = (AnimatedImageDrawable) d;
            if (play)
                a.start();
            else
                a.stop();
        }
    }

    // Helper: start/stop cho drawable HIỆN CÓ của ImageView (không đổi resource)
    private void startDrawableIfAnim(ImageView iv) {
        if (iv == null)
            return;
        Drawable d = iv.getDrawable();
        if (d instanceof AnimationDrawable)
            ((AnimationDrawable) d).start();
        else if (Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable)
            ((AnimatedImageDrawable) d).start();
    }

    private void stopDrawableIfAnim(ImageView iv) {
        if (iv == null)
            return;
        Drawable d = iv.getDrawable();
        if (d instanceof AnimationDrawable)
            ((AnimationDrawable) d).stop();
        else if (Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable)
            ((AnimatedImageDrawable) d).stop();
    }

    private void buildLanes(int count) {
        // Nếu đang chạy thì dừng
        stopRaceLoop();
        lanesContainer.removeAllViews();
        runners.clear();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < count; i++) {
            View lane = inflater.inflate(R.layout.item_duck_lane, lanesContainer, false);

            TextView tvName = lane.findViewById(R.id.tvName);
            ImageView imgDuck = lane.findViewById(R.id.imgDuck);
            ImageView imgWave = lane.findViewById(R.id.imgWave);
            View area = lane.findViewById(R.id.area);

            String name = String.format(Locale.getDefault(), "Vịt %d", i + 1);
            tvName.setText(name);

            imgDuck.setImageResource(R.drawable.duck_run);
            imgDuck.clearColorFilter();
            startDrawableIfAnim(imgDuck);

            // ẨN SÓNG BAN ĐẦU (chưa bấm Start)
            if (imgWave != null) {
                Drawable wd = imgWave.getDrawable();
                if (wd instanceof AnimationDrawable)
                    ((AnimationDrawable) wd).stop();
                else if (Build.VERSION.SDK_INT >= 28 && wd instanceof AnimatedImageDrawable)
                    ((AnimatedImageDrawable) wd).stop();
                imgWave.setVisibility(View.GONE);
            }

            DuckRunner runner = new DuckRunner(name, imgDuck, area);
            runner.wave = imgWave;
            runners.add(runner);

            lanesContainer.addView(lane);
        }

        // Sau khi layout xong, tính toạ độ vạch đích theo vùng chạy
        trackFrame.post(this::computeFinishX);
        raceFinished = false;
        tvCountdown.setText("");
        enableControls(true);
    }

    private void computeFinishX() {
        if (runners.isEmpty())
            return;
        DuckRunner ref = runners.get(0);

        int areaWidth = ref.area.getWidth();
        int duckWidth = ref.duck.getWidth();
        int rightPadding = dp(8);

        finishX = Math.max(0, areaWidth - duckWidth - rightPadding);
    }

    private void startCountdownThenRace() {
        enableControls(false);

        // Hiện "3" ngay lập tức cho chắc
        tvCountdown.setText("3");

        playSound(raceStartSound);

        // 3 giây, tick mỗi 1 giây -> lần lượt 3, 2, 1
        new CountDownTimer(3000, 1000) {
            int n = 3;

            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText(String.valueOf(n));
                n--;
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("FIGHT");
                playGoSound();
                // Xoá chữ sau 0.5s, race bắt đầu ngay
                tvCountdown.postDelayed(() -> tvCountdown.setText(""), 500);
                startRace();
            }
        }.start();
    }

    private void startRace() {
        // Đảm bảo tính được finishX
        computeFinishX();

        // Bắt đầu nhạc nền
        playBackgroundMusic();

        // Reset vị trí/tốc độ và thiết lập nhịp "boost" ngẫu nhiên
        for (DuckRunner r : runners) {
            r.reset();
            startDrawableIfAnim(r.duck);
            scheduleBoost(r);

            // HIỆN SÓNG & CHẠY ANIMATION SÓNG KHI BẮT ĐẦU
            if (r.wave != null) {
                r.wave.setVisibility(View.VISIBLE);
                Drawable wd = r.wave.getDrawable();
                if (wd instanceof AnimationDrawable)
                    ((AnimationDrawable) wd).start();
                else if (Build.VERSION.SDK_INT >= 28 && wd instanceof AnimatedImageDrawable)
                    ((AnimatedImageDrawable) wd).start();
            }
        }

        raceRunning = true;
        raceFinished = false;
        lastTickMs = SystemClock.elapsedRealtime();
        handler.post(gameLoop);
    }

    private void resetRace() {
        stopRaceLoop();
        stopBackgroundMusic();
        stopGoSound();
        stopRaceStartSound();
        stopContinuousQuacking();

        for (DuckRunner r : runners) {
            r.resetToStart();
            stopDrawableIfAnim(r.duck);
            r.stopAnimation();

            // TẮT SÓNG LẠI KHI RESET
            if (r.wave != null) {
                Drawable wd = r.wave.getDrawable();
                if (wd instanceof AnimationDrawable)
                    ((AnimationDrawable) wd).stop();
                else if (Build.VERSION.SDK_INT >= 28 && wd instanceof AnimatedImageDrawable)
                    ((AnimatedImageDrawable) wd).stop();
                r.wave.setVisibility(View.GONE);
                r.wave.setTranslationX(0f); // trả về đầu lane
            }
        }

        // Khởi động lại tiếng vịt kêu liên tục sau khi reset
        startContinuousQuacking();

        tvCountdown.setText("");
        raceFinished = false;
        enableControls(true);
    }

    private void enableControls(boolean enable) {
        spDuckCount.setEnabled(enable);
        btnStart.setEnabled(enable);
        btnConfirmBet.setEnabled(enable);
        btnReset.setEnabled(true);
    }

    private void stopRaceLoop() {
        raceRunning = false;
        handler.removeCallbacks(gameLoop);
        for (DuckRunner r : runners) {
            if (r.boostRunnable != null) {
                handler.removeCallbacks(r.boostRunnable);
                r.boostRunnable = null;
            }
        }
    }

    // ======= Game loop (60fps) =======
    private long lastTickMs = 0L;
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (!raceRunning)
                return;
            long now = SystemClock.elapsedRealtime();
            float dt = Math.min(0.05f, (now - lastTickMs) / 1000f); // clamp 50ms
            lastTickMs = now;

            DuckRunner winner = null;

            for (DuckRunner r : runners) {
                // Gia tốc ngắn hạn (boost) + ma sát kéo về tốc độ cơ bản
                float target = r.baseSpeed;
                float dv = target - r.speed;
                float accel = r.boosting ? BOOST_ACCEL : 0f;
                r.speed += (dv * 2.0f) * dt + accel * dt;

                // Clamp tốc độ
                if (r.speed < MIN_SPEED)
                    r.speed = MIN_SPEED;
                if (r.speed > MAX_SPEED)
                    r.speed = MAX_SPEED;

                // Cập nhật vị trí
                r.x += r.speed * dt;
                if (r.x > finishX) {
                    r.x = finishX;
                    if (winner == null)
                        winner = r;
                }

                r.duck.setTranslationX(r.x);

                // CHO SÓNG ĐI THEO NGANG VỚI VỊT
                if (r.wave != null) {
                    // nếu wave rộng ~ bằng con vịt, để offset = 0; cần chỉnh nhẹ thì đổi thành
                    // -dp(2) hoặc +dp(2)
                    float waveOffsetX = 0f;
                    r.wave.setTranslationX(r.x + waveOffsetX);
                }
            }

            if (winner != null && !raceFinished) {
                raceFinished = true;
                raceRunning = false;
                handler.removeCallbacks(this);
                for (DuckRunner r : runners)
                    handler.removeCallbacks(r.boostRunnable);

                // Dừng nhạc nền và âm thanh GO nhưng giữ tiếng vịt kêu liên tục
                stopBackgroundMusic();
                stopGoSound();
                // Phát âm thanh kết thúc
                playSound(raceFinishSound);

                showWinner(winner);
                enableControls(true);
            } else {
                handler.postDelayed(this, 16);
            }
        }
    };

    private void showWinner(DuckRunner winner) {
        FirebaseUser user = auth.getCurrentUser();
        int totalBet = 0;
        int totalWin = 0;
        String winnerName = winner.name;

        // Create bet data string
        StringBuilder betData = new StringBuilder();
        for (Bet bet : currentBets) {
            betData.append(bet.duckName).append(":").append(bet.amount).append(",");
        }
        String betDataString = betData.toString();

        if (user != null && !currentBets.isEmpty()) {
            int duckCount = runners.size();
            int multiplier = duckCount - 1;

            for (Bet bet : currentBets) {
                totalBet += bet.amount;
                if (bet.duckName.equals(winnerName)) {
                    totalWin += bet.amount * multiplier;
                }
            }

            if (totalWin > 0) {
                final int finalTotalWin = totalWin;
                db.collection("users").document(user.getUid())
                        .update("coins", FieldValue.increment(totalWin))
                        .addOnSuccessListener(aVoid -> {
                            Intent intent = new Intent(MainActivity.this, BetResultWinActivity.class);
                            intent.putExtra("amount", finalTotalWin);
                            intent.putExtra("duck", winnerName);
                            intent.putExtra("betData", betDataString);
                            intent.putExtra("duckCount", duckCount);
                            startActivity(intent);
                        });
            } else {
                Intent intent = new Intent(MainActivity.this, BetResultLoseActivity.class);
                intent.putExtra("amount", totalBet);
                intent.putExtra("duck", winnerName);
                intent.putExtra("betData", betDataString);
                intent.putExtra("duckCount", duckCount);
                startActivity(intent);
            }
        } else {
            Intent intent = new Intent(MainActivity.this, BetResultLoseActivity.class);
            intent.putExtra("amount", 0);
            intent.putExtra("duck", winnerName);
            intent.putExtra("betData", betDataString);
            intent.putExtra("duckCount", runners.size());
            startActivity(intent);
        }

        btnConfirmBet.setVisibility(View.VISIBLE);
        btnConfirmBet.setEnabled(true);
        currentBets.clear();
    }

    private void navigateToBetResult(DuckRunner winner) {
        FirebaseUser user = auth.getCurrentUser();
        int totalBet = 0;
        int totalWin = 0;
        String winnerName = winner.name;

        if (user != null && !currentBets.isEmpty()) {
            int duckCount = runners.size();
            int multiplier = duckCount - 1;

            for (Bet bet : currentBets) {
                totalBet += bet.amount;
                if (bet.duckName.equals(winnerName)) {
                    totalWin += bet.amount * multiplier;
                }
            }

            if (totalWin > 0) {
                final int finalTotalWin = totalWin;
                db.collection("users").document(user.getUid())
                        .update("coins", FieldValue.increment(totalWin))
                        .addOnSuccessListener(aVoid -> {
                            Intent intent = new Intent(MainActivity.this, BetResultWinActivity.class);
                            intent.putExtra("amount", finalTotalWin);
                            intent.putExtra("duck", winnerName);
                            startActivity(intent);
                        });
            } else {
                Intent intent = new Intent(MainActivity.this, BetResultLoseActivity.class);
                intent.putExtra("amount", totalBet);
                intent.putExtra("duck", winnerName);
                startActivity(intent);
            }
        } else {
            Intent intent = new Intent(MainActivity.this, BetResultWinActivity.class);
            intent.putExtra("amount", 0);
            intent.putExtra("duck", winnerName);
            startActivity(intent);
        }

        btnConfirmBet.setVisibility(View.VISIBLE);
        btnConfirmBet.setEnabled(true);
        currentBets.clear();
    }

    // Lên lịch "boost" ngẫu nhiên theo nhịp
    private void scheduleBoost(DuckRunner r) {
        int delay = randomRange(250, 700);
        handler.postDelayed(r.boostRunnable = () -> {
            r.boosting = true;
            int boostTime = randomRange(120, 320);
            handler.postDelayed(() -> {
                r.boosting = false;
                if (!raceFinished)
                    scheduleBoost(r);
            }, boostTime);
        }, delay);
    }

    private int randomRange(int a, int b) {
        return a + random.nextInt(Math.max(1, b - a + 1));
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    // ======= Quản lý âm thanh =======
    private void playSound(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying())
                    return;
                mediaPlayer.seekTo(200);
                mediaPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void playBackgroundMusic() {
        if (backgroundMusic != null) {
            try {
                backgroundMusic.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void stopBackgroundMusic() {
        if (backgroundMusic != null && backgroundMusic.isPlaying()) {
            try {
                backgroundMusic.pause();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void playDuckQuack() {
        if (duckQuackSounds == null)
            return;
        // Chỉ phát khi có player rảnh, tránh chồng âm
        for (int i = 0; i < duckQuackSounds.length; i++) {
            int idx = random.nextInt(duckQuackSounds.length);
            MediaPlayer mp = duckQuackSounds[idx];
            if (mp != null && !mp.isPlaying()) {
                playSound(mp);
                return;
            }
        }
    }

    private void stopRaceStartSound() {
        if (raceStartSound != null && raceStartSound.isPlaying()) {
            try {
                raceStartSound.pause();
                raceStartSound.seekTo(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void playGoSound() {
        if (goSound != null) {
            try {
                goSound.seekTo(2000);
                goSound.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void stopGoSound() {
        if (goSound != null && goSound.isPlaying()) {
            try {
                goSound.pause();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startContinuousQuacking() {
        stopContinuousQuacking();

        quackRunnable = new Runnable() {
            @Override
            public void run() {
                playDuckQuack();
                int delay = randomRange(1000, 3000);
                quackHandler.postDelayed(this, delay);
            }
        };

        quackHandler.postDelayed(quackRunnable, randomRange(500, 1500));
    }

    private void stopContinuousQuacking() {
        if (quackRunnable != null) {
            quackHandler.removeCallbacks(quackRunnable);
            quackRunnable = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayers();
    }

    private void releaseMediaPlayers() {
        if (backgroundMusic != null) {
            backgroundMusic.release();
            backgroundMusic = null;
        }
        if (raceStartSound != null) {
            raceStartSound.release();
            raceStartSound = null;
        }
        if (goSound != null) {
            goSound.release();
            goSound = null;
        }
        if (duckQuackSounds != null) {
            for (MediaPlayer quackSound : duckQuackSounds) {
                if (quackSound != null) {
                    quackSound.release();
                }
            }
            duckQuackSounds = null;
        }
        if (raceFinishSound != null) {
            raceFinishSound.release();
            raceFinishSound = null;
        }
    }

    // ======= Model 1 runner =======
    private static class DuckRunner {
        final String name;
        final ImageView duck;

        final View area;

        ImageView wave;

        float x = 0f; // translationX
        float speed = 0f; // px/s
        float baseSpeed = 0f; // mỗi vịt khác nhau nhẹ
        boolean boosting = false;
        Runnable boostRunnable;
        // Biến cá nhân hóa để tạo giãn cách
        float minSpeed = 0f;
        float maxSpeed = 0f;
        float targetOffset = 0f; // offset chậm biến thiên cộng vào baseSpeed

        DuckRunner(String name, ImageView duck, View area) {
            this.name = name;
            this.duck = duck;
            this.area = area;
        }

        void reset() {
            x = 0f;
            duck.setTranslationX(0f);
            // baseSpeed mỗi vịt hơi khác để tạo cá tính
            baseSpeed = 140f + (float) (Math.random() * 40f); // 140..180 px/s
            speed = baseSpeed;
            boosting = false;
        }

        void resetToStart() {
            x = 0f;
            speed = 0f;
            boosting = false;
            duck.setTranslationX(0f);

            // Khởi động lại animation vịt (giữ logic cũ)
            startAnimation();
        }

        void startAnimation() {
            Drawable d = duck.getDrawable();
            if (d instanceof AnimationDrawable) {
                ((AnimationDrawable) d).start();
            } else if (Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable) {
                ((AnimatedImageDrawable) d).start();
            }
        }

        void stopAnimation() {
            Drawable d = duck.getDrawable();
            if (d instanceof AnimationDrawable) {
                ((AnimationDrawable) d).stop();
            } else if (Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable) {
                ((AnimatedImageDrawable) d).stop();
            }
        }
    }

    // ======= Spinner listener rút gọn =======
    private static class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable onSelected;

        SimpleItemSelectedListener(Runnable onSelected) {
            this.onSelected = onSelected;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            onSelected.run();
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }

    private void setupUserRealtime(String uid) {
        DocumentReference ref = db.collection("users").document(uid);
        // bỏ listener cũ nếu có
        if (userReg != null)
            userReg.remove();

        userReg = ref.addSnapshotListener((snap, e) -> {
            if (e != null || snap == null || !snap.exists())
                return;
            String name = snap.getString("displayName");
            Long coins = snap.getLong("coins");
            btnPlayerName.setText(name == null ? "Player" : name);

            // Animate coin update
            String oldCoinText = tvCoins.getText().toString();
            String newCoinText = String.valueOf(coins == null ? 0 : coins);

            if (!oldCoinText.equals(newCoinText)) {
                animateCoinUpdate(newCoinText);
            } else {
                tvCoins.setText(newCoinText);
            }
        });
    }

    private void confirmBetAndStartRace() {
        currentBets.clear();
        int totalBet = 0;

        // Duyệt qua từng lane
        for (int i = 0; i < lanesContainer.getChildCount(); i++) {
            View lane = lanesContainer.getChildAt(i);
            CheckBox chk = lane.findViewById(R.id.chkDuck);
            EditText edt = lane.findViewById(R.id.edtAmount);

            if (chk != null && chk.isChecked()) {
                int amount = 0;
                try {
                    amount = Integer.parseInt(edt.getText().toString());
                } catch (Exception ignored) {}
                if (amount > 0) {
                    Bet bet = new Bet();
                    bet.duckName = chk.getText().toString().isEmpty()
                            ? "Vịt " + (i + 1)
                            : chk.getText().toString();
                    bet.amount = amount;
                    currentBets.add(bet);
                    totalBet += amount;
                }
            }
        }

        if (currentBets.isEmpty()) {
            Toast.makeText(this, "Bạn chưa đặt cược!", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            int finalTotalBet = totalBet;
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            long currentCoins = snapshot.getLong("coins") != null ? snapshot.getLong("coins") : 0;

                            if (currentCoins <= 0) {
                                Toast.makeText(this, "Bạn không còn xu!", Toast.LENGTH_SHORT).show();
                                currentBets.clear();
                            } else if (finalTotalBet > currentCoins) {
                                Toast.makeText(this,
                                        "Bạn chỉ có " + currentCoins + " xu, không thể đặt " + finalTotalBet,
                                        Toast.LENGTH_SHORT).show();
                                currentBets.clear();
                            } else {
                                db.collection("users").document(user.getUid())
                                        .update("coins", FieldValue.increment(-finalTotalBet))
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Bạn đã đặt " + finalTotalBet + " xu!", Toast.LENGTH_SHORT).show();
                                            btnConfirmBet.setEnabled(false);
                                            if (!raceRunning) {
                                                startCountdownThenRace();
                                            }
                                        });
                            }
                        }
                    });
        }
    }

    private void animateCoinUpdate(String newCoinText) {
        Animation bounceAnim = AnimationUtils.loadAnimation(this, R.anim.coin_bounce);
        bounceAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                // Animation started
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                tvCoins.setText(newCoinText);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // Animation repeated
            }
        });
        tvCoins.startAnimation(bounceAnim);
    }


    // ======= Full Screen Methods =======
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        // Giữ màn hình luôn sáng
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }
}

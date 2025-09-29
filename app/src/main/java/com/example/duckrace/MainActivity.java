package com.example.duckrace;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private Spinner spDuckCount;
    private Button btnStart, btnReset, btnPlayerName, btnBet;
    private ImageButton btnAddCoins;
    private LinearLayout lanesContainer;
    private View trackFrame, finishLine;
    private TextView tvCountdown, tvCoins;


    private final List<DuckRunner> runners = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean raceRunning = false;
    private boolean raceFinished = false;

    // Tham số "vật lý" (đơn vị: px/s và px/s^2)
    private float MIN_SPEED = 120f; // tốc độ cơ bản
    private float MAX_SPEED = 260f; // tốc độ tối đa
    private float BOOST_ACCEL = 220f; // gia tốc khi được "boost"
    private float FRICTION = 140f; // ma sát để vịt ko tăng vô hạn

    private float finishX = 0f; // vị trí đích (theo translationX trong vùng chạy)

    private final Random random = new Random();

    // MediaPlayer cho âm thanh
    private MediaPlayer backgroundMusic;
    private MediaPlayer raceStartSound;
    private MediaPlayer goSound; // Âm thanh "GO!"
    private MediaPlayer[] duckQuackSounds; // Mảng 3 loại âm thanh vịt kêu
    private MediaPlayer raceFinishSound;

    // Timer cho tiếng vịt kêu liên tục
    private Handler quackHandler = new Handler(Looper.getMainLooper());
    private Runnable quackRunnable;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration userReg;

    // Tien cuoc
    private final List<Bet> currentBets = new ArrayList<>();

    private static class Bet {
        String duckName;
        int amount;
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        db   = FirebaseFirestore.getInstance();
        btnBet = findViewById(R.id.btnBet);
        btnBet.setOnClickListener(v -> showBetDialog());

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
                        // Quay về màn hình Login
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Spinner 3..8 vịt
        Integer[] counts = new Integer[] { 3, 4, 5, 6, 7, 8 };
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
            // Nhạc nền (loop) - chỉ tạo nếu file tồn tại
            int backgroundMusicId = getResources().getIdentifier("background_music", "raw", getPackageName());
            if (backgroundMusicId != 0) {
                backgroundMusic = MediaPlayer.create(this, backgroundMusicId);
                if (backgroundMusic != null) {
                    backgroundMusic.setLooping(true);
                    backgroundMusic.setVolume(0.6f, 0.6f); // Tăng âm lượng x2 (0.3 -> 0.6)
                }
            }

            // Âm thanh bắt đầu cuộc đua
            int raceStartId = getResources().getIdentifier("race_start", "raw", getPackageName());
            if (raceStartId != 0) {
                raceStartSound = MediaPlayer.create(this, raceStartId);
                if (raceStartSound != null) {
                    raceStartSound.setVolume(0.7f, 0.7f);
                }
            }

            // Âm thanh "GO!"
            int goId = getResources().getIdentifier("go", "raw", getPackageName());
            if (goId != 0) {
                goSound = MediaPlayer.create(this, goId);
                if (goSound != null) {
                    goSound.setVolume(0.8f, 0.8f);
                }
            }

            // Khởi tạo mảng âm thanh vịt kêu (3 loại)
            duckQuackSounds = new MediaPlayer[3];
            String[] quackFiles = { "duck_quack_1", "duck_quack_2", "duck_quack_3" };

            for (int i = 0; i < quackFiles.length; i++) {
                int quackId = getResources().getIdentifier(quackFiles[i], "raw", getPackageName());
                if (quackId != 0) {
                    duckQuackSounds[i] = MediaPlayer.create(this, quackId);
                    if (duckQuackSounds[i] != null) {
                        duckQuackSounds[i].setVolume(0.75f, 0.75f); // Tăng âm lượng x1.5 (0.5 -> 0.75)
                    }
                }
            }

            // Âm thanh kết thúc
            int raceFinishId = getResources().getIdentifier("race_finish", "raw", getPackageName());
            if (raceFinishId != 0) {
                raceFinishSound = MediaPlayer.create(this, raceFinishId);
                if (raceFinishSound != null) {
                    raceFinishSound.setVolume(0.8f, 0.8f);
                }
            }
        } catch (Exception e) {
            // Nếu không có file âm thanh, tiếp tục mà không báo lỗi
            e.printStackTrace();
        }
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
            View area = lane.findViewById(R.id.area);

            String name = String.format(Locale.getDefault(), "Vịt %d", i + 1);
            tvName.setText(name);

            // Thiết lập animation cho vịt
            imgDuck.setImageResource(R.drawable.duck_animation);
            AnimationDrawable duckAnimation = (AnimationDrawable) imgDuck.getDrawable();
            duckAnimation.start();

            DuckRunner runner = new DuckRunner(name, imgDuck, area);
            runners.add(runner);

            lanesContainer.addView(lane);
        }

        // Sau khi layout xong, tính toạ độ vạch đích theo vùng chạy
        // (đưa về translationX của con vịt trong "area")
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

        // Mốc đích: mép phải vùng chạy trừ bề rộng vịt
        finishX = Math.max(0, areaWidth - duckWidth - rightPadding);

        // đặt vạch đích sát mép phải trackFrame (để nhìn "chuẩn")
        // (finishLine đã width 4dp & layout_gravity="end" nên OK)
    }

    private void startCountdownThenRace() {
        enableControls(false);
        tvCountdown.setText("3");

        // Tiếng vịt kêu vẫn tiếp tục phát trong countdown

        // Phát âm thanh bắt đầu
        playSound(raceStartSound);

        new CountDownTimer(3000, 1000) {
            int n = 3;

            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText(String.valueOf(n));
                n--;
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("GO!");
                // Phát âm thanh "GO!" (cắt bớt 2 giây đầu)
                playGoSound();
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

        // Tiếng vịt kêu đã phát liên tục từ khi mở app

        // Reset vị trí/tốc độ và thiết lập nhịp "boost" ngẫu nhiên
        for (DuckRunner r : runners) {
            r.reset();
            scheduleBoost(r);
        }

        raceRunning = true;
        raceFinished = false;
        lastTickMs = SystemClock.elapsedRealtime();
        handler.post(gameLoop);
    }

    private void resetRace() {
        stopRaceLoop();
        stopBackgroundMusic();
        stopGoSound(); // Dừng âm thanh GO nếu đang phát
        stopContinuousQuacking(); // Dừng tiếng vịt kêu liên tục

        for (DuckRunner r : runners) {
            r.resetToStart();
            r.stopAnimation();
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
        btnBet.setEnabled(enable);
        btnReset.setEnabled(true);
    }

    private void stopRaceLoop() {
        raceRunning = false;
        handler.removeCallbacks(gameLoop);
        for (DuckRunner r : runners) {
            handler.removeCallbacks(r.boostRunnable);
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
                // Ma sát "dịu": đẩy dần về target
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
                // Không dừng tiếng vịt kêu - để nó tiếp tục phát

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
        if (user != null && !currentBets.isEmpty()) {
            int totalReward = 0;

            // Tính tiền thưởng
            for (Bet bet : currentBets) {
                if (bet.duckName.equals(winner.name)) {
                    totalReward += bet.amount * 2; // trả lại gấp đôi số tiền cược
                }
            }

            int finalTotalReward = totalReward; // biến final để dùng trong lambda
            String finalWinnerName = winner.name; // copy tên vịt sang biến final

            if (finalTotalReward > 0) {
                db.collection("users").document(user.getUid())
                        .update("coins", FieldValue.increment(finalTotalReward))
                        .addOnSuccessListener(aVoid -> {
                            String msg = "🏆 " + finalWinnerName + " thắng!\nBạn nhận được " + finalTotalReward + " xu!";
                            new AlertDialog.Builder(this)
                                    .setTitle("Kết quả")
                                    .setMessage(msg)
                                    .setPositiveButton("OK", null)
                                    .show();

                            btnBet.setVisibility(View.VISIBLE); // hiện lại nút sau khi đua xong
                        });
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Kết quả")
                        .setMessage("🏆 " + finalWinnerName + " thắng!\nTiếc quá, bạn không thắng xu nào.")
                        .setPositiveButton("OK", null)
                        .show();

                btnBet.setVisibility(View.VISIBLE);
            }
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Kết quả")
                    .setMessage("🏆 " + winner.name + " thắng cuộc!")
                    .setPositiveButton("OK", null)
                    .show();

            btnBet.setVisibility(View.VISIBLE);
        }

        btnBet.setEnabled(true);
        currentBets.clear(); // reset cược sau khi xử lý
    }

    // Lên lịch "boost" ngẫu nhiên theo nhịp
    private void scheduleBoost(DuckRunner r) {
        int delay = randomRange(250, 700);
        handler.postDelayed(r.boostRunnable = () -> {
            r.boosting = true;
            // Không phát tiếng vịt kêu ở đây nữa vì đã có tiếng kêu liên tục
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
                mediaPlayer.seekTo(0);
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
        // Chọn ngẫu nhiên 1 trong 3 loại âm thanh vịt kêu
        if (duckQuackSounds != null) {
            int randomIndex = random.nextInt(duckQuackSounds.length);
            MediaPlayer selectedQuack = duckQuackSounds[randomIndex];
            if (selectedQuack != null) {
                playSound(selectedQuack);
            }
        }
    }

    private void playGoSound() {
        // Phát âm thanh "GO!" với cắt bớt 2 giây đầu
        if (goSound != null) {
            try {
                goSound.seekTo(2000); // Bỏ qua 2 giây đầu (2000ms)
                goSound.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void stopGoSound() {
        // Dừng âm thanh "GO!" nếu đang phát
        if (goSound != null && goSound.isPlaying()) {
            try {
                goSound.pause();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startContinuousQuacking() {
        // Dừng timer cũ nếu có
        stopContinuousQuacking();

        // Tạo timer mới cho tiếng vịt kêu liên tục (không phụ thuộc vào trạng thái
        // race)
        quackRunnable = new Runnable() {
            @Override
            public void run() {
                playDuckQuack();
                // Lên lịch lần tiếp theo (1-3 giây ngẫu nhiên)
                int delay = randomRange(1000, 3000);
                quackHandler.postDelayed(this, delay);
            }
        };

        // Bắt đầu timer
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
        // Giải phóng MediaPlayer khi Activity bị hủy
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

        float x = 0f; // translationX
        float speed = 0f; // px/s
        float baseSpeed = 0f; // mỗi vịt khác nhau nhẹ
        boolean boosting = false;
        Runnable boostRunnable;

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

            // Khởi động lại animation vịt
            startAnimation();
        }

        void startAnimation() {
            AnimationDrawable animation = (AnimationDrawable) duck.getDrawable();
            if (animation != null) {
                animation.start();
            }
        }

        void stopAnimation() {
            AnimationDrawable animation = (AnimationDrawable) duck.getDrawable();
            if (animation != null) {
                animation.stop();
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
        DocumentReference
                ref = db.collection("users").document(uid);
        // bỏ listener cũ nếu có
        if (userReg != null) userReg.remove();

        userReg = ref.addSnapshotListener((snap, e) -> {
            if (e != null || snap == null || !snap.exists()) return;
            String name = snap.getString("displayName");
            Long coins  = snap.getLong("coins");
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

    private void showBetDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_bet, null);
        LinearLayout betContainer = view.findViewById(R.id.betContainer);

        // Sinh UI theo số vịt
        for (DuckRunner r : runners) {
            View item = LayoutInflater.from(this).inflate(R.layout.item_bet, betContainer, false);

            CheckBox checkBox = item.findViewById(R.id.chkDuck);
            EditText edtAmount = item.findViewById(R.id.edtAmount);

            checkBox.setText(r.name);

            betContainer.addView(item);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        view.findViewById(R.id.btnConfirmBet).setOnClickListener(v -> {
            currentBets.clear();
            int totalBet = 0;

            // Gom danh sách cược
            for (int i = 0; i < betContainer.getChildCount(); i++) {
                View item = betContainer.getChildAt(i);
                CheckBox chk = item.findViewById(R.id.chkDuck);
                EditText edt = item.findViewById(R.id.edtAmount);

                if (chk.isChecked()) {
                    int amount = 0;
                    try { amount = Integer.parseInt(edt.getText().toString()); } catch (Exception ignored) {}
                    if (amount > 0) {
                        Bet bet = new Bet();
                        bet.duckName = chk.getText().toString();
                        bet.amount = amount;
                        currentBets.add(bet);
                        totalBet += amount;
                    }
                }
            }

            // Nếu không đặt cược thì vẫn đóng dialog, không báo lỗi
            if (currentBets.isEmpty()) {
                dialog.dismiss();
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
                                    Toast.makeText(this, "Bạn không còn xu để đặt cược!", Toast.LENGTH_SHORT).show();
                                    currentBets.clear();
                                } else if (finalTotalBet > currentCoins) {
                                    Toast.makeText(this, "Bạn chỉ có " + currentCoins + " xu, không thể đặt " + finalTotalBet, Toast.LENGTH_SHORT).show();
                                    currentBets.clear();
                                } else {
                                    // Đủ tiền → trừ xu
                                    db.collection("users").document(user.getUid())
                                            .update("coins", FieldValue.increment(-finalTotalBet))
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(this, "Bạn đã đặt " + finalTotalBet + " xu!", Toast.LENGTH_SHORT).show();
                                            });

                                    btnBet.setEnabled(false);
                                    dialog.dismiss();
                                }
                            }
                        });
            }
        });

        dialog.show();
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

}
package com.example.duckrace;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.duckrace.services.MusicService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGoRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable full screen mode - edge to edge
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Make status bar transparent
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        // Nếu đã đăng nhập -> vào Main luôn
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            goMain();
            return;
        }

        startService(new Intent(this, MusicService.class));

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoRegister = findViewById(R.id.tvGoRegister);

        btnLogin.setOnClickListener(v -> signIn());
        tvGoRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));

        // Hide system UI when user interacts with the screen
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                // System bars are visible, hide them again
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        });
    }

    private void signIn() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
            toast("Nhập email & mật khẩu");
            return;
        }

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    toast("Đăng nhập thành công");
                    goMain();
                })
                .addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void goMain() {
        stopService(new Intent(this, MusicService.class));
        startActivity(new Intent(this, MainActivity.class));
        finish(); // không quay lại login khi bấm back
    }

    @Override
    public void onBackPressed() {
        // Người dùng thoát app từ màn Login -> dừng nhạc
        stopService(new Intent(this, MusicService.class));
        super.onBackPressed();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}

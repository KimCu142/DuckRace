package com.example.duckrace;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText etEmail, etPassword, etDisplayName;
    private Button btnRegister;
    private TextView tvGoLogin;

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

        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            goMain();
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etDisplayName = findViewById(R.id.etDisplayName);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoLogin = findViewById(R.id.tvGoLogin);

        btnRegister.setOnClickListener(v -> doRegister());
        tvGoLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));

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

    private void doRegister() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        String name = etDisplayName.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
            toast("Nhập email & mật khẩu");
            return;
        }
        if (pass.length() < 6) {
            toast("Mật khẩu tối thiểu 6 ký tự");
            return;
        }
        if (TextUtils.isEmpty(name))
            name = "Người chơi mới";
        final String displayName = TextUtils.isEmpty(name) ? "Người chơi mới" : name;
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> {
                    FirebaseUser user = r.getUser();
                    if (user == null) {
                        toast("Không lấy được user");
                        return;
                    }
                    // Tạo hồ sơ ban đầu trong Firestore
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("email", email);
                    profile.put("displayName", displayName);
                    profile.put("coins", 100);
                    profile.put("createdAt", FieldValue.serverTimestamp());
                    profile.put("updatedAt", FieldValue.serverTimestamp());

                    db.collection("users").document(user.getUid())
                            .set(profile)
                            .addOnSuccessListener(v -> {
                                toast("Đăng ký thành công!");
                                goMain();
                            })
                            .addOnFailureListener(e -> toast("Lỗi lưu Firestore: " + e.getMessage()));
                })
                .addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}

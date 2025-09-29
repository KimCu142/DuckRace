package com.example.duckrace;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGoRegister;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        // Nếu đã đăng nhập -> vào Main luôn
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) { goMain(); return; }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoRegister = findViewById(R.id.tvGoRegister);

        btnLogin.setOnClickListener(v -> signIn());
        tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void signIn() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
            toast("Nhập email & mật khẩu"); return;
        }

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> { toast("Đăng nhập thành công"); goMain(); })
                .addOnFailureListener(e -> toast(e.getMessage()));
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish(); // không quay lại login khi bấm back
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}

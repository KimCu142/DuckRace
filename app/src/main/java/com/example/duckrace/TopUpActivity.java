package com.example.duckrace;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.EditText;
import androidx.activity.OnBackPressedCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class TopUpActivity extends AppCompatActivity {

    private EditText etTopupAmount;
    private TextView tvCurrentBalance;
    private ImageButton btnBack;
    private Button btnTopUp;
    private Button btnQuick100, btnQuick500, btnQuick1000, btnQuick2000, btnQuick5000, btnQuick10000;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration userReg;

    private long currentCoins = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topup);

        // Khởi tạo views
        initViews();

        // Khởi tạo Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Kiểm tra user đã đăng nhập
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login to top up coins!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup realtime listener cho balance
        setupUserRealtime(currentUser.getUid());

        // Setup click listeners
        setupClickListeners();

        // Setup back pressed dispatcher
        setupBackPressedHandler();
    }

    private void initViews() {
        etTopupAmount = findViewById(R.id.etTopupAmount);
        tvCurrentBalance = findViewById(R.id.tvCurrentBalance);
        btnBack = findViewById(R.id.btnBack);
        btnTopUp = findViewById(R.id.btnTopUp);
        btnQuick100 = findViewById(R.id.btnQuick100);
        btnQuick500 = findViewById(R.id.btnQuick500);
        btnQuick1000 = findViewById(R.id.btnQuick1000);
        btnQuick2000 = findViewById(R.id.btnQuick2000);
        btnQuick5000 = findViewById(R.id.btnQuick5000);
        btnQuick10000 = findViewById(R.id.btnQuick10000);
    }

    private void setupClickListeners() {
        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Quick amount buttons
        btnQuick100.setOnClickListener(v -> setQuickAmount(100));
        btnQuick500.setOnClickListener(v -> setQuickAmount(500));
        btnQuick1000.setOnClickListener(v -> setQuickAmount(1000));
        btnQuick2000.setOnClickListener(v -> setQuickAmount(2000));
        btnQuick5000.setOnClickListener(v -> setQuickAmount(5000));
        btnQuick10000.setOnClickListener(v -> setQuickAmount(10000));

        // Top up button
        btnTopUp.setOnClickListener(v -> processTopUp());
    }

    private void setQuickAmount(int amount) {
        etTopupAmount.setText(String.valueOf(amount));
    }

    private void setupUserRealtime(String uid) {
        DocumentReference userRef = db.collection("users").document(uid);
        
        // Remove old listener if exists
        if (userReg != null) {
            userReg.remove();
        }

        userReg = userRef.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null || !snapshot.exists()) {
                return;
            }

            Long coins = snapshot.getLong("coins");
            currentCoins = (coins != null) ? coins : 0;
            tvCurrentBalance.setText(String.valueOf(currentCoins));
        });
    }

    private void processTopUp() {
        String amountStr = etTopupAmount.getText().toString().trim();
        
        // Validate input
        if (!isValidAmount(amountStr)) {
            return;
        }

        long topupAmount = Long.parseLong(amountStr);
        
        // Show confirmation dialog
        showConfirmationDialog(topupAmount);
    }

    private boolean isValidAmount(String amountStr) {
        // Check empty
        if (TextUtils.isEmpty(amountStr)) {
            showError("Please enter amount to top up!");
            return false;
        }

        // Check if it's a number
        try {
            long amount = Long.parseLong(amountStr);
            
            // Check negative number
            if (amount <= 0) {
                showError("Amount must be greater than 0!");
                return false;
            }

            // Check too large number (prevent overflow)
            if (amount > 1000000) {
                showError("Maximum amount is 1,000,000!");
                return false;
            }

            return true;

        } catch (NumberFormatException e) {
            showError("Please enter a valid number!");
            return false;
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        etTopupAmount.requestFocus();
    }

    private void showConfirmationDialog(long amount) {
        String message = String.format("Are you sure you want to top up %,d coins?\n\n" +
                        "Current balance: %,d coins\n" +
                        "Balance after top up: %,d coins", 
                        amount, currentCoins, currentCoins + amount);

        new AlertDialog.Builder(this)
                .setTitle("Confirm Top Up")
                .setMessage(message)
                .setIcon(R.drawable.ic_coin)
                .setPositiveButton("Yes, Top Up", (dialog, which) -> {
                    performTopUp(amount);
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void performTopUp(long amount) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User authentication error!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent multiple clicks
        btnTopUp.setEnabled(false);
        btnTopUp.setText("Processing...");

        DocumentReference userRef = db.collection("users").document(currentUser.getUid());
        
        userRef.update("coins", FieldValue.increment(amount))
                .addOnSuccessListener(aVoid -> {
                    // Success
                    showSuccessDialog(amount);
                    etTopupAmount.setText(""); // Clear input
                })
                .addOnFailureListener(e -> {
                    // Error
                    Toast.makeText(TopUpActivity.this, 
                            "Top up error: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                })
                .addOnCompleteListener(task -> {
                    // Re-enable button
                    btnTopUp.setEnabled(true);
                    btnTopUp.setText("🪙 TOP UP NOW");
                });
    }

    private void showSuccessDialog(long amount) {
        String message = String.format("🎉 Top up successful!\n\n" +
                        "Coins added: %,d coins\n" +
                        "New balance: %,d coins\n\n" +
                        "Good luck in your races!", 
                        amount, currentCoins + amount);

        new AlertDialog.Builder(this)
                .setTitle("Top Up Successful!")
                .setMessage(message)
                .setIcon(R.drawable.ic_star)
                .setPositiveButton("Back to Home", (dialog, which) -> {
                    finish(); // Go back to MainActivity
                })
                .setNegativeButton("Top Up More", null)
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove listener to prevent memory leaks
        if (userReg != null) {
            userReg.remove();
        }
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Custom back behavior - confirm if user wants to leave
                if (!TextUtils.isEmpty(etTopupAmount.getText().toString().trim())) {
                    new AlertDialog.Builder(TopUpActivity.this)
                            .setTitle("Exit Top Up")
                            .setMessage("Are you sure you want to exit? The entered amount will be lost.")
                            .setPositiveButton("Exit", (dialog, which) -> finish())
                            .setNegativeButton("Stay", null)
                            .show();
                } else {
                    finish();
                }
            }
        });
    }
}
package com.example.duckrace;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BetResultWinActivity extends AppCompatActivity {

    private MediaPlayer winSound;
    private LinearLayout duckRankingContainer;
    private LinearLayout leftColumn;
    private LinearLayout rightColumn;
    private TextView tvVictory, tvTotalWin;
    private Button btnContinue;

    private List<DuckResult> duckResults = new ArrayList<>();
    private String winnerName;
    private int winAmount;
    private Bet[] userBets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bet_result_win);

        // Get data from intent
        Intent intent = getIntent();
        winnerName = intent.getStringExtra("duck");
        winAmount = intent.getIntExtra("amount", 0);
        String betDataString = intent.getStringExtra("betData");

        // Parse bet data
        userBets = parseBetData(betDataString);

        // Initialize views
        initViews();

        // Create mock duck results for ranking
        createDuckResults();

        // Setup UI
        setupUI();

        // Play win sound
        playWinSound();

    }

    private void initViews() {
        tvVictory = findViewById(R.id.tvVictory);
        tvTotalWin = findViewById(R.id.tvTotalWin);
        duckRankingContainer = findViewById(R.id.duckRankingContainer);
        leftColumn = findViewById(R.id.leftColumn);
        rightColumn = findViewById(R.id.rightColumn);
        btnContinue = findViewById(R.id.btnContinue);

        btnContinue.setOnClickListener(v -> {
            finish();
        });
    }

    private void createDuckResults() {
        // Create results for all ducks with realistic ranking
        String[] duckNames = { "Vịt 1", "Vịt 2", "Vịt 3", "Vịt 4", "Vịt 5", "Vịt 6" };
        int[] duckIcons = { R.drawable.duck_run, R.drawable.duck_run, R.drawable.duck_run,
                R.drawable.duck_run, R.drawable.duck_run, R.drawable.duck_run };

        // Show up to 5 ducks (1-3 left, 4-5 right)
        int maxDucks = Math.min(duckNames.length, 5);
        for (int i = 0; i < maxDucks; i++) {
            DuckResult result = new DuckResult();
            result.name = duckNames[i];
            result.icon = duckIcons[i];
            result.isWinner = duckNames[i].equals(winnerName);

            // Find bet amount for this duck
            result.betAmount = 0;
            if (userBets != null) {
                for (Bet bet : userBets) {
                    if (bet.duckName.equals(duckNames[i])) {
                        result.betAmount = bet.amount;
                        break;
                    }
                }
            }

            result.rank = i + 1; // Will be updated after sorting
            duckResults.add(result);
        }

        // Sort by winner first, then by bet amount (higher bets first)
        Collections.sort(duckResults, (a, b) -> {
            if (a.isWinner && !b.isWinner)
                return -1;
            if (!a.isWinner && b.isWinner)
                return 1;
            return Integer.compare(b.betAmount, a.betAmount);
        });

        // Update ranks after sorting
        for (int i = 0; i < duckResults.size(); i++) {
            duckResults.get(i).rank = i + 1;
        }
    }

    private void setupUI() {
        tvVictory.setText("You won the bet.");
        tvTotalWin.setText("Tổng thắng: +" + winAmount + " xu");

        // Create duck ranking rows: 1-3 on left, 4-6 on right
        for (int i = 0; i < duckResults.size(); i++) {
            DuckResult result = duckResults.get(i);
            LinearLayout parent = (i < 3) ? leftColumn : rightColumn;
            View rowView = createDuckRow(result, i + 1, parent);
            parent.addView(rowView);
        }
    }

    private View createDuckRow(DuckResult result, int position, LinearLayout parent) {
        View rowView = getLayoutInflater().inflate(R.layout.item_duck_result, parent, false);

        ImageView imgDuck = rowView.findViewById(R.id.imgDuck);
        TextView tvDuckName = rowView.findViewById(R.id.tvDuckName);
        TextView tvDuckRank = rowView.findViewById(R.id.tvDuckRank);
        TextView tvBetAmount = rowView.findViewById(R.id.tvBetAmount);
        View mvpBadge = rowView.findViewById(R.id.mvpBadge);

        // Set duck icon
        imgDuck.setImageResource(result.icon);

        // Set duck name
        tvDuckName.setText(result.name);

        // Set rank
        tvDuckRank.setText(result.rank + ".");

        // Set bet amount only
        tvBetAmount.setText(result.betAmount + " xu");

        // Highlight winner
        if (result.isWinner) {
            rowView.setBackgroundColor(getResources().getColor(R.color.winner_background, null));
            mvpBadge.setVisibility(View.VISIBLE);
        } else {
            rowView.setBackgroundColor(getResources().getColor(R.color.loser_background, null));
            mvpBadge.setVisibility(View.GONE);
        }

        return rowView;
    }

    private void playWinSound() {
        try {
            winSound = MediaPlayer.create(this, R.raw.win);
            if (winSound != null) {
                winSound.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (winSound != null) {
            winSound.release();
            winSound = null;
        }
    }

    private static class Bet implements java.io.Serializable {
        String duckName;
        int amount;
    }

    private Bet[] parseBetData(String betDataString) {
        if (betDataString == null || betDataString.isEmpty()) {
            return new Bet[0];
        }

        List<Bet> bets = new ArrayList<>();
        String[] betEntries = betDataString.split(",");

        for (String entry : betEntries) {
            if (!entry.isEmpty()) {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    Bet bet = new Bet();
                    bet.duckName = parts[0];
                    bet.amount = Integer.parseInt(parts[1]);
                    bets.add(bet);
                }
            }
        }

        return bets.toArray(new Bet[0]);
    }

    private static class DuckResult {
        String name;
        int icon;
        boolean isWinner;
        int betAmount;
        int rank;
    }
}

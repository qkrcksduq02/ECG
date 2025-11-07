package com.example.ecg;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class ScoreActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_score);

        TextView myScoreTextView = findViewById(R.id.myScoreTextView);
        TextView partnerScoreTextView = findViewById(R.id.partnerScoreTextView);

        Random random = new Random();
        int myScore = random.nextInt(100) + 1;
        int partnerScore = random.nextInt(100) + 1;

        myScoreTextView.setText("나: " + myScore);
        partnerScoreTextView.setText("상대: " + partnerScore);

        Button restartButton = findViewById(R.id.restartButton);
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ScoreActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });
    }
}
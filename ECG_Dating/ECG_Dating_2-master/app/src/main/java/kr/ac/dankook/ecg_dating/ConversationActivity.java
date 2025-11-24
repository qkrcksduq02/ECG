package kr.ac.dankook.ecg_dating;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

public class ConversationActivity extends AppCompatActivity {

    private Chronometer timerConversation;
    private Button btnQuestion, btnBalanceGame, btnIdealType;
    private Button btnEndDating;
    private TextView tvBpm;

    // Bluetooth
    private static final String TAG = "ConversationActivity";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private ConnectedThread connectedThread;
    private Handler handler;
    private final static int MESSAGE_READ = 2;
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Standard SPP UUID

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Initialize UI
        initUI();
        tvBpm.setText("BPM: 초기화"); // Set initial text for debugging

        // Start timer
        timerConversation.setBase(SystemClock.elapsedRealtime());
        timerConversation.start();

        // Get device address from MainActivity
        String address = getIntent().getStringExtra("BT_DEVICE_ADDRESS");
        if (address == null) {
            Toast.makeText(this, "블루투스 장치 주소를 받지 못했습니다.", Toast.LENGTH_SHORT).show();
            tvBpm.setText("오류: 장치 없음");
            finish();
            return;
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

        // Handler for incoming messages from Bluetooth thread
        handler = new Handler(Looper.getMainLooper(), msg -> {
            if (msg.what == MESSAGE_READ) {
                String readMessage = (String) msg.obj;
                if (readMessage == null) return true;

                String trimmedMessage = readMessage.trim();
                Log.d(TAG, "Raw BT Data: \"" + trimmedMessage + "\"");
                if (trimmedMessage.isEmpty()) return true;

                if (trimmedMessage.contains("전극")) {
                    tvBpm.setText(trimmedMessage);
                } else {
                    String digitsOnly = trimmedMessage.replaceAll("[^0-9]", "");
                    if (!digitsOnly.isEmpty()) {
                        tvBpm.setText("BPM: " + digitsOnly);
                    }
                }
            }
            return true;
        });

        connectToDeviceInBackground(device);
        setupButtonListeners();
    }

    private void initUI() {
        timerConversation = findViewById(R.id.timer_conversation);
        btnQuestion = findViewById(R.id.btn_question);
        btnBalanceGame = findViewById(R.id.btn_balance_game);
        btnIdealType = findViewById(R.id.btn_ideal_type);
        btnEndDating = findViewById(R.id.btn_end_dating);
        tvBpm = findViewById(R.id.tv_bpm);
    }

    @SuppressLint("MissingPermission")
    private void connectToDeviceInBackground(BluetoothDevice device) {
        // Update UI to show connection is in progress
        runOnUiThread(() -> tvBpm.setText("BPM: 연결 중..."));

        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
                bluetoothSocket.connect(); // This is a blocking call

                connectedThread = new ConnectedThread(bluetoothSocket);
                connectedThread.start();

                // Update UI on successful connection
                runOnUiThread(() -> {
                    Toast.makeText(ConversationActivity.this, "블루투스 연결 성공!", Toast.LENGTH_SHORT).show();
                    tvBpm.setText("BPM: 수신 대기 중...");
                });

            } catch (IOException e) {
                Log.e(TAG, "소켓 연결 실패", e);

                // Update UI on connection failure
                runOnUiThread(() -> {
                    Toast.makeText(ConversationActivity.this, "블루투스 연결에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    tvBpm.setText("BPM: 연결 실패");
                });

                try {
                    if (bluetoothSocket != null) {
                        bluetoothSocket.close();
                    }
                } catch (IOException closeException) {
                    Log.e(TAG, "소켓 닫기 실패", closeException);
                }
            }
        }).start();
    }

    private void setupButtonListeners() {
        btnQuestion.setOnClickListener(v -> startActivity(new Intent(ConversationActivity.this, QuestionListActivity.class)));
        btnIdealType.setOnClickListener(v -> startActivity(new Intent(ConversationActivity.this, ChoiceActivity.class)));
        btnBalanceGame.setOnClickListener(v -> startActivity(new Intent(ConversationActivity.this, Balancegame.class)));
        btnEndDating.setOnClickListener(v -> {
            timerConversation.stop();
            showEyeContactPopup(timerConversation.getText().toString());
        });
    }

    private void showEyeContactPopup(String duration) {
        LayoutInflater inflater = getLayoutInflater();
        View popupView = inflater.inflate(R.layout.popup_eye_contact, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(popupView)
                .setCancelable(false)
                .create();
        dialog.show();

        TextView tvCountdown = popupView.findViewById(R.id.tv_countdown);

        new CountDownTimer(10000, 1000) {
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText(String.valueOf(millisUntilFinished / 1000 + 1));
            }

            public void onFinish() {
                dialog.dismiss();
                Intent intent = new Intent(ConversationActivity.this, ResultActivity.class);
                intent.putExtra("DURATION", duration);
                intent.putExtra("VIBRATION_MALE", 0); // Placeholder
                intent.putExtra("VIBRATION_FEMALE", 0); // Placeholder
                startActivity(intent);
                finish();
            }
        }.start();
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final BufferedReader mmBufferedReader;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            mmInStream = tmpIn;
            mmBufferedReader = new BufferedReader(new InputStreamReader(mmInStream));
        }

        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String line = mmBufferedReader.readLine();
                    if (line != null) {
                        handler.obtainMessage(MESSAGE_READ, line).sendToTarget();
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        public void cancel() {
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerConversation.stop();
        if (connectedThread != null) {
            connectedThread.interrupt(); // Use interrupt to stop the thread
            connectedThread.cancel();    // Close the socket
        }
    }
}

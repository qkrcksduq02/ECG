package kr.ac.dankook.ecg_dating;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private Button btnShowPopup;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice selectedDevice;
    private Button btnStartDating; // Member variable to be accessed from different methods

    // ActivityResultLauncher for permissions
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean scanGranted = result.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false);
                Boolean connectGranted = result.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false);

                if (scanGranted != null && scanGranted && connectGranted != null && connectGranted) {
                    // Permissions granted, proceed with Bluetooth logic
                    checkAndEnableBluetooth();
                } else {
                    // Permissions denied
                    Toast.makeText(this, "블루투스 권한이 거부되었습니다. 앱을 사용하려면 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                }
            });

    // ActivityResultLauncher for enabling Bluetooth
    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Bluetooth is enabled, now show device list
                    selectDevice();
                } else {
                    Toast.makeText(this, "블루투스를 활성화해야 합니다.", Toast.LENGTH_SHORT).show();
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "이 기기는 블루투스를 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnShowPopup = findViewById(R.id.btn_show_popup);

        btnShowPopup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConnectPopup();
            }
        });
    }

    private void showConnectPopup() {
        LayoutInflater inflater = getLayoutInflater();
        View popupView = inflater.inflate(R.layout.popup_connect, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(popupView);

        Button btnConnectDevice = popupView.findViewById(R.id.btn_connect_device);
        btnStartDating = popupView.findViewById(R.id.btn_start_dating); // Initialize member variable

        AlertDialog dialog = builder.create();

        btnConnectDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start Bluetooth connection logic
                startBluetoothConnectionProcess();
            }
        });

        btnStartDating.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedDevice != null) {
                    Toast.makeText(MainActivity.this, "소개팅을 시작합니다!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, ConversationActivity.class);
                    // Pass the selected device's address to the next activity
                    intent.putExtra("BT_DEVICE_ADDRESS", selectedDevice.getAddress());
                    startActivity(intent);
                    dialog.dismiss();
                } else {
                    Toast.makeText(MainActivity.this, "먼저 ECG 장치를 연결해주세요.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.show();
    }

    private void startBluetoothConnectionProcess() {
        // 1. Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                });
                return; // Wait for permission result
            }
        }
        // For older versions, permissions are in manifest and granted at install time.

        // Permissions are granted, proceed to enable Bluetooth
        checkAndEnableBluetooth();
    }

    private void checkAndEnableBluetooth() {
        // 2. Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBtIntent);
        } else {
            // 3. If already enabled, show paired devices
            selectDevice();
        }
    }

    @SuppressLint("MissingPermission") // Permissions are checked in startBluetoothConnectionProcess
    private void selectDevice() {
        // 4. Get paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> deviceList = new ArrayList<>();
        ArrayList<BluetoothDevice> devices = new ArrayList<>();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device.getName() + "\n" + device.getAddress());
                devices.add(device);
            }
        } else {
            Toast.makeText(this, "페어링된 기기가 없습니다. 먼저 블루투스 설정에서 기기를 페어링해주세요.", Toast.LENGTH_LONG).show();
            return;
        }

        // 5. Show device list in a dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("연결할 기기 선택");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        builder.setAdapter(adapter, (dialog, which) -> {
            // 6. When a device is selected
            selectedDevice = devices.get(which);

            // For now, we just confirm the selection.
            // The actual socket connection should be done here in a background thread.
            // For this step, we will just simulate a successful connection.
            Toast.makeText(MainActivity.this, selectedDevice.getName() + " 선택됨. 연결 시도...", Toast.LENGTH_SHORT).show();

            // Simulate connection success and enable the start button
            btnStartDating.setEnabled(true);
            Toast.makeText(MainActivity.this, "연결 성공!", Toast.LENGTH_SHORT).show();

            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
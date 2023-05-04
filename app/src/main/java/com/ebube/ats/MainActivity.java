package com.ebube.ats;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.poovam.pinedittextfield.CirclePinField;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {
    public static BluetoothSocket mmSocket;
    private final String ADMIN_KEY = "134578";
    private Timer connectionTimer;
    public static ConnectedThread connectedThread;
    public static CreateConnectThread createConnectThread;

    // UI Initialization
    private ImageView gridBulb;
    private ImageView solarBulb;
    private ImageView genBulb;
    private ImageView lockGen;
    private ImageView lockAutoStart;
    private TextView current;
    private TextView voltage;
    private TextView power;
    private TextView waterLevel;
    private TextView genStatus;
    private TextView oilLevel;
    private TextView bluetoothStatus;
    private SwitchMaterial autoStartGen;
    private SwitchMaterial toggleGen;
    private boolean isAdmin;
    private boolean isConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // UI Initialization
        gridBulb = findViewById(R.id.gridBulb);
        solarBulb = findViewById(R.id.solarBulb);
        genBulb = findViewById(R.id.genBulb);
        current = findViewById(R.id.current);
        voltage = findViewById(R.id.voltage);
        power = findViewById(R.id.power);
        waterLevel = findViewById(R.id.waterLevel);
        oilLevel = findViewById(R.id.oilLevel);
        bluetoothStatus = findViewById(R.id.bluetoothStatus);
        autoStartGen = findViewById(R.id.autoStartGen);
        toggleGen = findViewById(R.id.toggleGen);
        genStatus = findViewById(R.id.genStatus);
        lockGen = findViewById(R.id.toggleGenLock);
        lockAutoStart = findViewById(R.id.autoStartLock);

        lockGen.setOnClickListener((e) -> getAdminCredentials());
        lockAutoStart.setOnClickListener((e) -> getAdminCredentials());

        autoStartGen.setOnCheckedChangeListener((button, isChecked) -> {
            if (isAdmin) {
                if (connectedThread != null) {
                    connectedThread.write(isChecked ? "1" : "0");
                }
            } else {
                getAdminCredentials();
                autoStartGen.setChecked(true);
            }
        });

        toggleGen.setOnCheckedChangeListener((button, isChecked) -> {
            if (isAdmin) {
                if (connectedThread != null) {
                    connectedThread.write(isChecked ? "2" : "3");
                    genStatus.setText(isChecked ? R.string.turn_off_gen : R.string.turn_on_gen);
                }
            } else {
                getAdminCredentials();
                toggleGen.setChecked(!isChecked);
            }
        });

        createConnectThread = new CreateConnectThread(this);

        connectionTimer = new Timer();
        connectionTimer.schedule(createConnectThread, 1000L, 30000L);
    }


    private void getAdminCredentials() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.modal_activity, null);
        CirclePinField circlePinField = view.findViewById(R.id.circleField);
        dialogBuilder.setView(view);


        dialogBuilder.setCancelable(false);
        AlertDialog dialog = dialogBuilder.create();
        circlePinField.setOnTextCompleteListener((text) -> {
            if (ADMIN_KEY.equals(text)) {
                isAdmin = true;
                lockGen.setVisibility(View.INVISIBLE);
                lockAutoStart.setVisibility(View.INVISIBLE);
                toggleGen.setEnabled(true);
                autoStartGen.setEnabled(true);
            } else {
                toggleGen.setEnabled(false);
                autoStartGen.setEnabled(false);
                lockGen.setVisibility(View.VISIBLE);
                lockAutoStart.setVisibility(View.VISIBLE);
            }
            dialog.dismiss();
            return true;
        });
        dialog.show();
        circlePinField.requestFocus();
    }

    /* ============================ Thread to Create Bluetooth Connection =================================== */
    private class CreateConnectThread extends TimerTask {
        private final MainActivity activity;

        public CreateConnectThread(MainActivity activity) {
            this.activity = activity;
        }

        public void run() {
            if (isConnected) {
                return;
            }
            runOnUiThread(() -> bluetoothStatus.setText(R.string.connecting));
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH}, 3);
            } else {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, 2);
                    return;
                }
                String DEVICE_ADDRESS = "00:22:06:01:25:5B";
                BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
                bluetoothAdapter.cancelDiscovery();
                try {

                    UUID uuid = bluetoothDevice.getUuids()[0].getUuid();
                    mmSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
                    mmSocket.connect();
                    Log.e("Status", "Device connected");
                    isConnected = true;
                    runOnUiThread(() -> bluetoothStatus.setText(R.string.connected));
                } catch (IOException connectException) {
                    // Unable to connect; close the socket and return.
                    isConnected = false;
                    runOnUiThread(() -> bluetoothStatus.setText(R.string.not_connected));
                    try {
                        mmSocket.close();
                        Log.e("Status", "Cannot connect to device");
                    } catch (IOException closeException) {
                        Log.e(TAG, "Could not close the client socket", closeException);
                    }
                    return;
                }

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                connectedThread = new ConnectedThread(mmSocket);
                connectedThread.start();
            }
        }

        // Closes the client socket and causes the thread to finish.
        public boolean cancel() {
            try {
                mmSocket.close();
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
            return false;
        }
    }

    /* =============================== Thread for Data Transfer =========================================== */
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Could not get input and output stream of the client socket", e);
                isConnected = false;
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes = 0; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    /*
                    Read from the InputStream from Arduino until termination character is reached.
                    Then send the whole String message to GUI Handler.
                     */
                    buffer[bytes] = (byte) mmInStream.read();
                    String readMessage;
                    if (buffer[bytes] == '\n') {
                        readMessage = new String(buffer, 0, bytes);
                        updateUI(readMessage);
                        Log.e("Arduino Message", readMessage);
                        bytes = 0;
                    } else {
                        bytes++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        public void updateUI(String message) {
            runOnUiThread(() -> {
                switch (message) {
                    case "grid is on":
                        tripBulbs();
                        gridBulb.setImageResource(R.drawable.bulb_alive);
                        return;
                    case "pv is on":
                        tripBulbs();
                        solarBulb.setImageResource(R.drawable.bulb_alive);
                        return;
                    case "gen is on":
                        tripBulbs();
                        genBulb.setImageResource(R.drawable.bulb_alive);
                        if (toggleGen.isEnabled()) {
                            toggleGen.setChecked(true);
                        }
                        genStatus.setText(R.string.turn_off_gen);
                        return;
                }
                if (message.contains("water lever = ")) {
                    waterLevel.setText(splitAndReturnValue(message));
                } else if (message.contains("oil level = ")) {
                    oilLevel.setText(splitAndReturnValue(message));
                } else if (message.contains("current = ")) {
                    current.setText(splitAndReturnValue(message));
                } else if (message.contains("voltage = ")) {
                    voltage.setText(splitAndReturnValue(message));
                } else if (message.contains("power = ")) {
                    power.setText(splitAndReturnValue(message));
                }
            });
        }

        public String splitAndReturnValue(String message) {
            return message.split("=")[1].trim();
        }

        public void tripBulbs() {
            gridBulb.setImageResource(R.drawable.bulb_dead);
            solarBulb.setImageResource(R.drawable.bulb_dead);
            genBulb.setImageResource(R.drawable.bulb_dead);
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes(); //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e("Send Error", "Unable to send message", e);
            }
        }

    }

    /* ============================ Terminate Connection at BackPress ====================== */
    @Override
    public void onBackPressed() {
        // Terminate Bluetooth Connection and close app
        if (connectionTimer != null) {
            connectionTimer.cancel();
            createConnectThread.cancel();
        }
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }
}
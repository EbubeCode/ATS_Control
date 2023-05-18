package com.ebube.ats;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.poovam.pinedittextfield.LinePinField;

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
    private final boolean[] ignoreSwitch = {false, false};

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
            if (ignoreSwitch[0]) {
                ignoreSwitch[0] = false;
                return;
            }
            if (isAdmin && connectedThread != null) {
                connectedThread.write(isChecked ? "1" : "0");
            }

        });

        toggleGen.setOnCheckedChangeListener((button, isChecked) -> {
            if (ignoreSwitch[1]) {
                ignoreSwitch[1] = false;
                return;
            }
            if (isAdmin && connectedThread != null) {
                connectedThread.write(isChecked ? "2" : "3");
                genStatus.setText(isChecked ? R.string.turn_off_gen : R.string.turn_on_gen);
            }
        });

        createConnectThread = new CreateConnectThread(this);

        connectionTimer = new Timer();
        connectionTimer.schedule(createConnectThread, 5000L, 30000L);
    }


    private void getAdminCredentials() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.modal_activity, null);
        LinePinField linePinField = view.findViewById(R.id.lineField);
        dialogBuilder.setView(view);


        dialogBuilder.setCancelable(false);
        AlertDialog dialog = dialogBuilder.create();
        linePinField.setOnTextCompleteListener((text) -> {
            if (ADMIN_KEY.equals(text)) {
                isAdmin = true;
                lockGen.setVisibility(View.INVISIBLE);
                lockAutoStart.setVisibility(View.INVISIBLE);
                toggleGen.setEnabled(true);
                autoStartGen.setEnabled(true);
            } else {
                isAdmin = false;
                toggleGen.setEnabled(false);
                autoStartGen.setEnabled(false);
                lockGen.setVisibility(View.VISIBLE);
                lockAutoStart.setVisibility(View.VISIBLE);
            }
            dialog.dismiss();
            return true;
        });
        dialog.show();
        linePinField.requestFocus();
    }

    /* ============================ Thread to Create Bluetooth Connection =================================== */
    private class CreateConnectThread extends TimerTask {
        private final MainActivity activity;

        public CreateConnectThread(MainActivity activity) {
            this.activity = activity;
        }

        public void run() {
            if (mmSocket != null && mmSocket.isConnected() && connectedThread != null) {
                connectedThread.ping();
                return;
            }
            runOnUiThread(MainActivity.this::returnFieldsToDefault);
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
                    runOnUiThread(() -> bluetoothStatus.setText(R.string.connected));
                } catch (IOException connectException) {
                    // Unable to connect; close the socket and return.
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
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[100];  // buffer store for the stream
            int bytes = 0; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    /*
                    Read from the InputStream from Arduino until termination character is reached.
                    Then send the whole String message to GUI Handler.
                     */
                    buffer[bytes] = (byte) mmInStream.read();
                    String readMessage = new String(buffer, 0, bytes);
                    Log.i(TAG, readMessage);
                    if (buffer[bytes] == '\n') {
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
                if (message.contains("grid is on")) {
                    tripBulbs();
                    gridBulb.setImageResource(R.drawable.bulb_alive);
                    genStatus.setText(R.string.turn_on_gen);
                } else if (message.contains("pv is on")) {
                    tripBulbs();
                    solarBulb.setImageResource(R.drawable.bulb_alive);
                    genStatus.setText(R.string.turn_on_gen);
                } else if (message.contains("gen is on")) {
                    tripBulbs();
                    genBulb.setImageResource(R.drawable.bulb_alive);
                    genStatus.setText(R.string.turn_off_gen);
                } else if (message.contains("water level = ")) {
                    waterLevel.setText(splitAndReturnValue(message));
                } else if (message.contains("oil level = ")) {
                    oilLevel.setText(splitAndReturnValue(message));
                } else if (message.contains("current = ")) {
                    current.setText(splitAndReturnValue(message));
                } else if (message.contains("voltage = ")) {
                    voltage.setText(splitAndReturnValue(message));
                } else if (message.contains("power = ")) {
                    power.setText(splitAndReturnValue(message));
                } else if (message.contains("switchGen = ")) {
                    genStatus.setText(splitAndReturnValue(message).equals("0") ? R.string.turn_off_gen : R.string.turn_on_gen);
                    if (toggleGen.isEnabled()) {
                        ignoreSwitch[1] = true;
                        toggleGen.setChecked(splitAndReturnValue(message).equals("0"));
                    }
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
                cancel();
            }
        }

        /**
         * ping the device to ensure the connection
         */
        public void ping() {
            write("p");
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                runOnUiThread(MainActivity.this::returnFieldsToDefault);
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }

    }

    /* ============================ Terminate Connection at BackPress ====================== */
    @Override
    public void onBackPressed() {
        // Terminate Bluetooth Connection and close app
        if (connectedThread != null) {
            connectedThread.cancel();
        }
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }

    public void returnFieldsToDefault() {
        gridBulb.setImageResource(R.drawable.bulb_dead);
        genBulb.setImageResource(R.drawable.bulb_dead);
        solarBulb.setImageResource(R.drawable.bulb_dead);
        current.setText(R.string.nil);
        voltage.setText(R.string.nil);
        power.setText(R.string.nil);
        oilLevel.setText(R.string.nil);
        waterLevel.setText(R.string.nil);
        toggleGen.setChecked(false);
        autoStartGen.setChecked(true);
        bluetoothStatus.setText(R.string.not_connected);
    }
}
package com.wbd101.hlt010;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.util.Log;
import android.content.Context;


import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class RfcommClientThread extends Thread {
    private final BluetoothDevice device;
    private final BluetoothSocket socket;
    private final String TAG = "RfcommClientThread";
    private static final int BUFFER_SIZE = 1024;
    private OutputStream outputStream;
    private InputStream inputStream;
    private static RfcommClientThread instance;
    private static final int REQUEST_PERMISSIONS = 4;
    private Context context;

    public static RfcommClientThread getInstance() {
        return instance;
    }

    public RfcommClientThread(Context context, BluetoothDevice device, String uuidString) {
        instance = this;
        this.device = device;
        BluetoothSocket tmp = null;
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
        } else {
            if (context instanceof Activity) {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQUEST_PERMISSIONS);
            } else {
                Log.e(TAG, "Context is not an Activity. Cannot request permissions.");
            }
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
        }
        Log.d(TAG, "RfcommClientThread constructor called.");
        try {
            // Use reflection to create an RFCOMM socket on channel 1
            Method method = device.getClass().getMethod("createRfcommSocket", int.class);
            tmp = (BluetoothSocket) method.invoke(device, 1); // Channel 1 is commonly used
            Log.d(TAG, "Socket created successfully using reflection.");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up socket with reflection", e);
            // Fall back to createRfcommSocketToServiceRecord() if reflection fails
            try {
                UUID uuid = UUID.fromString(uuidString);
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException ioException) {
                Log.e(TAG, "Socket's create() method failed", ioException);
            }
        }
        socket = tmp;

    }

    public void run() {
        Log.d(TAG, "RfcommClientThread running...");
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
        } else {
            if (context instanceof Activity) {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQUEST_PERMISSIONS);
            } else {
                Log.e(TAG, "Context is not an Activity. Cannot request permissions.");
            }
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
        }

        try {
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            Log.d(TAG, "Attempting to connect to ");
            socket.connect();
            Log.d(TAG, "Connected successfully");

            // Connection successful, handle I/O here
            manageConnectedSocket();

        } catch (IOException connectException) {
            Log.e(TAG, "Could not connect to ", connectException);
            try {
                socket.close();
                Log.d(TAG, "Socket closed after connection failure.");
            } catch (IOException closeException) {
                Log.e(TAG, "Could not close the client socket", closeException);
            }
        }
    }


    private void manageConnectedSocket() {

        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            Log.d(TAG, "Input and output streams initialized.");

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytes;

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = inputStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    String received = new String(buffer, 0, bytes);
                    Log.d(TAG, "Received: " + received);

                    // Here you can add code to send data back if needed
                    // outputStream.write(someData);

                } catch (IOException e) {
                    Log.e(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating streams", e);
        }
    }

    public void sendData(String data) {
        try{
            if (socket != null && socket.isConnected() && outputStream != null) {
                outputStream.write(data.getBytes());
                outputStream.flush();
                Log.d(TAG, "Sent data: " + data);
            } else {
                Log.e(TAG, "Output stream is null. Data not sent.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error sending data", e);
        }
    }

    // Call this method to cancel an in-progress connection
    public void cancel() {
        try {
            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }
}
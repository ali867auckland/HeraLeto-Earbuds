package com.wbd101.hlt010;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Handler;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Iterator;
import java.util.LinkedList;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothLeScanner bleScanner = null;
    private ScanCallback scanCallback;
    private scanResultAdapter scanAdapter;
    private LinkedList<ScanResult> scanResults = new LinkedList<>();

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private boolean isScanning = false;

    private Button scan_btn;
    private Button history_btn;
    private ActionBar actionBar;
    private CheckBox checkboxRaw;
    private TextView infoText, barTitle;

    private static boolean saveRawMain = false;  // For raw data logging
    private final static String TAG = "MainActivity.java";
    public static int langauge = 1;
    public static boolean showSpO2 = true;
    private static int wbdID = 7674;  // WBD's manuf  acturer ID (HEX = 1DFA)
    private int manufacturerID = 0;

    private ActivityResultLauncher<Intent> enableBtLauncher;

    private boolean hasAttemptedConnection = false;
    private final Handler advertisementHandler = new Handler(Looper.getMainLooper());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_PERMISSIONS);
        }
        // Set the page title:
        actionBar = getSupportActionBar();
        if (actionBar == null) {
            Log.e(TAG, "Null Error, MainAcitivity line 90");
        }
        if (showSpO2) {
            actionBar.setTitle(R.string.menu_title_1);
        } else {
            actionBar.setTitle(R.string.menu_title_hlt_1);
        }
        if(actionBar!=null){
            barTitle = new TextView(getApplicationContext());
            barTitle.setText(actionBar.getTitle());
            barTitle.setTextColor(Color.WHITE);
            barTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(barTitle);
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            enableBtLauncher.launch(enableBtIntent);
        }
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "bluetooth_not_supported", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "mBluetoothAdapter, MainActivity 122");
            finish();
        }
        history_btn = findViewById(R.id.NBO_view_history);
        if (history_btn == null){
            Log.e(TAG, "history_btn, MainActivity 126");
        }
        history_btn.setOnClickListener(v -> {
            Log.d(TAG, "onCreate: History button pressed.");
            Intent viewHistory = new Intent(this, ViewHistory.class);  // Get the next activity
            startActivity(viewHistory);                                 // Start the activity
        });
        scan_btn = findViewById(R.id.search_button);
        if (scan_btn == null){
            Log.e(TAG, "scan_btn, MainAcitivity line 135");
        }
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        if (recyclerView == null){
            Log.e(TAG, "recyclerView, MainAcitivity line 140");
        }
        scanAdapter = new scanResultAdapter(this, scanResults);
        recyclerView.setAdapter(scanAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        scan_btn.performClick();

        infoText = findViewById(R.id.info);
        checkboxRaw = findViewById(R.id.checkBox);
    }

    // Initialise Language Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_device_language, menu);
        return true;
    }

    // Language Menu Functions
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.ENG) {
            Log.d(TAG, "English selected.");
            langauge = 1;
            Toast.makeText(getApplicationContext(), "English selected.", Toast.LENGTH_SHORT).show();
            updateLanguage(langauge);
            return true;
        } else if (item.getItemId() == R.id.CNT) {
            Log.d(TAG, "Traditional Chinese selected.");
            Toast.makeText(getApplicationContext(), "Traditional Chinese selected.", Toast.LENGTH_SHORT).show();
            langauge = 2;
            updateLanguage(langauge);
            return true;
        } else if (item.getItemId() == R.id.CNS) {
            Log.d(TAG, "Simplified Chinese selected.");
            Toast.makeText(getApplicationContext(), "Simplified Chinese selected.", Toast.LENGTH_SHORT).show();
            langauge = 3;
            updateLanguage(langauge);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // UI Language updates
    public void updateLanguage(int val){
        switch (val){
            case 1:
                if (showSpO2) {
                    barTitle.setText(R.string.menu_title_1);
                } else {
                    barTitle.setText(R.string.menu_title_hlt_1);
                }
                infoText.setText(R.string.info_1);
                if (isScanning) {
                    scan_btn.setText(R.string.device_stop_search_1);
                } else {
                    scan_btn.setText(R.string.device_search_1);
                }
                history_btn.setText(R.string.history_1);
                checkboxRaw.setText(R.string.log_data_1);
                break;
            case 2:
                if (showSpO2) {
                    barTitle.setText(R.string.menu_title_2);
                } else {
                    barTitle.setText(R.string.menu_title_hlt_2);
                }
                infoText.setText(R.string.info_2);
                if (isScanning) {
                    scan_btn.setText(R.string.device_stop_search_2);
                } else {
                    scan_btn.setText(R.string.device_search_2);
                }
                history_btn.setText(R.string.history_2);
                checkboxRaw.setText(R.string.log_data_2);
                break;
            case 3:
                if (showSpO2) {
                    barTitle.setText(R.string.menu_title_3);
                } else {
                    barTitle.setText(R.string.menu_title_hlt_3);
                }
                infoText.setText(R.string.info_3);
                if (isScanning) {
                    scan_btn.setText(R.string.device_stop_search_3);
                } else {
                    scan_btn.setText(R.string.device_search_3);
                }
                history_btn.setText(R.string.history_3);
                checkboxRaw.setText(R.string.log_data_3);
                break;
        }
    }

    public static int getLanguage(){
        return langauge;
    }

    public static void setLanguage(int val){
        if ((val > 0)&&(val<4)) {
            langauge = val;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mBluetoothAdapter.isEnabled()) {
            final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            enableBtLauncher.launch(enableBtIntent);
            return;
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_PHONE_STATE)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSIONS);
        }

    }
    /**
    @Override
    protected void onPause() {
        if (bleScanner != null && scanCallback != null && isScanning) {
            stopScan();
            bleScanner = null;
        }
        super.onPause();
        Log.d("MainActivity", "onPause called");
    }
     **/


    @Override
    protected void onStop() {
        if (bleScanner != null && scanCallback != null && isScanning) {
            stopScan();
            bleScanner = null;
        }
        super.onStop();
        Log.d("MainActivity", "onStop called");
    }

    /**
    @Override
    protected void onDestroy() {
        if (bleScanner != null && scanCallback != null && isScanning) {
            stopScan();
            bleScanner = null;
        }
        super.onDestroy();Log.d("MainActivity", "onDestroy called");
        advertisementHandler.removeCallbacksAndMessages(null);
    }
    **/

    private void startScan(){
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "BluetoothAdapter is not available or not enabled.");
            return;
        }
        bleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Log.e(TAG, "Null Error, MainAcitvity 307");
            Log.d(TAG, "Null Error, MainAcitvity 307");
            Log.w(TAG, "Null Error, MainAcitvity 307");
        }
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "bluetooth_not_supported", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "mBluetoothAdapter, MainAcitvity line 312");
            Log.e(TAG, "mBluetoothAdapter, MainAcitvity line 312");
            Log.w(TAG, "mBluetoothAdapter, MainAcitvity line 312");
            finish();
        }
        ScanSettings scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
        scanAdapter.notifyDataSetChanged();
        scanCallback = new ScanCallback() {

            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                BluetoothDevice device = result.getDevice();
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
                } else {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.BLUETOOTH_SCAN},
                            REQUEST_PERMISSIONS);
                }
                String deviceName = device.getName();

                // Check the manufacturer ID:
                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord != null) {
                    SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
                    if (manufacturerData == null) {
                        Log.d(TAG, "manufacturerData");
                    }
                    for (int i = 0; i < manufacturerData.size(); i++) {
                        manufacturerID = manufacturerData.keyAt(i);
                        byte[] obj = manufacturerData.get(manufacturerID);
                        //Log.i("ScanRecord-MFData: ", "id: " + manufacturerID + " obj: " + bytesToHex(obj));
                    }
                }


                //                if(deviceName!=null && (deviceName.contains("HLT") ||
                //                                        deviceName.contains("Hera") ||
                //                                        deviceName.contains("OskronCare"))
                //                ) {
                //                if(device.getName()!=null) {

                if (manufacturerID == wbdID) {

                    Iterator<ScanResult> it = scanResults.iterator();
                    boolean present = false;
                    while (it.hasNext()) {
                        ScanResult item = it.next();
                        if (item.getDevice().getAddress().equals(result.getDevice().getAddress())) {
                            present = true;
                            int position = scanResults.indexOf(item);
                            scanResults.set(position, result);
                            scanAdapter.notifyDataSetChanged();
                        }
                    }
                    if (!present) {
                        //THis one!!!!!!!!!!!!!!!
                        Log.w("ScanCallback", "Found unique BLE device! Name : " + result.getDevice().getName() + " address: " + result.getDevice().getAddress());
                        //ScanRecord scanRecord = result.getScanRecord();
                        //                        if (scanRecord != null) {
                        //                            int flag = scanRecord.getAdvertiseFlags();
                        //                            String bytes = bytesToHex(scanRecord.getBytes());
                        //                            SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
                        //                            Log.i("ScanRecord-flag: ", " " + flag);
                        //                            Log.i("ScanRecord-bytes: ", bytes);
                        //                            for (int i = 0; i < manufacturerData.size(); i++) {
                        //                                int id = manufacturerData.keyAt(i);
                        //                                byte[] obj = manufacturerData.get(id);
                        //                                Log.i("ScanRecord-MFData: ", "id: " + id + " obj: " + bytesToHex(obj));
                        //                            }
                        //                        }
                        scanResults.add(result);
                        scanAdapter.notifyItemInserted(scanResults.size() - 1);
                    }
                    updateLanguage(getLanguage());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e("ScanCallback", "onScanFailed: code " + errorCode);
            }
        };
        scanResults.clear();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_PERMISSIONS);
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
        }
        if (bleScanner == null || scanCallback == null) {
            Log.e(TAG, "Null Error, MainActivity line 407");
            Log.w(TAG, "Null Error, MainActivity line 407");
            Log.d(TAG, "Null Error, MainActivity line 407");
        }
        bleScanner.startScan(null, scanSettings, scanCallback);
        isScanning = true;
    }
    private void stopScan(){
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "BluetoothAdapter is not available or not enabled.stopScan");
            return;
        }
        if (bleScanner == null || scanCallback == null) {
            Log.e(TAG, "stopScan");
            Log.d(TAG, "stopScan");
            Log.w(TAG, "stopScan");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_PERMISSIONS);
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
        }
        if (bleScanner == null || scanCallback == null) {
            Log.e(TAG, "bleScanner or scanCallback");
            Log.d(TAG, "bleScanner or scanCallback");
            Log.w(TAG, "bleScanner or scanCallback");
        }

        bleScanner.stopScan(scanCallback);
        isScanning = false;

        switch(langauge){
            case 1:
                scan_btn.setText(R.string.device_search_1);
                break;
            case 2:
                scan_btn.setText(R.string.device_search_2);
                break;
            case 3:
                scan_btn.setText(R.string.device_search_3);
                break;
        }
        saveRawMain = checkboxRaw.isChecked();
    }

    public void button_function(View v) {
        if(isScanning){
            stopScan();
        }
        else{
            startScan();
            if (bleScanner == null) {
                Log.e(TAG, "BleScanner button_function");
                Log.w(TAG, "BleScanner button_function");
                Log.d(TAG, "BleScanner button_function");
            }
            switch(langauge){
                case 1:
                    scan_btn.setText(R.string.device_stop_search_1);
                    break;
                case 2:
                    scan_btn.setText(R.string.device_stop_search_2);
                    break;
                case 3:
                    scan_btn.setText(R.string.device_stop_search_3);
                    break;
            }
        }
    }

    public static boolean getRawFlag(){

        return saveRawMain;
    }

    /**
     * Convert bytes into hex string.
     */
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        if ((bytes == null) || (bytes.length <= 0)) {
            return "";
        }

        char[] hexChars = new char[bytes.length * 3 - 1];

        for (int j=0; j<bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            if (j < bytes.length - 1) {
                hexChars[j * 3 + 2] = 0x20;           // hard coded space
            }
        }

        return new String(hexChars);
    }
}
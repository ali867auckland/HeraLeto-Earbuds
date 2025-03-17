package com.wbd101.hlt010;

import static com.wbd101.hlt010.AndroidHRVAPI.HRV_Get_Analysis_Result;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

public class  BleOperations extends AppCompatActivity {
    private ActivityResultLauncher<Intent> enableBtLauncher;

    private static final String TAG = "BleOperations";

    // Bluetooth related
    private BluetoothGatt gatt;
    private static final int GATT_MAX_MTU_SIZE = 517;
    private final ArrayList<BluetoothGattCharacteristic> characteristics = new ArrayList<>();
    private static final UUID HEART_RATE_MEASURE = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID TEMPERATURE_MEASURE = UUID.fromString("00002a1c-0000-1000-8000-00805f9b34fb");
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothLeScanner bleScanner = null;
    private ScanCallback scanCallback;
    private static final int REQUEST_ENABLE_BT = 1;
    private final Handler advertisementHandler = new Handler(Looper.getMainLooper());
    private int lastAdvertisement = 0;
    private static final int wbdID = 7674;  // WBD's manufacturer ID (HEX = 1DFA)
    private int manufacturerID = 0;

    // Data variables
    private long currentUnixTime;
    private int currentHeartRate = 0;
    private float currentTemp = 0;
    private int currentResp = 0;
    private String csv_path, csv_path2, csv_path3;
    private final Date dNow = new Date();
    private final SimpleDateFormat timeStamp = new SimpleDateFormat("HH_mm_ss");
    SimpleDateFormat dateTimeStamp = new SimpleDateFormat("yyyy_MM_dd'_'HH_mm_ss");
    private String currentTimeString;
    private List<String> processed_line;
    private String deviceID = "";
    private String deviceAddress = "";
    private int rri_progress = 0;
    private int hr_max =0, hr_min = 100;
    private float temp_max =0.0f, temp_min = 200.0f;
    private int resp_max = 0, resp_min = 100;
    private int spo2_min = 90;
    private int x_hr = 0, x_temp = 0, x_resp=0, x_spo2 = 0;
    private int markerID = 0;
    private int seconds = 0;

    // UI handles:
    private TextView hr, rr, temp, tempUnit, o2;
    private TextView label_HR, label_T, label_RR, label_S, label_DevName, barTitle;
    private final LineGraphSeries<DataPoint> hrGraphSeries = new LineGraphSeries<>();
    private final LineGraphSeries<DataPoint> tempGraphSeries = new LineGraphSeries<>();
    private final LineGraphSeries<DataPoint> respGraphSeries = new LineGraphSeries<>();
    private final LineGraphSeries<DataPoint> spo2GraphSeries = new LineGraphSeries<>();
    private GraphView hr_graph, temp_graph, resp_graph, spo2_graph;
    private int language = 1;
    private ActionBar actionBar;
    private Button disconnect_button;
    private CardView spo2CardView;
    private Button marker_button;
    private TextView timer_string;
    private final Handler timeHandler = new Handler(Looper.getMainLooper());

    private int displaySpO2 = 0;
    private float displayTemp = 0;
    private int displayHR = 0;
    private int displayResp = 0;
    private int spo2val = 0;
    private int spo2LastValid = 0;

    // Flags:
    private boolean saveRaw = false;
    private final boolean saveTempResp = true;  // Enable saving temperature and respiration rate
    private final boolean saveSpO2 = true;            // Enable saving SpO2 messages
    private final boolean disconnectFlag = false;     // Used for auto-reconnection
    private final boolean enableHRV = true;           // Used to include and test the HRV API.
    private boolean showSpO2 = false;
    private final boolean enableMarker = true;        // Enable a timing marker button to be implemented
    private boolean startTimer = true;
    private final boolean saveLastSpO2Reading = true;
    private final boolean saveDebugString = true;
    private static final int REQUEST_PERMISSIONS = 3;

    // Native API types
    private hrv_result_t hrv_result = new hrv_result_t();
    private float rmssd = 0;
    private RfcommClientThread rfcommThread;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_operations);

        Intent intent = getIntent();
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
            Log.e(TAG, "Null Error, Bleoperations line 174");
        }
        if (showSpO2){
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

        // Register for broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        registerReceiver(mReceiver, filter);
        //BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        //BluetoothDevice device = getIntent().getParcelableExtra("data", BluetoothDevice.class);
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        if (device == null) {
            Log.e(TAG, "Intent device, BleOperations line 201");
        }
        Bundle extras = intent.getExtras();
        boolean autoConnect = true;
        runOnUiThread(() -> {
            // handle devices with same MAC for both classic BT and BLE:
            if (ActivityCompat.checkSelfPermission(BleOperations.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
            } else {
                ActivityCompat.requestPermissions(BleOperations.this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN},
                        REQUEST_PERMISSIONS);
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
            }
            if (device != null) {
                gatt = device.connectGatt(getApplicationContext(), autoConnect, gattCallback,
                        BluetoothDevice.TRANSPORT_LE);
            } else {
                Log.e(TAG, "device, BleOperations 219");
            }
        });


        // Update the device ID and address:
        if (device != null) {
            deviceAddress = device.getAddress();
        } else {
            Log.e(TAG, "deviceAddress, BleOperations ,line 228");
        }
        deviceID = deviceAddress.substring(deviceAddress.length()-5,deviceAddress.length()-3) +
                deviceAddress.substring(deviceAddress.length()-2);
        Log.d(TAG, "onCreate: deviceID = " + deviceID);
        Log.d(TAG, "onCreate: deviceAddress = " + deviceAddress);
        TextView device_name = findViewById(R.id.NBO_device_name);
        device_name.setText(deviceID != null ? deviceID.toUpperCase():"UNKNOWN");
        label_DevName = findViewById(R.id.Label_device_name_ble);

        // Check if we need to generate the csv save file name and target directories:
        saveRaw = MainActivity.getRawFlag();  // From previous page UI checkbox
        if (saveRaw) {
            File root = getExternalFilesDir(Environment.getDataDirectory().getAbsolutePath());
            try {
                String appFolder = root + "/" + getString(R.string.app_name);
                File appDirectory = new File(appFolder);
                if (!appDirectory.exists()) {
                    appDirectory.mkdir(); //mkdir had warning
                }
                String tx_filename = deviceID + "__" + dateTimeStamp.format(dNow.getTime());  // Important: double underscore used as a separator.
                csv_path = appFolder + "/" + tx_filename + "_HR_PPI.csv";
                csv_path2 = appFolder + "/" + tx_filename + "_T_RR.csv";
                csv_path3 = appFolder + "/" + tx_filename + "_SPO2.csv";

            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }


        // UI: HR display
        hr_graph = findViewById(R.id.hr_graph);
        hr_graph.setBackgroundColor(Color.TRANSPARENT);
        hr_graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        hr_graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        hr_graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        hr_graph.getViewport().setYAxisBoundsManual(true);
        hr_graph.getViewport().setXAxisBoundsManual(true);
        hr_graph.getViewport().setMinX(0.0);
        hr_graph.getViewport().setMaxX(100.0);
        hrGraphSeries.setThickness(4);
        hrGraphSeries.setColor(Color.parseColor("#A6CEE3"));
        hrGraphSeries.setDataPointsRadius((float)2.0);
        hr_graph.addSeries(hrGraphSeries);
        label_HR = findViewById(R.id.hr_textView);

        // UI: Temperature display
        temp_graph = findViewById(R.id.temp_graph);
        temp_graph.setBackgroundColor(Color.TRANSPARENT);
        temp_graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        temp_graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        temp_graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        temp_graph.getViewport().setYAxisBoundsManual(true);
        temp_graph.getViewport().setXAxisBoundsManual(true);
        temp_graph.getViewport().setMinX(0.0);
        temp_graph.getViewport().setMaxX(100.0);
        tempGraphSeries.setThickness(4);
        tempGraphSeries.setColor(Color.parseColor("#A6CEE3"));
        tempGraphSeries.setDataPointsRadius((float)2.0);
        temp_graph.addSeries(tempGraphSeries);
        label_T = findViewById(R.id.temp_textView);

        // UI: Respiration display
        resp_graph = findViewById(R.id.resp_graph);
        resp_graph.setBackgroundColor(Color.TRANSPARENT);
        resp_graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        resp_graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        resp_graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        resp_graph.getViewport().setYAxisBoundsManual(true);
        resp_graph.getViewport().setXAxisBoundsManual(true);
        resp_graph.getViewport().setMinX(0.0);
        resp_graph.getViewport().setMaxX(100.0);
        respGraphSeries.setThickness(4);
        respGraphSeries.setColor(Color.parseColor("#A6CEE3"));
        respGraphSeries.setDataPointsRadius((float)2.0);
        resp_graph.addSeries(respGraphSeries);
        label_RR = findViewById(R.id.resp_textView);

        // UI: SpO2 display
        spo2CardView = findViewById(R.id.spo2_cardView);
        spo2_graph = findViewById(R.id.spo2_graph);
        spo2_graph.setBackgroundColor(Color.TRANSPARENT);
        spo2_graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        spo2_graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        spo2_graph.getGridLabelRenderer().setVerticalLabelsVisible(false);
        spo2_graph.getViewport().setYAxisBoundsManual(true);
        spo2_graph.getViewport().setXAxisBoundsManual(true);
        spo2_graph.getViewport().setMinX(0.0);
        spo2_graph.getViewport().setMaxX(100.0);
//        spo2_graph.getViewport().setMinY(90);
        spo2_graph.getViewport().setMaxY(102);
        spo2GraphSeries.setThickness(4);
        spo2GraphSeries.setColor(Color.parseColor("#A6CEE3"));
        spo2GraphSeries.setDataPointsRadius((float)2.0);
        spo2_graph.addSeries(spo2GraphSeries);
        label_S = findViewById(R.id.spo2_textView);


        // Initialise native methods:
        AndroidRespirationAPI.init_respiration_analysis();
        if (enableHRV) {
            AndroidHRVAPI.init_hrv_analysis();
        }


        // Set up UI text handles:
        hr = findViewById(R.id.NBO_hr_value);
        if (extras == null || hr == null) {
            Log.e(TAG, "Null Error, BleOperations line 338");
        }
        hr.setText(extras.getString("currHR"));
        rr = findViewById(R.id.NBO_resp_value);
        rr.setText(extras.getString("currRR"));
        temp = findViewById(R.id.NBO_temp_value);
        temp.setText(extras.getString("currTemp"));
        tempUnit = findViewById(R.id.temp_unit);
        o2 = findViewById(R.id.NBO_spo2_value);
        o2.setText(extras.getString("currSpO2"));
        timer_string = findViewById(R.id.NBO_timer);


        // Set up the disconnect button:
        disconnect_button = findViewById(R.id.NBO_disconnect_button);
        disconnect_button.setOnClickListener(v -> {
            timeHandler.removeCallbacksAndMessages(null);
            gatt.disconnect();
            if (bleScanner == null || scanCallback == null) {
                Log.e(TAG, "Null Error, BleOperations line 357");
                Log.d(TAG, "Null Error, BleOperations line 357");
                Log.w(TAG, "Null Error, BleOperations line 357");
            }

            bleScanner.stopScan(scanCallback);  // Stop scanning for advertising packets
            try{advertisementHandler.removeCallbacksAndMessages(null);} catch (Exception ignored){}
            finish();  // Finishes current activity and go back to MainActivity (device selection)
        });

        // Set up the Marker ID button:
        marker_button = findViewById(R.id.NBO_marker_button);
        if (enableMarker){
            marker_button.setVisibility(View.VISIBLE);
        }
        marker_button.setOnClickListener(v -> {
            markerID += 1;
            marker_button.setText("Marker ID: " + markerID);
        });

        // Set up Bluetooth advertisement data background scanning for SpO2:
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtLauncher.launch(enableBtIntent);
        }
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "bluetooth_not_supported", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "mBluetoothAdapter, BleOperations line 388");
            finish();
        }

        language = MainActivity.getLanguage();
        updateLanguage(language);

        beginAdvertisementScanning();  // Start the background scanning callback
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
            language = 1;
            Toast.makeText(getApplicationContext(), "English selected.", Toast.LENGTH_SHORT).show();
            updateLanguage(language);
            return true;
        } else if (item.getItemId() == R.id.CNT) {
            Log.d(TAG, "Traditional Chinese selected.");
            Toast.makeText(getApplicationContext(), "Traditional Chinese selected.", Toast.LENGTH_SHORT).show();
            language = 2;
            updateLanguage(language);
            return true;
        } else if (item.getItemId() == R.id.CNS) {
            Log.d(TAG, "Simplified Chinese selected.");
            Toast.makeText(getApplicationContext(), "Simplified Chinese selected.", Toast.LENGTH_SHORT).show();
            language = 3;
            updateLanguage(language);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Update language
    private void updateLanguage(int val){
        switch (val){
            case 1:
                if (showSpO2) {
                    barTitle.setText(R.string.menu_title_1);
                } else {
                    barTitle.setText(R.string.menu_title_hlt_1);
                }
                label_DevName.setText(R.string.device_id_1);
                disconnect_button.setText(R.string.disconnect_1);
                label_HR.setText(R.string.device_heart_rate_1);
                label_T.setText(R.string.device_temperature_1);
                label_RR.setText(R.string.device_respiration_1);
                label_S.setText(R.string.device_spo2_1);
                break;
            case 2:
                if (showSpO2) {
                    barTitle.setText(R.string.menu_title_2);
                } else {
                    barTitle.setText(R.string.menu_title_hlt_2);
                }
                label_DevName.setText(R.string.device_id_2);
                disconnect_button.setText(R.string.disconnect_2);
                label_HR.setText(R.string.device_heart_rate_2);
                label_T.setText(R.string.device_temperature_2);
                label_RR.setText(R.string.device_respiration_2);
                label_S.setText(R.string.device_spo2_2);
                break;
            case 3:
                if (showSpO2) {
                    barTitle.setText(R.string.menu_title_3);
                } else {
                    barTitle.setText(R.string.menu_title_hlt_3);
                }
                label_DevName.setText(R.string.device_id_3);
                disconnect_button.setText(R.string.disconnect_3);
                label_HR.setText(R.string.device_heart_rate_3);
                label_T.setText(R.string.device_temperature_3);
                label_RR.setText(R.string.device_respiration_3);
                label_S.setText(R.string.device_spo2_3);
                break;
        }
    }

    // Handler to repeat BLE advertisement scanning:
    private void beginAdvertisementScanning(){
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: Scanning for advertised packets...");
                if (lastAdvertisement > 10){
                    lastAdvertisement = 0;
                    try {
                        scanAdvertisingData();
                    } catch (Exception e){
                        Log.d(TAG, "Advertisement rescan Exception: " + e);
                    }
                }
                lastAdvertisement++;
                advertisementHandler.postDelayed(this,1000);
            }
        };
        advertisementHandler.post(runnable);
    }


    // Scan BLE advertising data in background (for BLE SpO2 data):
    private void scanAdvertisingData(){
        if (bleScanner == null || scanCallback == null) {
            Log.e(TAG, "bleScanner or scanCallback");
            Log.d(TAG, "bleScanner or scanCallback");
            Log.w(TAG, "bleScanner or scanCallback");

        }
        bleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "bluetooth_not_supported", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "mBluetoothAdapter, BleOperations line 507");
            Log.d(TAG, "mBluetoothAdapter, BleOperations line 507");
            Log.w(TAG, "mBluetoothAdapter, BleOperations line 507");
            finish();
        }
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();

        scanCallback = new ScanCallback() {

            @Override
            public void onScanResult(int callbackType, ScanResult result) {

                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();

                if (ActivityCompat.checkSelfPermission(BleOperations.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
                } else {
                    ActivityCompat.requestPermissions(BleOperations.this,
                            new String[]{Manifest.permission.BLUETOOTH_SCAN},
                            REQUEST_PERMISSIONS);
                    Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
                }
                String deviceName = device.getName();

                // Check the manufacturer ID:
                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord != null) {
                    SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
                    for (int i = 0; i < manufacturerData.size(); i++) {
                        manufacturerID = manufacturerData.keyAt(i);
                        byte[] obj = manufacturerData.get(manufacturerID);
                        //Log.i("ScanRecord-MFData: ", "id: " + manufacturerID + " obj: " + bytesToHex(obj));
                    }
                }


//                if(device.getName()!=null &&
//                        (device.getName().contains("HLT") ||
//                                (device.getName().contains("Hera")) ||
//                                (device.getName().contains("OskronCare")))) {

                if (manufacturerID == wbdID){

                    if (device.getAddress().equals(deviceAddress)) {

                        lastAdvertisement = 0;
//                        ScanRecord scanRecord = result.getScanRecord();
//                        Log.d("onScanResult: ", "device.getAddress() = " + device.getAddress());
//                        Log.d("onScanResult: ", "device.getName() = " + device.getName());

                        if (scanRecord != null) {

                            int flag = scanRecord.getAdvertiseFlags();
                            String bytes = bytesToHex(scanRecord.getBytes());
                            SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
                            Log.i("ScanRecord-flag: ", " " + flag);
                            Log.i("ScanRecord-bytes: ", bytes);

                            for (int i = 0; i < manufacturerData.size(); i++) {

                                int id = manufacturerData.keyAt(i);
                                byte[] obj = manufacturerData.get(id);
                                Log.i("ScanRecord-MFData: ", "id: " + id + " obj: " + bytesToHex(obj));
                                String value_str = bytesToHex(obj);
                                String[] line = value_str.split(" ");

                                // Extract the advertised HR value:
                                String hrval = (String) hr.getText();
                                int hrInt;
                                if (hrval.contains("-")){
                                    hrInt = 0;
                                } else {
                                    hrInt = Integer.parseInt(hrval);
                                }

                                // Extract the advertised respiration rate:
                                int res_rate = Integer.parseInt(line[5], 16);
                                if ((res_rate>0)&&(res_rate<45)) {
                                    currentResp = res_rate;
                                }

                                // Custom SpO2 value check:
                                spo2val = Integer.parseInt(line[line.length-5],16);
                                Log.d("ScanRecord-MFData: ", "SpO2 = " + spo2val);
                                if (spo2val == 151){
                                    if (!showSpO2) {
                                        runOnUiThread(() -> {
                                            spo2CardView.setVisibility(View.VISIBLE);
                                            showSpO2 = true;
                                        });
                                    }
                                }

                                if ((hrInt>0) && (hrInt<255) && (spo2val >= 70) && (spo2val <= 100)){

                                    runOnUiThread(() -> {

                                        displaySpO2 = spo2val;
                                        spo2LastValid = spo2val;

                                        // Update the SpO2 UI text:
                                        o2.setText(Integer.toString(spo2val));

                                        // Update the SpO2 graph lower limit:
                                        if(spo2val<spo2_min){
                                            spo2_min = spo2val;
                                        }
                                        spo2_graph.getViewport().setMinY(spo2_min);

                                        // Update the SpO2 graph:
//                                        DataPoint datapoint = new DataPoint(x_spo2++, spo2val);
//                                        spo2GraphSeries.appendData(datapoint, true, 100);

                                        if (!showSpO2) {
                                            spo2CardView.setVisibility(View.VISIBLE);
                                            showSpO2 = true;
                                            updateLanguage(language);
                                        }
                                    });

                                    // Save the raw data if the "Log raw data" box was checked:
//                                    if (saveRaw && saveSpO2){
//
//                                        Log.d(TAG, "onScanResult: Writing SpO2 data to file...");
//
//                                        // Set the timestamp for data logging:
//                                        Date dNow = new Date();
//                                        currentUnixTime = dNow.getTime();
//                                        currentTimeString = timeStamp.format(dNow.getTime());
//
//                                        // Construct the data line to be written to file:
//                                        processed_line = new ArrayList<>();
//                                        processed_line.add(currentTimeString);                    // First value is the timestamp
//                                        processed_line.add(Integer.toString(spo2val));            // Second value is the temperature
//                                        String[] final_line = new String[processed_line.size()];  // Create a string array of the correct size
//                                        processed_line.toArray(final_line);                       // Convert raw data array to string array
//                                        csv_writer(final_line, csv_path3, true);                  // Write the string array to the CSV file
//                                    }

                                } else {
                                    runOnUiThread(() -> {

                                        displaySpO2 = 0;

                                        // Update the SpO2 graph:
//                                        DataPoint datapoint = new DataPoint(x_spo2++, 0);
//                                        spo2GraphSeries.appendData(datapoint, true, 100);
                                    });
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e("ScanCallback", "onScanFailed: code " + errorCode);
            }
        };
        if (bleScanner == null) {
            Log.e(TAG, "Null Error, BleOperations line 676");
            Log.d(TAG, "Null Error, BleOperations line 676");
            Log.w(TAG, "Null Error, BleOperations line 676");
        }
        bleScanner.startScan(null, scanSettings, scanCallback);
    }

    private void runTimer() {
        if (startTimer) {
            timer_string.setVisibility(View.VISIBLE);
            startTimer = false;
            Runnable runnable = new Runnable() {  // Perform timer updates in a separate thread
                @Override
                public void run() {
                    int hours = seconds / 3600;
                    int minutes = (seconds % 3600) / 60;
                    int secs = seconds % 60;
                    String time = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
                    timer_string.setText(time);
                    timer_string.setTextSize(32);
                    seconds++;
                    timeHandler.postDelayed(this, 1000);  // Repeat this every 1s
                }
            };
            timeHandler.post(runnable);
        }
    }

    // Create a BroadcastReceiver for bluetooth actions
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                //BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //BluetoothDevice device = getIntent().getParcelableExtra("data", BluetoothDevice.class);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                if (device == null) {
                    Log.e(TAG, "Null Error BleOperations line 704");
                }
                Toast.makeText(getApplicationContext(), "Connected to "+ device.getAddress(), Toast.LENGTH_SHORT).show();
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                Toast.makeText(getApplicationContext(), "Device Disconnected", Toast.LENGTH_SHORT).show();
            }
        }
    };


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange called with status: " + status + ", newState: " + newState);
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG, "Successfully connected to BLE device: " + gatt.getDevice().getAddress());
                if(newState == BluetoothProfile.STATE_CONNECTED){
                    Log.w("BluetoothGattCallback", "Successfully connected to device "+gatt.getDevice().getAddress());
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (ActivityCompat.checkSelfPermission(BleOperations.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                            Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
                        } else {
                            ActivityCompat.requestPermissions(BleOperations.this,
                                    new String[]{Manifest.permission.BLUETOOTH_SCAN},
                                    REQUEST_PERMISSIONS);
                            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
                        }
                        boolean ans = gatt.discoverServices();
                        Log.d("onConnectionStateChange", "Discover services started: "+ ans);
                        gatt.requestMtu(GATT_MAX_MTU_SIZE);
                    });
                    BluetoothManager bluetoothManager = BleOperations.this.getSystemService(BluetoothManager.class);
                    BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
                    BluetoothDevice raspberryPi = bluetoothAdapter.getRemoteDevice("E4:5F:01:18:2C:8F");
                    Log.d(TAG, "Initializing RfcommClientThread...");
                    rfcommThread = new RfcommClientThread(BleOperations.this, raspberryPi, getString(R.string.app_uuid));
                    rfcommThread.start();
                    Log.d(TAG, "RfcommClientThread started.");

                }
                else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                    Log.w("BluetoothGattCallback", "Successfully disconnected form device "+ gatt.getDevice().getAddress());
                    if (disconnectFlag) {
                        gatt.close();
                        if (bleScanner == null || scanCallback == null) {
                            Log.e(TAG, "bleScanner or scanCallback");
                            Log.d(TAG, "bleScanner or scanCallback");
                            Log.w(TAG, "bleScanner or scanCallback");
                        }
                        bleScanner.stopScan(scanCallback);
                    }
                }
                else{
                    Log.w("BluetoothGattCallback", "Error "+status+" encountered for "+gatt.getDevice().getAddress()+ "\nDisconnecting...");
                    gatt.close();
                    if (bleScanner == null || scanCallback == null) {
                        Log.e(TAG, "Null Error");
                        Log.d(TAG, "Null Error");
                        Log.w(TAG, "Null Error");
                    }
                    bleScanner.stopScan(scanCallback);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            List<BluetoothGattService> services = gatt.getServices();
            Log.w("BluetoothGattCallback", "Discovered "+ services.size()+" services for "+gatt.getDevice().getAddress());

            for(int i = 0; i< services.size(); i++){
                List<BluetoothGattCharacteristic> characteristic = services.get(i).getCharacteristics();
                Log.w("BluetoothGattCallback", "Discovered Characteristic of size "+ characteristic.size()+" for "+ services.get(i).getUuid());
                for(int j = 0; j<characteristic.size(); j++){
                    characteristics.add(characteristic.get(j));
                    Log.w("BluetoothGattCallback", "Discovered Characteristic"+ characteristic.get(j).getUuid()+" for "+gatt.getDevice().getAddress());
                }
            }
            Log.w("BluetoothGattCallback", "Total Characteristics = "+ characteristics.size());
            characteristicsOperations(characteristics);

        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            super.onCharacteristicRead(gatt, characteristic, value, status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.i("BluetoothGattCallback", "Read characteristic success for "+ characteristic.getUuid().toString() +" value: "+ new String(value, StandardCharsets.UTF_8));
            }
            else if(status == BluetoothGatt.GATT_READ_NOT_PERMITTED){
                Log.i("BluetoothGattCallback", "Read not permitted for  "+ characteristic.getUuid().toString());
            }
            else{
                Log.i("BluetoothGattCallback", "Characteristic read failed for  "+ characteristic.getUuid().toString());
                if (ActivityCompat.checkSelfPermission(BleOperations.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
                } else {
                    ActivityCompat.requestPermissions(BleOperations.this,
                            new String[]{Manifest.permission.BLUETOOTH_SCAN},
                            REQUEST_PERMISSIONS);
                    Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
                }
                gatt.disconnect();
                if (bleScanner == null || scanCallback == null) {
                    Log.e(TAG, "bleScanner or scanCallback");
                    Log.d(TAG, "bleScanner or scanCallback");
                    Log.w(TAG, "bleScanner or scanCallback");
                }
                bleScanner.stopScan(scanCallback);

            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            super.onCharacteristicChanged(gatt, characteristic, value);
            broadcastUpdate(characteristic, value);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            boolean event = status ==BluetoothGatt.GATT_SUCCESS;
            Log.w("onMtuChanged", "ATT MTU changed to "+mtu+" "+ event);
        }
    };


    private void characteristicsOperations(List<BluetoothGattCharacteristic> characteristics){
        if(characteristics.isEmpty()){
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?");
            return;
        }
        Iterator<BluetoothGattCharacteristic> it = characteristics.iterator();
        while(it.hasNext()) {
            BluetoothGattCharacteristic character = it.next();

            if(character.getUuid().equals(HEART_RATE_MEASURE)){
                new Handler(Looper.getMainLooper()).postDelayed(() -> setNotifications(character, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, true), 2000);

            }
            if(character.getUuid().equals(TEMPERATURE_MEASURE)){
                new Handler(Looper.getMainLooper()).postDelayed(() -> setNotifications(character, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, true), 500);
            }
        }

    }


    public void setNotifications(BluetoothGattCharacteristic characteristic, byte[] payload, boolean enable){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission granted");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_PERMISSIONS);
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted");
        }
        String CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";
        UUID cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID);

        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(cccdUuid);
        if(descriptor == null){
            Log.e("setNotification", "Could not get CCC descriptor for characteristic "+ characteristic.getUuid());
        }
        if (descriptor == null) {
            Log.e(TAG, "Null Error");
        }
        if(!gatt.setCharacteristicNotification(descriptor.getCharacteristic(), enable)){
            Log.e("setNotification", "setCharacteristicNotification failed");
        }
        descriptor.setValue(payload);
        boolean result = gatt.writeDescriptor(descriptor);
        if(!result){
            Log.e("setNotification", "writeDescriptor failed for descriptor");

            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(),"Descriptor failed! Device may not be connected. Try again!",Toast.LENGTH_LONG).show());
            gatt.disconnect();
            finish();
        }
    }


    // Message handling:
    private void broadcastUpdate(final BluetoothGattCharacteristic characteristic, byte[] value) {

        // Process the heart-rate message:
        if (characteristic.getUuid().equals(HEART_RATE_MEASURE)) {

            // Initialise timer:
            if (startTimer){
                runOnUiThread(this::runTimer);
            }

            String value_str = bytesToHex(value);
            String[] line = value_str.split(" ");
            int heart_rate = Integer.parseInt(line[1], 16);  // Extract the HR value

            Log.d("Raw data logging : ", Arrays.toString(line));
            Log.d("broadcastUpdate", "Heart Rate: " + heart_rate);

            // Set the timestamp for data logging:
            Date dNow = new Date();
            currentUnixTime = dNow.getTime();
            currentTimeString = timeStamp.format(dNow.getTime());

            // Process the HR information:
            if (heart_rate != 255) {

                // Set the HR for data logging:
                currentHeartRate = heart_rate;

                // Create and send JSON data, before UI updates
                String dataToSend = String.format(
                        Locale.US,
                        "{\"timestamp\":\"%s\",\"heartRate\":%d,\"temperature\":%.2f,\"respiration\":%d,\"spo2\":%d,\"markerID\": %d, \"deviceID\":\"%s\"}",
                        currentTimeString,
                        currentHeartRate,
                        currentTemp,
                        currentResp,
                        displaySpO2,
                        markerID,
                        deviceID
                );

                // Send through RFCOMM if connection exists
                RfcommClientThread rfcomm = RfcommClientThread.getInstance();
                if (rfcomm != null) {
                    rfcomm.sendData(dataToSend + "\n");
                }

                runOnUiThread(() -> {

                    // Update the UI plot upper limit:
                    if(heart_rate != 0 && heart_rate>hr_max){
                        hr_max = heart_rate;
                        hr_graph.getViewport().setMaxY(hr_max+2);
                    }

                    // Update the UI plot lower limit:
                    if(heart_rate != 0 && heart_rate<hr_min){
                        hr_min = heart_rate;
                        hr_graph.getViewport().setMinY(hr_min-2);
                    }

                    // Update the HR graph and/or the HR display text:
                    DataPoint datapoint = new DataPoint(x_hr++, heart_rate);
                    hrGraphSeries.appendData(datapoint, true, 100);
                    if (heart_rate>0) {
                        hr.setText(Integer.toString(heart_rate));
                    } else {
                        hr.setText("--");
                    }

                    // Update the temperature graph
                    datapoint = new DataPoint(x_temp++, displayTemp);
                    tempGraphSeries.appendData(datapoint, true, 100);

                    // Update the respiration graph
                    datapoint = new DataPoint(x_resp++, displayResp);
                    respGraphSeries.appendData(datapoint, true, 100);

                    // Update the SpO2 graph
                    datapoint = new DataPoint(x_spo2++, displaySpO2);
                    spo2GraphSeries.appendData(datapoint, true, 100);
                });
            }

            // Process the RRI information:
            List<Short> rri_values= new ArrayList<>();
            List<Integer> currentRRIs = new ArrayList<>();
            if(line.length>2) {
                rri_progress++;
                String[] rris = Arrays.copyOfRange(line, 2, line.length);
                for(int i = 0 ; i < rris.length-1; i=i+2){
                    //int raw_rri = Integer.parseInt(rris[i+1] + rris[i], 16);  // No Ble timing correction
                    int raw_rri = (int)(Integer.parseInt(rris[i+1]+rris[i], 16)*1000.0/1024);  // Corrected
                    rri_values.add((short) raw_rri);
                    currentRRIs.add(raw_rri);
                }
                for(int i = 0; i< rri_values.size(); i++){
                    AndroidRespirationAPI.analyze_respiration((float)rri_values.get(i));  // native
                }
            }

            // Implement HRV API
            if (enableHRV){
                if (currentRRIs.size() > 0){
                    for (int i = 0; i < currentRRIs.size(); i++) {
                        Log.d("HRV API Input: ","timestamp: " + (int)(currentUnixTime/1000) + ", HR: " + (short)heart_rate + ", RRI: " + rri_values.get(i));
                        AndroidHRVAPI.hrv_analysis((int)(currentUnixTime/1000),(short)heart_rate,rri_values.get(i));
                    }
                }
                hrv_result = AndroidHRVAPI.HRV_Get_Analysis_Result();
                rmssd = hrv_result.getRMSSD();
                Log.d("HRV API Output: ", "RMSSD: " + rmssd);
            }

            // Save the raw data if the "Log raw data" box was checked:
            if (saveRaw){
                // If no RRIs were detected:
                if (currentRRIs.size() == 0){
                    processed_line = new ArrayList<>();
                    processed_line.add(currentTimeString);                         // First value is the timestamp
                    processed_line.add(Integer.toString(currentHeartRate));        // Second value is the HR (bpm)
                    processed_line.add(Integer.toString(-1));                      // No RRI = -1
                    if (enableHRV) {
                        processed_line.add(Float.toString(rmssd));
                    }
                    String[] final_line = new String[processed_line.size()];       // Create a string array of the correct size
                    processed_line.toArray(final_line);                            // Convert raw data array to string array
                    csv_writer(final_line, csv_path, true);                        // Write the string array to the CSV file
                }
                else {
                    // Make a new line entry for each RRI value (repeated HR and timestamp)
                    for (int i = 0; i < currentRRIs.size(); i++) {
                        processed_line = new ArrayList<>();
                        processed_line.add(currentTimeString);                     // First value is the timestamp
                        processed_line.add(Integer.toString(currentHeartRate));    // Second value is the HR (bpm)
                        processed_line.add(Integer.toString(currentRRIs.get(i)));  // Third value is the RRI (ms)
                        if (enableHRV) {
                            processed_line.add(Float.toString(rmssd));
                        }
                        String[] final_line = new String[processed_line.size()];   // Create a string array of the correct size
                        processed_line.toArray(final_line);                        // Convert raw data array to string array
                        csv_writer(final_line, csv_path, true);                    // Write the string array to the CSV file
                    }
                }
            }
        }


        // Process the temperature message & Calculate respiration rate:
        if (characteristic.getUuid().equals(TEMPERATURE_MEASURE)) {
            String result;
            int displayed_heart_rate=0;
            try {
                displayed_heart_rate = Integer.parseInt(hr.getText().toString());
            }
            catch (final NumberFormatException e) {
                Log.d("broadcastUpdate-temp:", e.getMessage() != null ? e.getMessage() : "No message available.");
            }
            if (value.length >= 5 && displayed_heart_rate!=0) {

                // Extract the temperature value in degrees Celsius:
                boolean isFahrenheit = (value[0] & 0x01) == 1;
                int temperature_mantissa = (value[1] & 0xFF) | ((value[2] & 0xFF) << 8) | ((value[3] & 0xFF) << 16);
                double temperature = temperature_mantissa * Math.pow(10.0, 1.0 * value[4]);
                result = String.format(Locale.US, "%.2f°%s", temperature, (isFahrenheit) ? "F" : "C");

                // Set the timestamp for data logging:
                Date dNow = new Date();
                currentUnixTime = (int) dNow.getTime();
                currentTimeString = timeStamp.format(dNow.getTime());

                // The initial value is always 30 so we wait for 10 PPIs and make sure we don't see
                // any high values (so<220) that is returned before we get actual values
                if(temperature < 220.0){
                    String finalResult = result.substring(0,5);  // removing the unit because UI is programmed separately

                    // Set the T for data logging:
                    currentTemp = (float) temperature;
                    if(!isFahrenheit){
                        if ((currentTemp<25)||(currentTemp>45)) {
                            currentTemp = 0.0F;
                        }
                    } else {
                        if ((currentTemp<77)||(currentTemp>113)) {
                            currentTemp = 0.0F;
                        }
                    }
                    displayTemp = currentTemp;

                    runOnUiThread(() -> {

                        // Update the temperature UI text:
                        if (currentTemp>0) {
                            temp.setText(finalResult);
                        } else {
                            temp.setText("--");
                        }

                        // Update the temperature graph upper limit:
                        if(currentTemp>temp_max){
                            temp_max = currentTemp;
                            temp_graph.getViewport().setMaxY(temp_max+0.2);
                        }

                        // Update the temperature graph lower limit:
                        if((currentTemp<temp_min)&&(currentTemp>0)){
                            temp_min = currentTemp;
                            temp_graph.getViewport().setMinY(temp_min-0.2);
                        }

                        // Update the temperature graph:
//                        DataPoint datapoint = new DataPoint(x_temp++, currentTemp);
//                        tempGraphSeries.appendData(datapoint, true, 100);
                        if(!isFahrenheit){
                            tempUnit.setText(R.string.celsius);
                        }else tempUnit.setText(R.string.fahrenheit);
                    });
                }


                // Calculate the respiration rate from the native functions:
                if(rri_progress > 30) {     // RR takes around 30 seconds to produce results
//                    respiration_result_t respiraResult = AndroidRespirationAPI.get_respiration_result();
//                    currentResp = respiraResult.getRespiratory_rate();
//                    float curr_depth = respiraResult.getRespiration_current_depth();
//                    String is_inspirating = (respiraResult.getIs_inspirating()) ? "True" : "False";
//                    Log.d("onCreate-HRV_Results", "Respiratory rate: " + currentResp);
//                    Log.d("onCreate-HRV_Results", "Current depth: " + curr_depth);
//                    Log.d("onCreate-HRV_Results", "is_inspirating: " + is_inspirating);

                    runOnUiThread(() -> {
                        if (currentResp >= 6) {
                            displayResp = currentResp;

                            // Update the respiration text:
                            rr.setText(String.format(Locale.US, "%d", currentResp));

                            // Update the respiration graph upper limit:
                            if (currentResp > resp_max) {
                                resp_max = currentResp;
                                resp_graph.getViewport().setMaxY(resp_max + 1);
                            }

                            // Update the respiration graph lower limit:
                            if (currentResp < resp_min) {
                                resp_min = currentResp;
                                resp_graph.getViewport().setMinY(resp_min - 1);
                            }

                            // Update the respiration graph:
//                            DataPoint datapoint = new DataPoint(x_resp++, currentResp);
//                            respGraphSeries.appendData(datapoint, true, 100);
                        }
                        else {
                            // Update the respiration text:
                            rr.setText("--");
                            displayResp = 0;
                            // Update the respiration graph:
//                            DataPoint datapoint = new DataPoint(x_resp++, currentResp);
//                            respGraphSeries.appendData(datapoint, true, 100);
                        }
                    });
                }

                // Save the raw data if the "Log raw data" box was checked:
                if (saveRaw && saveTempResp){
                    processed_line = new ArrayList<>();
                    processed_line.add(currentTimeString);                    // First value is the timestamp
                    processed_line.add(Float.toString(currentTemp));          // Second value is the temperature
                    processed_line.add(Integer.toString(currentResp));        // Third value is the respiration rate
                    String[] final_line = new String[processed_line.size()];  // Create a string array of the correct size
                    processed_line.toArray(final_line);                       // Convert raw data array to string array
                    csv_writer(final_line, csv_path2, true);                  // Write the string array to the CSV file
                }

                if (saveRaw && saveSpO2 && showSpO2){
                    Log.d(TAG, "onScanResult: Writing SpO2 data to file...");

                    // Construct the data line to be written to file:
                    processed_line = new ArrayList<>();
                    processed_line.add(currentTimeString);                    // First value is the timestamp
                    if (saveLastSpO2Reading){
                        processed_line.add(Integer.toString(spo2LastValid));
                    } else {
                        processed_line.add(Integer.toString(spo2val));        // Second value is the temperature
                    }
                    if (enableMarker){
                        processed_line.add(Integer.toString(markerID));
                    }
                    if (saveDebugString){
                        String debugStr = "[wbd]" + currentHeartRate + " " + spo2val + " " + currentTemp + " " + currentResp;
                        processed_line.add(debugStr);
                    }
                    String[] final_line = new String[processed_line.size()];  // Create a string array of the correct size
                    processed_line.toArray(final_line);                       // Convert raw data array to string array
                    csv_writer(final_line, csv_path3, true);                  // Write the string array to the CSV file
                }

            }

            // if HR == 0 || Not a valid data message:
            else{
                runOnUiThread(() -> {
                    hr.setText("--");
                    temp.setText("--");
                    rr.setText("--");
                    o2.setText("--");

                    displayResp = 0;
                    displayTemp = 0;
                    displaySpO2 = 0;
                    displayHR = 0;  // Not needed

                    // (HR graph already updated in HR message)

                    // Update the SpO2 graph:
//                    DataPoint datapoint = new DataPoint(x_spo2++, 0);
//                    spo2GraphSeries.appendData(datapoint, true, 100);

                    // Update the temperature graph:
//                    datapoint = new DataPoint(x_temp++, 0);
//                    tempGraphSeries.appendData(datapoint, true, 100);

                    // Update the respiration graph:
//                    datapoint = new DataPoint(x_resp++, 0);
//                    respGraphSeries.appendData(datapoint, true, 100);
                });}
        }
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

    // Raw data logging function
    public static void csv_writer(String[] line, String filePath, Boolean append){
        try {
            CSVWriter writer = new CSVWriter(new FileWriter(filePath, append));
            writer.writeNext(line, false);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

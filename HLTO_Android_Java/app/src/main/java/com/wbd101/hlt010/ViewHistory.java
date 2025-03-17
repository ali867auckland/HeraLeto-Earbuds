package com.wbd101.hlt010;

import android.graphics.Color; // For manipulating color values
import android.os.Build; // Used to acces information about the current Android device's API level
import android.os.Bundle; // Class is used to pass data between activities, save and restore the state of an activity
import android.os.Environment; // Provides access to external storage on Android, useful when needing to read or write files to the external storage
import android.util.Log; // For logging debug or error messages
import android.util.TypedValue; // Class is used to convert between different types of unit measurements, useful when handling UI layout across different screen denities
import android.view.Menu; // Represents an options menu
import android.view.MenuInflater; // Used to inflate menu resources into a menu hierarchy
import android.view.MenuItem; // Represents a single item in an options menu
import android.widget.Button; // Represents a button in the UI
import android.widget.TextView; // Represents a text display element in the UI
import android.widget.Toast; // For displaying brief messages to the user

import androidx.annotation.RequiresApi; // Used to indicate that a method requires a certain api level to function, prevent calling method on older Android versions
import androidx.appcompat.app.ActionBar; // For managing the app's action bar
import androidx.appcompat.app.AppCompatActivity; // Base class for modern Android activities
import androidx.recyclerview.widget.LinearLayoutManager; // For arranging RecyclerView items in a list
import androidx.recyclerview.widget.RecyclerView; // A flexible view for providing a scrollable list

import com.opencsv.CSVReader; // Part of opencsv lib, used to read and parse CSV files
import com.opencsv.exceptions.CsvValidationException;

import java.io.File; // Represebts a fuke ir directory in the filesystem, allowing for manipulating files and directories
import java.io.FileReader; // Provides ability to read files
import java.io.IOException; // Used for handling input/output exceptions
import java.nio.file.Files; // Utility for manipulating files
import java.nio.file.Paths; // Used for manipulating file paths, create file path objects in a class, which work with NIO
import java.util.ArrayList; // Provides resizable array implementation of the list interface
import java.util.Arrays; // Utility methods for working with arrays
import java.util.Collections; // Utility methods for working with arrays
import java.util.LinkedList; // A double linked list implementation

public class ViewHistory extends AppCompatActivity {

    private Button close_btn;
    private historyResultAdapter scanAdapter;
    private LinkedList<String> scanResults = new LinkedList<>();
    private LinkedList<String> hrResults = new LinkedList<>();
    private LinkedList<String> tempResults = new LinkedList<>();
    private LinkedList<String> respResults = new LinkedList<>();
    private LinkedList<String> spo2Results = new LinkedList<>();
    private final String TAG = "ViewHistory";
    private ActionBar actionBar;
    private TextView barTitle;
    private static boolean showSpO2 = false;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Set the page title:
        actionBar = getSupportActionBar();
        if (actionBar == null) {
            Log.e(TAG, "Null Error, ViewHistory line 58");
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

        // Search and display the data directory:
        File root = getExternalFilesDir(Environment.getDataDirectory().getAbsolutePath());
        String appFolder = root + "/" + getString(R.string.app_name);
        Log.d(TAG, "onCreate: Path = " + appFolder);
        File directory = new File(appFolder);
        File[] files = directory.listFiles();

        if (files != null) {

            Arrays.sort(files, Collections.reverseOrder());
            Log.d(TAG, "onCreate: files discovered = " + Arrays.toString(files));

            // Example filename (no device ID): 2022_04_14_17_15_54_HR_PPI.csv
            // Example filename (with device ID): DEVNAME__2022_04_14_17_15_54_HR_PPI.csv

            // Search the available files for unique device recordings:
            Log.d("Files", "Size: " + files.length);
            for (int i = 0; i < files.length; i++) {
                String fileName = files[i].getName();
                Log.d("Files", "FileName:" + fileName);

                // Extract the device ID, date and time:
                String[] fSplit = fileName.split("__");
                String devID, header;
                if (fSplit.length > 1) {
                    devID = fSplit[0];
                    header = fSplit[1].substring(0, 19);
                } else {
                    devID = "No device ID";
                    header = fSplit[0].substring(0, 19);
                }
                Log.d("Files", "FileName devID:" + devID);
                Log.d("Files", "FileName header:" + header);
                if (!scanResults.contains(devID + "__" + header)) {
                    scanResults.add(devID + "__" + header);
                }
            }


            // For each unique recording, extract the HR, Temp, and Respiration data from the files:
            if (scanResults.size() > 0) {
                int i;
                for (i = 0; i < scanResults.size(); i++) {
                    String fileName = scanResults.get(i);
                    String[] fSplit = fileName.split("__");
                    String hrFile, trFile, spFile;
                    if (fileName.contains("No device ID")) {
                        hrFile = fSplit[1] + "_HR_PPI.csv";
                        trFile = fSplit[1] + "_T_RR.csv";
                        spFile = fSplit[1] + "_SPO2.csv";
                    } else {
                        hrFile = fileName + "_HR_PPI.csv";
                        trFile = fileName + "_T_RR.csv";
                        spFile = fileName + "_SPO2.csv";
                    }

                    // Extract the HR:
                    int hrMin = 0;
                    int hrMax = 0;
                    int hrAvg = 0;
                    if (Files.exists(Paths.get(appFolder + "/" + hrFile))) {   // Proceed if the "HR_PPI" file exists.
                        Log.d(TAG, "onCreate: Processing HR file:  " + hrFile);
                        try {
                            CSVReader reader = new CSVReader(new FileReader(appFolder + "/" + hrFile));
                            String[] nextLine;
                            ArrayList<Integer> hrList = new ArrayList();
                            int hrLength = 0;
                            while ((nextLine = reader.readNext()) != null) {
                                int val = Integer.parseInt(nextLine[1]);
                                if ((val > 40) && (val < 220)) {
                                    hrList.add(val);
                                    hrLength += 1;
                                }
                            }
                            if (hrLength > 0) {
                                hrMin = Collections.min(hrList);
                                hrMax = Collections.max(hrList);
                                for (int j = 0; j < hrLength; j++) {
                                    hrAvg += hrList.get(j);
                                }
                                hrAvg = (int) ((float) hrAvg) / hrLength;
                            }
                            Log.d(TAG, "onCreate: hrMin = " + hrMin);
                            Log.d(TAG, "onCreate: hrMax = " + hrMax);
                            Log.d(TAG, "onCreate: hrAvg = " + hrAvg);
                            reader.close();
                        } catch (IOException e) {
                            Log.d(TAG, "onCreate: Failed to load CSV file");
                        } catch (CsvValidationException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    hrResults.add(hrMin + "_" + hrAvg + "_" + hrMax);


                    // Extract the Temperature and Respiration rate information:
                    float tempMin = 0.0F;
                    float tempMax = 0.0F;
                    float tempAvg = 0.0F;
                    int respMin = 0;
                    int respMax = 0;
                    int respAvg = 0;
                    if (Files.exists(Paths.get(appFolder + "/" + trFile))) {   // Proceed if the "T_RR" file exists.
                        Log.d(TAG, "onCreate: Processing T-RR file:  " + trFile);
                        try {
                            CSVReader reader = new CSVReader(new FileReader(appFolder + "/" + trFile));
                            String[] nextLine;
                            ArrayList<Float> tList = new ArrayList();
                            ArrayList<Integer> rList = new ArrayList();
                            int tLength = 0;
                            int rLength = 0;
                            while ((nextLine = reader.readNext()) != null) {
                                float tVal = Float.parseFloat(nextLine[1]);
                                if ((tVal >= 25) && (tVal <= 45)) {
                                    tList.add(tVal);
                                    tLength += 1;
                                }
                                int rVal = Integer.parseInt(nextLine[2]);
                                if ((rVal >= 5) && (rVal <= 40)) {
                                    rList.add(rVal);
                                    rLength += 1;
                                }
                            }
                            if (tLength > 0) {
                                tempMin = Collections.min(tList);
                                tempMax = Collections.max(tList);
                                for (int j = 0; j < tLength; j++) {
                                    tempAvg += tList.get(j);
                                }
                                tempAvg = tempAvg / tLength;
                            }
                            if (rLength > 0) {
                                respMin = Collections.min(rList);
                                respMax = Collections.max(rList);
                                for (int j = 0; j < rLength; j++) {
                                    respAvg += rList.get(j);
                                }
                                respAvg = (int) ((float) respAvg) / rLength;
                            }
                            Log.d(TAG, "onCreate: tempMin = " + tempMin);
                            Log.d(TAG, "onCreate: tempMax = " + tempMax);
                            Log.d(TAG, "onCreate: tempAvg = " + tempAvg);
                            Log.d(TAG, "onCreate: respMin = " + respMin);
                            Log.d(TAG, "onCreate: respMax = " + respMax);
                            Log.d(TAG, "onCreate: respAvg = " + respAvg);
                            reader.close();
                        } catch (IOException e) {
                            Log.d(TAG, "onCreate: Failed to load CSV file");
                        } catch (CsvValidationException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    tempResults.add(tempMin + "_" + tempAvg + "_" + tempMax);
                    respResults.add(respMin + "_" + respAvg + "_" + respMax);


                    // Extract the SpO2:
                    int spMin = 0;
                    int spMax = 0;
                    int spAvg = 0;
                    if (Files.exists(Paths.get(appFolder + "/" + spFile))) {   // Proceed if the "SPO2" file exists.
                        showSpO2 = true;
                        Log.d(TAG, "onCreate: Processing SPO2 file:  " + spFile);
                        try {
                            CSVReader reader = new CSVReader(new FileReader(appFolder + "/" + spFile));
                            String[] nextLine;
                            ArrayList<Integer> spList = new ArrayList();
                            int spLength = 0;
                            while ((nextLine = reader.readNext()) != null) {
                                int val = Integer.parseInt(nextLine[1]);
                                if ((val >= 70) && (val <= 100)) {
                                    spList.add(val);
                                    spLength += 1;
                                }
                            }
                            if (spLength > 0) {
                                spMin = Collections.min(spList);
                                spMax = Collections.max(spList);
                                for (int j = 0; j < spLength; j++) {
                                    spAvg += spList.get(j);
                                }
                                spAvg = (int) ((float) spAvg) / spLength;
                            }
                            Log.d(TAG, "onCreate: spMin = " + spMin);
                            Log.d(TAG, "onCreate: spMax = " + spMax);
                            Log.d(TAG, "onCreate: spAvg = " + spAvg);
                            reader.close();
                        } catch (IOException e) {
                            Log.d(TAG, "onCreate: Failed to load CSV file");
                        } catch (CsvValidationException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    spo2Results.add(spMin + "_" + spAvg + "_" + spMax);
                }
            }
        }


        // Defining the close button:
        close_btn = findViewById(R.id.NBO_close_history);
        close_btn.setOnClickListener(v -> {
            finish();  // Exit activity and go back to device listing
        });

        // Apply language:
        TextView hInfo = findViewById(R.id.h_info);

        int language = MainActivity.getLanguage();
        switch(language){
            case 1:
                if (showSpO2) {
                    barTitle.setText(R.string.menu_title_1);
                } else {
                    barTitle.setText(R.string.menu_title_hlt_1);
                }
                close_btn.setText(R.string.h_back_1);
                hInfo.setText(R.string.h_info_1);
                break;
            case 2:
                if (showSpO2) {
                    barTitle.setText(R.string.menu_title_2);
                } else {
                    barTitle.setText(R.string.menu_title_hlt_2);
                }
                close_btn.setText(R.string.h_back_2);
                hInfo.setText(R.string.h_info_2);
                break;
            case 3:
                if (showSpO2) {
                    barTitle.setText(R.string.menu_title_3);
                } else {
                    barTitle.setText(R.string.menu_title_hlt_3);
                }
                close_btn.setText(R.string.h_back_3);
                hInfo.setText(R.string.h_info_3);
                break;
        }

        // Set up the recycler view:
        RecyclerView recyclerView = findViewById(R.id.historyRecyclerView);
        scanAdapter = new historyResultAdapter(this, scanResults, hrResults, tempResults, respResults, spo2Results);
        recyclerView.setAdapter(scanAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    public static void displaySpO2(){
        showSpO2 = true;
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
            MainActivity.setLanguage(1);
            Toast.makeText(getApplicationContext(), "English selected.", Toast.LENGTH_SHORT).show();
            recreate();
            return true;
        } else if (item.getItemId() == R.id.CNT) {
            Log.d(TAG, "Traditional Chinese selected.");
            Toast.makeText(getApplicationContext(), "Traditional Chinese selected.", Toast.LENGTH_SHORT).show();
            MainActivity.setLanguage(2);
            recreate();
            return true;
        } else if (item.getItemId() == R.id.CNS) {
            Log.d(TAG, "Simplified Chinese selected.");
            Toast.makeText(getApplicationContext(), "Simplified Chinese selected.", Toast.LENGTH_SHORT).show();
            MainActivity.setLanguage(3);
            recreate();
            return true;
        }
        return super.onOptionsItemSelected(item);

    }
}


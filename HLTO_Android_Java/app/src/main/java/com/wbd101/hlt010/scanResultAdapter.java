package com.wbd101.hlt010;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice; // Represents a remote Bluetooth device
import android.bluetooth.le.ScanRecord; // Represents the data from a BLE advertisement packet
import android.bluetooth.le.ScanResult; // Represents a single BLE scan result
import android.content.Context; // Provides global information about an application environment
import android.content.Intent; // Used to start activities or send broadcasts
import android.util.Log; // For logging debug or error messages
import android.util.SparseArray; // A data structure optimized for storing and retrieving integers
import android.view.LayoutInflater; // Used to create a view from a XML layout file
import android.view.View; // The base class for Android UI components
import android.view.ViewGroup; // Special view component 
import android.widget.TableRow; // Represents a row in a tableLayout
import android.widget.TextView; // Represents a text display element in the UI

import androidx.annotation.NonNull; // Annotations that ensures null safety
import androidx.recyclerview.widget.RecyclerView; // Flexible view for efficiently displaying large datasets, designed to replace ListView

import java.util.LinkedList; // A double linked list implementation
import java.util.Locale; // Represents a specific geographical, political or cultural region (localization)

public class scanResultAdapter extends RecyclerView.Adapter<scanResultAdapter.scanViewHolder> { // Custom adapter for RecyclerView to display BLE results
    private final LinkedList<ScanResult> scanResultsList; //Stores BLE scan result in a linked list, each item is a ScanResult
    private final LayoutInflater mInflator; // Creates view objects from XML layout, initialized with app context
    private final Context context; // Provides access to resources and allows interaction with the Android system

    public scanResultAdapter(Context context, LinkedList<ScanResult> scanResultsList) {
        mInflator = LayoutInflater.from(context);
        this.scanResultsList = scanResultsList;
        this.context= context;
        // Context: Passed as an argument to access app resources
        // ScanResultsList: List of ScanResult objects to be displayed
        // mInflactor: Initialized with the context for inflating layout files
    }

    @NonNull
    @Override
    public scanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { // Creates views for each list item
        View mItemView = mInflator.inflate(R.layout.device_identity, parent, false); // Inflates device_identity layout into a View object
        return new scanViewHolder(mItemView, this); // 

    }

    @Override
    public void onBindViewHolder(@NonNull scanViewHolder holder, int position) { // Position of the current item in the list
        BluetoothDevice device = scanResultsList.get(position).getDevice(); // Gets current ScanResult and retrieves the current bluetooth device
        String fullAddress = device.getAddress(); // Gets device's Bluetooth MAC address
        @SuppressLint("MissingPermission") String deviceName = device.getName(); // Retrieves device name
        String devID = "";
        if (deviceName.contains("OskronCare") || deviceName.contains("KHCARE")){ // For these exact name
            devID = deviceName.substring(deviceName.length() - 4); // The last 4 digits of the name is used
        } else { // Otherwise extracts a substring from the MAC address
            devID = fullAddress.substring(fullAddress.length() - 5, fullAddress.length() - 3) +
                    fullAddress.substring(fullAddress.length() - 2);
        }
        // Displays unknown if name it not available
        holder.deviceName.setText(deviceName == null ? "Unknown": devID); // Sets device name in the UI
        holder.deviceAddress.setText(fullAddress); // Sets device address in the UI
        ScanRecord scanRecord = scanResultsList.get(position).getScanRecord(); // Retrieves ScanRecord associated with current ScanResult
        // Containing manufacturer-specific data

        if(scanRecord != null){

//            String bytes = bytesToHex(scanRecord.getBytes());
//            String[] values = bytes.split(" ");

            // HR
//            int heart_rate = Integer.parseInt(values[16], 16);
//            if ((heart_rate>0)&&(heart_rate<255)) {
//                holder.currHR.setText(String.format(Locale.US, "%d", heart_rate));
//            }

            // Respiration
//            int res_rate = Integer.parseInt(values[18], 16);
//            if ((res_rate>0)&&(res_rate<45)) {
//                holder.currRR.setText(String.format(Locale.US, "%d", res_rate));
//            }

            // Temperature
//            int temp_h_l = Integer.parseInt(values[24]+values[23], 16);
//            String raw_temp = String.format(Locale.US,"%d", temp_h_l);
//            if(raw_temp.length()==4){
//                String temp = raw_temp.substring(0, 2)+'.'+raw_temp.substring(2);
//                float fTemp = Float.parseFloat(temp);
//                if ((fTemp>=25)&&(fTemp<=45)) {
//                    holder.currTemp.setText(temp);
//                }
//            }

            // SpO2
//            SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
//            for (int i = 0; i < manufacturerData.size(); i++) {
//                int id = manufacturerData.keyAt(i);
//                byte[] obj = manufacturerData.get(id);
//                Log.i("ScanRecord-MFData: ", "id: " + id + " obj: " + bytesToHex(obj));
//                String value_str = bytesToHex(obj);
//                String[] line = value_str.split(" ");
//                int spo2val = Integer.parseInt(line[line.length - 5], 16);
//                if ((spo2val>=70)&&(spo2val<=100)) {
//                    holder.currSpO2.setText(Integer.toString(spo2val));
//                    holder.row_SpO2.setVisibility(View.VISIBLE);
//                }
//                if (spo2val==151){
//                    holder.row_SpO2.setVisibility(View.VISIBLE);
//                }
//            }

            // New method: Manufacturing data only
            SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData(); // Returns a SparseArray showing all manufacturer IDs to raw byte arrays
            int heart_rate = 0;
            for (int i = 0; i < manufacturerData.size(); i++) { // Loops through each entry to extract every parse data linked to manufacturerData
                int id = manufacturerData.keyAt(i);
                byte[] obj = manufacturerData.get(id); // Extracts a specific byte from the manufacturer data
                Log.i("ScanRecord-MFData: ", "id: " + id + " obj: " + bytesToHex(obj)); // Logs manufacturer ID and the byte array (in hexadecimal) for debugging
                String value_str = bytesToHex(obj); // Converts byte array obj into a readable hexadecimal string using bytesToHex utility method
                String[] line = value_str.split(" "); // Splits hexadecimal string into an array of substrings, each substring represents a byte
                int spo2val = Integer.parseInt(line[line.length - 5], 16); // Retrieves the 5th byte from end of array, also converts hexadecimal byte to a decimal integer (spo2Val)
                if ((spo2val>=70)&&(spo2val<=100)) { // Ensures that data is the correct value, within a valid range
                    holder.currSpO2.setText(Integer.toString(spo2val));
                    holder.row_SpO2.setVisibility(View.VISIBLE); // Make spo2val visisble in the UI
                }
                if (spo2val==151){ // spo2val is still displayed even if out of original range at 151
                    holder.row_SpO2.setVisibility(View.VISIBLE);
                }

                heart_rate = Integer.parseInt(line[3], 16); // Retrieves 4th byte from the start and converts to decimal integer (heart_rate)
                if ((heart_rate>0)&&(heart_rate<255)) { // Displays value in holder.currHR only is in range between 1 and 254
                    holder.currHR.setText(String.format(Locale.US, "%d", heart_rate));
                }

                int res_rate = Integer.parseInt(line[5], 16); // Retrieves 6th byte from start, converts in to a decimal integer(res_rate)
                if ((res_rate>0)&&(res_rate<45)) { // Displays value in holder.cuurrRR only is in range 1-44
                    holder.currRR.setText(String.format(Locale.US, "%d", res_rate));
                }

                int temp_h_l = Integer.parseInt(line[11]+line[10], 16); // CGrabs the 11th and 12th bytes, converts string to a decimal integer(temp_h_1)
                String raw_temp = String.format(Locale.US,"%d", temp_h_l);
                Log.d("values:", line[11]+line[10]);
                Log.d("raw_temp:", raw_temp); // Logs raw temp data for debugging
                if(raw_temp.length()==4){ // Ensure raw temp string has 4 digits
                    String temp = raw_temp.substring(0, 2)+'.'+raw_temp.substring(2); // Inserts decimal point after first 2 digits
                    float fTemp = Float.parseFloat(temp); // Convert to float(fTemp)
                    if ((fTemp>=25)&&(fTemp<=45)) { // Displays value in holder.currTemp only if in range 25-45
                        holder.currTemp.setText(temp);
                    }
                }
            }


            // Final check: don't show anything when heart_rate is 255 or 0
            if(heart_rate==255 || heart_rate==0){ 
                holder.currHR.setText("-");
                holder.currRR.setText("-");
                holder.currTemp.setText("-");
                holder.currSpO2.setText("-");
            }

            // Update language:
            int language = MainActivity.getLanguage();
            switch (language){
                case 1:
                    holder.label_deviceName.setText(R.string.device_id_1);
                    holder.label_deviceAddress.setText(R.string.device_address_1);
                    holder.label_currHR.setText(R.string.device_heart_rate_1);
                    holder.label_currTemp.setText(R.string.device_temperature_1);
                    holder.label_currRR.setText(R.string.device_respiration_1);
                    holder.label_currSpO2.setText(R.string.device_spo2_1);
                    break;
                case 2:
                    holder.label_deviceName.setText(R.string.device_id_2);
                    holder.label_deviceAddress.setText(R.string.device_address_2);
                    holder.label_currHR.setText(R.string.device_heart_rate_2);
                    holder.label_currTemp.setText(R.string.device_temperature_2);
                    holder.label_currRR.setText(R.string.device_respiration_2);
                    holder.label_currSpO2.setText(R.string.device_spo2_2);
                    break;
                case 3:
                    holder.label_deviceName.setText(R.string.device_id_3);
                    holder.label_deviceAddress.setText(R.string.device_address_3);
                    holder. label_currHR.setText(R.string.device_heart_rate_3);
                    holder.label_currTemp.setText(R.string.device_temperature_3);
                    holder.label_currRR.setText(R.string.device_respiration_3);
                    holder.label_currSpO2.setText(R.string.device_spo2_3);
                    break;
            }
        }
    }

    @Override
    public int getItemCount() { // Tells the RecyclerView how many items are in the data set
        return scanResultsList.size(); // RecyclerView uses this number to determine how many rows it needs to display
    }
    // Each scanViewHolder instant represents a single item in the RecyclerView(single row in the list)
    class scanViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{

        // UI elements that will show the labels or title for the data
        // Labels
        public final TextView label_deviceName;
        public final TextView label_deviceAddress;
        public final TextView label_currHR;
        public final TextView label_currTemp;
        public final TextView label_currRR;
        public final TextView label_currSpO2;
        public final TableRow row_SpO2;

        // Values
        public final TextView deviceName;
        public final TextView deviceAddress;
        public final TextView currHR;
        public final TextView currTemp;
        public final TextView currRR;
        public final TextView currSpO2;

        final scanResultAdapter mAdapter;

        public scanViewHolder(View itemView, scanResultAdapter adapter){
            super(itemView);
            // Finds the specific view in the itemView layout by their IDs and assigns them to the mumber variable
            label_deviceName = itemView.findViewById(R.id.label_device_name);
            label_deviceAddress = itemView.findViewById(R.id.label_device_address);
            label_currHR = itemView.findViewById(R.id.label_device_heart_rate);
            label_currTemp = itemView.findViewById(R.id.label_device_temperature);
            label_currRR = itemView.findViewById(R.id.label_device_respiration);
            label_currSpO2 = itemView.findViewById(R.id.label_device_spo2);

            
            deviceName = itemView.findViewById(R.id.value_device_name);
            deviceAddress = itemView.findViewById(R.id.value_device_address);
            currHR = itemView.findViewById(R.id.value_device_heart_rate);
            currTemp = itemView.findViewById(R.id.value_device_temperature);
            currRR = itemView.findViewById(R.id.value_device_respiration);
            currSpO2 = itemView.findViewById(R.id.value_device_spo2);

            row_SpO2 = itemView.findViewById(R.id.spo2_table_row);

            this.mAdapter = adapter; // Stores a reference to the adapter that holds the data for the RecyclerView, repsonsible for providing data to the Recycler View
            itemView.setOnClickListener(this); // Handles click events for the entire item view
        }

        @Override
        public void onClick(View v) { 
            int mPosition = getLayoutPosition(); // 
            ScanResult element = scanResultsList.get(mPosition);

            Intent intent = new Intent(context, BleOperations.class);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, element.getDevice());
            intent.putExtra("currHR", currHR.getText().toString());
            intent.putExtra("currRR", currRR.getText().toString());
            intent.putExtra("currTemp", currTemp.getText().toString());
            intent.putExtra("currSpO2",currSpO2.getText().toString());
            context.startActivity(intent);
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

}

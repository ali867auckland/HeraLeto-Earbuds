package com.wbd101.hlt010;

import android.content.Context; // Provides global information about an application environment
import android.util.Log; // For logging debug or error messages
import android.view.LayoutInflater; // Used to create a view from a XML layout file
import android.view.View; // The base class for Android UI components
import android.view.ViewGroup; // Special view component
import android.widget.TableRow; // Represents a row in a tableLayout
import android.widget.TextView; // Represents a text display element in the UI

import androidx.annotation.NonNull; // Annotations that ensures null safety
import androidx.recyclerview.widget.RecyclerView; // Flexible view for efficiently displaying large datasets, designed to replace ListView

import java.util.LinkedList; // A double linked list implementation

public class historyResultAdapter extends RecyclerView.Adapter<historyResultAdapter.scanViewHolder> {

    private final LinkedList<String> scanResultsList;
    private final LinkedList<String> hrResultsList;
    private final LinkedList<String> tempResultsList;
    private final LinkedList<String> respResultsList;
    private final LinkedList<String> spo2ResultsList;
    private final LayoutInflater mInflator;
    private final Context context;
    private final String TAG = "historyResultAdapter";

    public historyResultAdapter(Context context,
                                LinkedList<String> scanResultsList,
                                LinkedList<String> hrResultsList,
                                LinkedList<String> tempResultsList,
                                LinkedList<String> respResultsList,
                                LinkedList<String> spo2ResultsList)
    {
        this.mInflator = LayoutInflater.from(context);
        this.scanResultsList = scanResultsList;
        this.hrResultsList = hrResultsList;
        this.tempResultsList = tempResultsList;
        this.respResultsList = respResultsList;
        this.spo2ResultsList = spo2ResultsList;
        this.context= context;
    }

    @NonNull
    @Override // Creates a new view holder when a new item(row) needs to be displayed in the RecyclerView
    public historyResultAdapter.scanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View mItemView = mInflator.inflate(R.layout.device_history, parent, false);
        return new historyResultAdapter.scanViewHolder(mItemView, this);
    }


    // What to do when a history file is to be loaded:
    @Override
    public void onBindViewHolder(@NonNull historyResultAdapter.scanViewHolder holder, int position) {
        String fileName = scanResultsList.get(position);
        Log.d(TAG, "onBindViewHolder: fileName = " + fileName);

        // Get the device ID:
        String[] fSplit = fileName.split("__");
        holder.deviceName.setText(fSplit[0]);

        // Extract the date:
        String[] dateSplit = fSplit[1].substring(0,10).split("_");
        if (dateSplit.length == 3) {
            holder.fileDate.setText(dateSplit[0] + "/" + dateSplit[1] + "/" + dateSplit[2]);
        } else {
            holder.fileDate.setText(dateSplit[0]);
        }

        // Extract the time:
        String[] timeSplit = fSplit[1].substring(11).split("_");
        if (timeSplit.length == 3) {
            holder.fileTime.setText(timeSplit[0] + ":" + timeSplit[1] + ":" + timeSplit[2]);
        } else {
            holder.fileTime.setText(timeSplit[0]);
        }

        // Extract the HR data:
        String[] hSplit = hrResultsList.get(position).split("_");
        holder.minHR.setText(hSplit[0]);
        holder.avgHR.setText(hSplit[1]);
        holder.maxHR.setText(hSplit[2]);

        // Extract the Temperature data:
        String[] tSplit = tempResultsList.get(position).split("_");
        String tval1 = tSplit[0];
        if (tval1.length()>5){
            tval1 = tval1.substring(0,4);
        }
        String tval2 = tSplit[1];
        if (tval2.length()>5){
            tval2 = tval2.substring(0,4);
        }
        String tval3 = tSplit[2];
        if (tval3.length()>5){
            tval3 = tval3.substring(0,4);
        }
        holder.minTemp.setText(tval1);
        holder.avgTemp.setText(tval2);
        holder.maxTemp.setText(tval3);

        // Extract the respiration data:
        String[] rSplit = respResultsList.get(position).split("_");
        holder.minRR.setText(rSplit[0]);
        holder.avgRR.setText(rSplit[1]);
        holder.maxRR.setText(rSplit[2]);

        // Extract the SpO2 data:
        String[] sSplit = spo2ResultsList.get(position).split("_");
        holder.minO2.setText(sSplit[0]);
        holder.avgO2.setText(sSplit[1]);
        holder.maxO2.setText(sSplit[2]);

        // Show the SpO2 row if values are non-zero:
        if (!sSplit[1].equals("0")){
            holder.spo2_row.setVisibility(View.VISIBLE);
            ViewHistory.displaySpO2();
        }

        // Set language:
        int language = MainActivity.getLanguage();
        switch(language){
            case 1:
                holder.label_devName_h.setText(R.string.device_id_1);
                holder.label_DT_h.setText(R.string.h_dt_1);
                holder.label_HR_h.setText(R.string.h_device_heart_rate_1);
                holder.label_T_h.setText(R.string.h_device_temperature_1);
                holder.label_R_h.setText(R.string.h_device_respiration_1);
                holder.label_S_h.setText(R.string.h_device_spo2_1);
                holder.label_min_h.setText(R.string.h_min_1);
                holder.label_max_h.setText(R.string.h_max_1);
                holder.label_avg_h.setText(R.string.h_avg_1);
                break;
            case 2:
                holder.label_devName_h.setText(R.string.device_id_2);
                holder.label_DT_h.setText(R.string.h_dt_2);
                holder.label_HR_h.setText(R.string.h_device_heart_rate_2);
                holder.label_T_h.setText(R.string.h_device_temperature_2);
                holder.label_R_h.setText(R.string.h_device_respiration_2);
                holder.label_S_h.setText(R.string.h_device_spo2_2);
                holder.label_min_h.setText(R.string.h_min_2);
                holder.label_max_h.setText(R.string.h_max_2);
                holder.label_avg_h.setText(R.string.h_avg_2);
                break;
            case 3:
                holder.label_devName_h.setText(R.string.device_id_3);
                holder.label_DT_h.setText(R.string.h_dt_3);
                holder.label_HR_h.setText(R.string.h_device_heart_rate_3);
                holder.label_T_h.setText(R.string.h_device_temperature_3);
                holder.label_R_h.setText(R.string.h_device_respiration_3);
                holder.label_S_h.setText(R.string.h_device_spo2_3);
                holder.label_min_h.setText(R.string.h_min_3);
                holder.label_max_h.setText(R.string.h_max_3);
                holder.label_avg_h.setText(R.string.h_avg_3);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return scanResultsList.size();
    }


    class scanViewHolder extends RecyclerView.ViewHolder{

        // Text labels
        public final TextView label_devName_h;
        public final TextView label_DT_h;
        public final TextView label_HR_h;
        public final TextView label_T_h;
        public final TextView label_R_h;
        public final TextView label_S_h;
        public final TextView label_min_h;
        public final TextView label_max_h;
        public final TextView label_avg_h;
        public final TableRow spo2_row;

        // Values
        public final TextView deviceName;
        public final TextView fileDate;
        public final TextView fileTime;
        public final TextView minHR;
        public final TextView maxHR;
        public final TextView avgHR;
        public final TextView minTemp;
        public final TextView maxTemp;
        public final TextView avgTemp;
        public final TextView minRR;
        public final TextView maxRR;
        public final TextView avgRR;
        public final TextView minO2;
        public final TextView maxO2;
        public final TextView avgO2;

        final historyResultAdapter mAdapter;

        public scanViewHolder(View itemView, historyResultAdapter adapter){
            super(itemView);

            // Labels
            label_devName_h = itemView.findViewById(R.id.label_device_name_h);
            label_DT_h = itemView.findViewById(R.id.label_datetime_h);
            label_HR_h = itemView.findViewById(R.id.label_heart_rate_h);
            label_T_h = itemView.findViewById(R.id.label_temperature_h);
            label_R_h = itemView.findViewById(R.id.label_respiration_h);
            label_S_h = itemView.findViewById(R.id.label_spo2_h);
            label_min_h = itemView.findViewById(R.id.label_min_h);
            label_max_h = itemView.findViewById(R.id.label_max_h);
            label_avg_h = itemView.findViewById(R.id.label_avg_h);

            // Values
            deviceName = itemView.findViewById(R.id.value_device_name_h);
            fileDate = itemView.findViewById(R.id.history_date);
            fileTime = itemView.findViewById(R.id.history_time);
            minHR = itemView.findViewById(R.id.min_hr_val);
            maxHR = itemView.findViewById(R.id.max_hr_val);
            avgHR = itemView.findViewById(R.id.avg_hr_val);
            minTemp = itemView.findViewById(R.id.min_temp_val);
            maxTemp = itemView.findViewById(R.id.max_temp_val);
            avgTemp = itemView.findViewById(R.id.avg_temp_val);
            minRR = itemView.findViewById(R.id.min_resp_val);
            maxRR = itemView.findViewById(R.id.max_resp_val);
            avgRR = itemView.findViewById(R.id.avg_resp_val);
            minO2 = itemView.findViewById(R.id.min_spo2_val);
            maxO2 = itemView.findViewById(R.id.max_spo2_val);
            avgO2 = itemView.findViewById(R.id.avg_spo2_val);

            spo2_row = itemView.findViewById(R.id.spo2_history_row);

            this.mAdapter = adapter;
        }
    }

}

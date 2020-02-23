package com.example.mobilenoiseclassifier;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import be.tarsos.dsp.io.android.AndroidFFMPEGLocator;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    // TFLite model variables
    float[] feature_vector  = new float[10025];
    String[] labels         = {"Horn", "Crowd", "Dog", "Siren", "Traffic"};
    float[][] output        = new float[1][NUM_CLASSES];

    // Bar Chart properties
    ArrayList<BarEntry> entries = new ArrayList<>();
    BarChart barChart;
    BarDataSet barDataSet;
    BarData barData;
    XAxis xAxis;

    // ListView properties
    public String item  = "Car Horn";
    ListView listView;
    String[] strings    = {"Car Horn", "Crowd", "Dog Bark", "Siren", "Vehicles", "Mixed"};

    // Buttons
    Button button_file, button_mic;

    private static final int NUM_CLASSES    = 5;

    /*
    Permissions properties.
    Random request code and string array of permissions needed.
     */
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    String[] app_permissions    = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // The custom list
        CustomList customList   = new CustomList(MainActivity.this, strings);

        listView    = findViewById(R.id.list);
        button_file = findViewById(R.id.button_file);
        button_mic  = findViewById(R.id.button_mic);
        barChart    = findViewById(R.id.bar_chart);

        // Set up the list and the click listener
        listView.setAdapter(customList);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                item    = strings[i];
                Log.i(TAG, "Item clicked: " + item);
            }
        });

        /*
        This method is to find ffmpeg library in our assets folder.
        TarsosDSP will utilize this library to process our audio file either recorded or samples.
         */
        new AndroidFFMPEGLocator(this);

        // Declare the extractor class as a new object for us to use.
        final Extractor extractor   = new Extractor();

        // Check the needed directory
        extractor.pathChecker();

        /*
        It's better to create a new thread to execute the extraction process.
        MFCC extraction is intense and if we force to use it on the same thread,
        the UI won't response anymore due to the low throughput.
         */
        final Thread mfccThread     = new Thread(new Runnable() {
            @Override
            public void run() {
                extractor.mfcc();
            }
        });

        final Thread classifyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    classify();
                    draw_chart(output);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        button_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*
                Make sure to ask for permission from user before the program start.
                Also check for the existence of the file path.
                */
                if (checkAndRequestPermission()) {

                    // Initialize the dispatcher to prepare the properties needed before extraction.
                    extractor.initDispatcher_file(item);

                    // Run the thread.
                    mfccThread.run();

                    // Make sure everything is extracted already, otherwise, wait for 1ms
                    if (!extractor.isDone)
                    {
                        try {
                            TimeUnit.MILLISECONDS.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    else{
                        try {
                            int counter = 0;
                            while (!extractor.mfccs_BQ.isEmpty())
                            {
                                //int list_counter    = 0;
                                float[] tmp = extractor.mfccs_BQ.take();
                                for (float tmp_element : tmp) {
                                    feature_vector[counter] = tmp_element;
                                    counter++;
                                }
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    classifyThread.run();
                }
                extractor.killDispatcher();
                classifyThread.interrupt();
            }
        });

        button_mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*
                Make sure to ask for permission from user before the program start.
                Also check for the existence of the file path.
                */
                if (checkAndRequestPermission()) {

                    // Initialize the dispatcher to prepare the properties needed before extraction.
                    extractor.initDispatcher_mic();

                    // Run the thread.
                    mfccThread.start();

                    new CountDownTimer(3000, 10) {
                        @Override
                        public void onTick(long l) {
                            /*
                            if (!extractor.mfccs_BQ.isEmpty())
                            {
                                try {
                                    int counter = 0;
                                    float[] received_features   = extractor.mfccs_BQ.take();
                                    for (float feature : received_features) {
                                        feature_vector[counter] = feature;
                                        counter++;
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                classifyThread.run();
                            }
                             */
                        }

                        @Override
                        public void onFinish() {

                            if (!extractor.mfccs_BQ.isEmpty())
                            {
                                try {
                                    int counter = 0;
                                    float[] received_features   = extractor.mfccs_BQ.take();
                                    Log.d(Thread.currentThread().getName(), "Feature: " + received_features[0]);
                                    for (float feature : received_features) {
                                        feature_vector[counter] = feature;
                                        counter++;
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                classifyThread.run();
                            }

                            mfccThread.interrupt();
                            extractor.killDispatcher();
                        }
                    }.start();
                }
            }
        });
    }

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button is checked
        switch (view.getId()) {
            case R.id.radio_file :
                if (checked) {
                    listView.setVisibility(View.VISIBLE);
                    button_mic.setVisibility(View.GONE);
                    button_file.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.radio_mic :
                if (checked) {
                    listView.setVisibility(View.GONE);
                    button_file.setVisibility(View.GONE);
                    button_mic.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    private void classify() throws IOException {

        // Handle the graph model
        Interpreter interpreter = new Interpreter(model_loader());

        ByteBuffer byteBuffer   = matrix_to_byte(feature_vector);

        // Run the graph
        interpreter.run(byteBuffer, output);

        // Close interpreter
        interpreter.close();
    }

    private void draw_chart(float[][] output) {
        entries.clear();
        for (int i = 0; i < NUM_CLASSES; i++)
            entries.add(new BarEntry(i, output[0][i]));

        xAxis   = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        barDataSet  = new BarDataSet(entries, "");

        /*
         1. Assign the labels and data entries to the data collection
         2. Set the data collection to chart
         3. Set the color set
         */
        barData = new BarData(barDataSet);
        barChart.setData(barData);
        barDataSet.setColors(ColorTemplate.COLORFUL_COLORS);
        barChart.invalidate();

        // Styling
        barChart.setDrawGridBackground(false);
        barChart.getAxisLeft().setTextColor(ColorTemplate.rgb("#FFFFFF"));
        barChart.getAxisLeft().setTypeface(Typeface.DEFAULT_BOLD);
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setTextColor(ColorTemplate.rgb("#FFFFFF"));
        barChart.getXAxis().setTypeface(Typeface.DEFAULT_BOLD);
        barChart.getXAxis().setLabelCount(5);
        barChart.getLegend().setEnabled(false);
    }

    private ByteBuffer matrix_to_byte(float[] vector) {
        ByteBuffer data = ByteBuffer.allocateDirect(feature_vector.length * 4);
        data.order(ByteOrder.nativeOrder());
        data.rewind();
        for (float element : vector) {
            data.putFloat(element);
        }
        return data;
    }

    /**
     * This function is for asking for permission from user which is needed for first time install.
     */
    private boolean checkAndRequestPermission() {
        // Check which permissions are granted
        List<String> permission_needed = new ArrayList<>();
        for (String permission : app_permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
            {
                permission_needed.add(permission);
            }
        }

        // Ask for non-granted permissions
        if (!permission_needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permission_needed.toArray(new String[0]),
                    PERMISSIONS_REQUEST_CODE);

            return false;
        }

        // All permissions granted; PROCEED.
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            HashMap<String, Integer> permission_results = new HashMap<>();
            int denied_count    = 0;

            // Gather the granted permission results
            for (int i = 0; i < grantResults.length; i++) {
                // Add only the needed permissions
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    permission_results.put(permissions[i], grantResults[i]);
                    denied_count++;
                }
            }

            // Check if all permissions are granted
            if (denied_count == 0) {
                Log.i(TAG, "ALL PERMISSIONS ARE GRANTED");
            }

            // If there are any not granted permission.
            else {
                for (Map.Entry<String, Integer> entry : permission_results.entrySet()) {
                    String permission_name  = entry.getKey();
                    int permissions_result  = entry.getValue();

                    /*
                    Permission is denied (the "never ask again" is not checked).
                    Ask again and explain the reason of the needed permissions.
                    shouldShowRequestPermissionRationale will return true.
                     */
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission_name))
                    {
                        // Show explanation dialog.
                        showDialog("", "Recording audio and Read/Write access to memory is needed.",
                                "Grant Permissions.", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                        checkAndRequestPermission();
                                    }
                                }, "Exit Application", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                        finish();
                                    }
                                }, false);
                        break;
                    }
                }
            }
        }
    }

    /**
     * This AlertDialog will show up to notify the user the importance of granting the permissions.
     *
     * @param title shows the title of the AlertDialog
     * @param message tells the users about the needs
     * @param positive_label is the positive button label
     * @param positive_click is the positive button action
     * @param negative_label is the negative button label
     * @param negative_click is the negative button action
     * @param is_cancel_able tells if this AlertDialog is cancelable or not
     * @return the AlertDialog
     */
    private AlertDialog showDialog(String title, String message, String positive_label,
                                   DialogInterface.OnClickListener positive_click,
                                   String negative_label,
                                   DialogInterface.OnClickListener negative_click,
                                   boolean is_cancel_able) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setCancelable(is_cancel_able);
        builder.setMessage(message);
        builder.setPositiveButton(positive_label, positive_click);
        builder.setNegativeButton(negative_label, negative_click);

        AlertDialog alert   = builder.create();
        alert.show();
        return alert;
    }

    /**
     * Memory-map the model file in assets folder.
     * @return Mapped model file which will be passed to TFLite interpreter.
     * @throws IOException throws the error if we can't read nor access the model file.
     */
    private MappedByteBuffer model_loader() throws IOException {
        AssetFileDescriptor assetFileDescriptor = this.getAssets().openFd("classifier_25.tflite");
        FileInputStream fileInputStream     = new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel         = fileInputStream.getChannel();

        long start_offset       = assetFileDescriptor.getStartOffset();
        long declared_length    = assetFileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, start_offset, declared_length);
    }
}

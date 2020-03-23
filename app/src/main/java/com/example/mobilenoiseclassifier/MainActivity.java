/*
 This code is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the
 Free Software Foundation; either version 3.0 of the License, or (at your
 option) any later version. (See <http://www.gnu.org/copyleft/lesser.html>.)

 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA

 Created by: WIN ROEDILY (roedilywinner@gmail.com)
 */
/*
 This is the main class of the activity; work as an entry point.
 1. It will check for permission to access device's microphone and external storage (read & write).
 2. After the permission is granted, it will check for folder called "CSV" in the device's external
    storage and create one if it was not found.
 3. Then it will copy all the audio files stored in assets folder to the newly created CSV folder.
 4. It will wait for the user input from any clicked button.
 */
package com.example.mobilenoiseclassifier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.ToggleButton;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    // Frame size
    private static final int FRAME_FILE = 612;
    private static final int FRAME_MIC = 102;

    // Source of audio
    private static final int FILE   = 1;
    private static final int MIC    = 2;

    // TFLite model variables
    private static final int NUM_CLASSES    = 5;

    // Audio migration props
    AssetManager assetManager;
    InputStream inputStream;
    OutputStream outputStream;
    String audio_path;
    String[] audio_name = {"horn.wav", "crowd.wav", "dog.wav", "siren.wav", "traffic.wav", "mix.wav"};
    File[] audio_list   = {null, null, null, null, null, null};
    float[] feature_vector;
    float[] mic_feature_vec;

    // ListView properties
    String item  = "car.wav";
    String[] labels         = {"Horn", "Crowd", "Dog", "Siren", "Traffic"};
    float[][] output        = new float[1][NUM_CLASSES];

    // Bar Chart properties
    ArrayList<BarEntry> entries = new ArrayList<>();
    BarChart barChart;
    BarDataSet barDataSet;
    BarData barData;
    XAxis xAxis;

    // Buttons
    RadioButton radio_file, radio_mic;
    ListView listView;
    ToggleButton toggle_save;
    Button button_file;
    ImageButton button_mic, button_stop;
    private int source;

    /*
    Permissions properties.
    Random request code and string array of permissions needed.
     */
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    String[] app_permissions    = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // The custom list
        CustomList customList   = new CustomList(MainActivity.this, audio_name);

        toggle_save = findViewById(R.id.toggle_save);
        listView    = findViewById(R.id.list);
        radio_file  = findViewById(R.id.radio_file);
        radio_mic   = findViewById(R.id.radio_mic);
        button_file = findViewById(R.id.button_file);
        button_mic  = findViewById(R.id.button_mic);
        button_stop = findViewById(R.id.button_stop);
        barChart    = findViewById(R.id.bar_chart);

        // Set up the list and the click listener
        listView.setAdapter(customList);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                item    = audio_name[i];
                Log.i(TAG, "Item clicked: " + item);
            }
        });

        /*
        This method is to find ffmpeg library in our assets folder.
        TarsosDSP will utilize this library to process our audio file either recorded or samples.
         */
        new AndroidFFMPEGLocator(this);

        /*
        Declare the extractor class as a new object for us to use.
        Check the folder existence to save CSV and put audio files.
        Define the size of the vector holders.
        */
        final Extractor extractor   = new Extractor();
        extractor.pathChecker();
        feature_vector = new float[FRAME_FILE * extractor.cepstrum_c];
        mic_feature_vec = new float[FRAME_MIC * extractor.cepstrum_c];


        /*
        ====================================================================================
                                        AUDIO FILE MIGRATION
        ====================================================================================
         */
        Context context = getApplicationContext();
        audio_path  = Environment.getExternalStorageDirectory() + "/CSV/";
        Log.d(TAG, "Migration Target ==> " + audio_path);
        for (int i = 0; i < audio_list.length; i++)
        {
            audio_list[i]   = new File(audio_path + audio_name[i]);
            if (!audio_list[i].exists())
                copy_file(context, audio_name[i]);
        }

        /*
        ====================================================================================
                                        THREADS SECTION
        ====================================================================================
         */
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

        final CountDownTimer countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long l) {
                int counter = 0;
                for (int i = 0; i < 102; i++) {
                    try {
                        float[] received_features = extractor.mfccs_BQ.take();
                        for (float feature : received_features) {
                            mic_feature_vec[counter] = feature;
                            counter++;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                classifyThread.run();
            }

            @Override
            public void onFinish() {
                extractor.killDispatcher();
                if (!extractor.mfccs_BQ.isEmpty()) {
                    try {
                        int counter = 0;
                        float[] received_features = extractor.mfccs_BQ.take();
                        //Log.d(Thread.currentThread().getName(), "Feature: " + received_features[0]);
                        for (float feature : received_features) {
                            mic_feature_vec[counter] = feature;
                            counter++;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    classifyThread.run();
                }
                extractor.mfccs_BQ.clear();
                classifyThread.interrupt();

                button_mic.setVisibility(View.VISIBLE);
                button_mic.setEnabled(true);
                button_mic.setClickable(true);
                button_stop.setVisibility(View.GONE);
                button_stop.setEnabled(false);
                button_stop.setClickable(false);
            }
        };

        /*
        ====================================================================================
                                        ALL BUTTON LISTENERS
        ====================================================================================
         */
        toggle_save.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean is_checked) {
                extractor.save_csv  = is_checked;
                if (is_checked)
                {
                    radio_mic.setClickable(false);
                    radio_file.setChecked(true);
                    listView.setVisibility(View.VISIBLE);
                    button_mic.setVisibility(View.GONE);
                    button_file.setVisibility(View.VISIBLE);
                    source  = FILE;
                }
                else {
                    radio_mic.setClickable(true);
                }
            }
        });

        button_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Disable button after click to avoid double-click
                button_file.setEnabled(false);
                button_file.setClickable(false);

                /*
                Make sure to ask for permission from user before the program start.
                Also check for the existence of the file path.
                */
                if (checkAndRequestPermission()) {
                    // Initialize the dispatcher to prepare the properties needed before extraction.
                    extractor.initDispatcher_file(item);

                    // Create CSV File
                    if (extractor.save_csv)
                        extractor.csv_creator();

                    // Run the thread.
                    mfccThread.run();

                    // Make sure everything is extracted already, otherwise, wait for 1ms
                    while (!extractor.isDone)
                    {
                        try {
                            TimeUnit.MILLISECONDS.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        int counter = 0;
                        while (!extractor.mfccs_BQ.isEmpty())
                        {
                            float[] tmp = extractor.mfccs_BQ.take();
                            for (float tmp_element : tmp) {
                                feature_vector[counter] = tmp_element;
                                counter++;
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (!extractor.save_csv)
                        classifyThread.run();
                }
                extractor.killDispatcher();
                extractor.mfccs_BQ.clear();

                if (!extractor.save_csv)
                    classifyThread.interrupt();

                // Bring the button back
                button_file.setEnabled(true);
                button_file.setClickable(true);
            }
        });

        button_mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Disable button after click to avoid double-click
                button_stop.setVisibility(View.VISIBLE);
                button_stop.setEnabled(true);
                button_stop.setClickable(true);
                button_mic.setVisibility(View.GONE);
                button_mic.setEnabled(false);
                button_mic.setClickable(false);

                /*
                Make sure to ask for permission from user before the program start.
                Also check for the existence of the file path.
                */
                if (checkAndRequestPermission()) {
                    // Initialize the dispatcher to prepare the properties needed before extraction.
                    extractor.initDispatcher_mic();

                    // Run the thread.
                    mfccThread.start();

                    countDownTimer.start();
                }
            }
        });

        button_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                button_mic.setVisibility(View.VISIBLE);
                button_mic.setEnabled(true);
                button_mic.setClickable(true);
                button_stop.setVisibility(View.GONE);
                button_stop.setEnabled(false);
                button_stop.setClickable(false);

                countDownTimer.cancel();

                extractor.killDispatcher();
                extractor.mfccs_BQ.clear();

                classifyThread.interrupt();
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
                    source  = FILE;
                }
                break;
            case R.id.radio_mic :
                if (checked) {
                    listView.setVisibility(View.GONE);
                    button_file.setVisibility(View.GONE);
                    button_mic.setVisibility(View.VISIBLE);
                    source  = MIC;
                }
                break;
        }
    }

    /*
    ====================================================================================
                                   TENSORFLOW LITE SECTION
    ====================================================================================
    */
    private void classify() throws IOException {

        // Handle the graph model
        Interpreter interpreter = new Interpreter(model_loader());

        ByteBuffer byteBuffer;
        if (source == FILE) {
            byteBuffer = matrix_to_byte(feature_vector);
        } else {
            byteBuffer = matrix_to_byte(mic_feature_vec);
        }

        // Run the graph
        interpreter.run(byteBuffer, output);

        // Close interpreter
        interpreter.close();
    }

    private ByteBuffer matrix_to_byte(float[] vector) {
        ByteBuffer data;
        if (source == FILE) {
            data = ByteBuffer.allocateDirect(feature_vector.length * 4);
        } else {
            data = ByteBuffer.allocateDirect(mic_feature_vec.length * 4);
        }
        data.order(ByteOrder.nativeOrder());
        data.rewind();
        for (float element : vector) {
            data.putFloat(element);
        }
        return data;
    }

    /**
     * Memory-map the model file in assets folder.
     * @return Mapped model file which will be passed to TFLite interpreter.
     * @throws IOException throws the error if we can't read nor access the model file.
     */
    private MappedByteBuffer model_loader() throws IOException {
        String path;
        if (source == FILE) {
            path = "classifier_612.tflite";
        } else {
            path = "classifier_102.tflite";
        }
        AssetFileDescriptor assetFileDescriptor = this.getAssets().openFd(path);
        FileInputStream fileInputStream     = new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel         = fileInputStream.getChannel();

        long start_offset       = assetFileDescriptor.getStartOffset();
        long declared_length    = assetFileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, start_offset, declared_length);
    }

    /*
    ====================================================================================
                                   BAR CHART DRAWING
    ====================================================================================
    */
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

    /*
    ====================================================================================
                                   PERMISSION HANDLER
    ====================================================================================
    */
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
                    entry.getValue();

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

    /*
    ====================================================================================
                                   AUDIO MIGRATION HANDLER
    ====================================================================================
    */
    private void copy_file(Context context, String file_name) {
        assetManager    = context.getAssets();
        try {
            inputStream     = assetManager.open(file_name);
            outputStream    = new FileOutputStream(audio_path + file_name);
            byte[] buffer   = new byte[1200000];
            int read        = inputStream.read(buffer);
            while (read != -1) {
                outputStream.write(buffer, 0, read);
                read    = inputStream.read(buffer);
            }
            Log.i(TAG, "File copied ==> " + file_name);
        } catch (IOException e) {
            Log.e(TAG, "copy_file: FAILED");
            e.printStackTrace();
        }
    }
}
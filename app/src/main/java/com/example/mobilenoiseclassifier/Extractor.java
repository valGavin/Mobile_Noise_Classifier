package com.example.mobilenoiseclassifier;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;

class Extractor {

    // Log report TAG
    private String TAG  = "Extractor";

    // Process counter and status
    private int counter_process = 0;
    private int counter_writer  = 0;
    boolean isDone  = false;

    // BlockingQueue to store features
    BlockingQueue<float[]> mfccs_BQ = new LinkedBlockingQueue<>(1024);

    // PrintWriter for writing to CSV file
    private PrintWriter printWriter = null;

    // Declare the AudioDispatcher value
    private AudioDispatcher audioDispatcher = null;

    // Properties for AudioDispatcher
    private int sample_rate = 8000;
    private int size        = 400;
    private int overlap     = 281;
    private int mic_size    = 640;
    private int mic_overlap = 527;

    // Properties for MFCC Extraction
    private int frame_size;                                 // Buffer size
    private int cepstrum_c  = 25;                           // Number of MFCC to extract per frame
    private int melFilter   = 25;                           // Points for FilterBank
    private float low_freq  = 0f;                           // Lower frequency
    private float hi_freq   = ((float)sample_rate)/2f;      // High frequency is half the sample rate

    // Thread for save the extracted features to CSV file.
    private final Thread saver    = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                saveToCSV();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    });

    @SuppressLint("SimpleDateFormat")
    private
    SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    /**
     * Initialize the AudioDispatcher to capture from file
     */
    void initDispatcher_file(String item)
    {
        Log.i(TAG, "Create Dispatcher: INITIALIZE");
        audioDispatcher = AudioDispatcherFactory.fromPipe(
                "/storage/emulated/0/CSV/" + item + ".wav",
                sample_rate, size, overlap);
        frame_size  = size;
        Log.i(TAG, "Create Dispatcher: COMPLETE");
    }

    /**
     * Initialize the AudioDispatcher to capture from default microphone
     */
    void initDispatcher_mic()
    {
        Log.i(TAG, "Create Dispatcher: INITIALIZE");
        audioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sample_rate, mic_size, mic_overlap);
        frame_size  = mic_size;
        Log.i(TAG, "Create Dispatcher: COMPLETE");
    }

    /**
     * This function is a function where MFCC extraction and storing to BlockingQueue happens.
     */
    void mfcc()
    {
        Log.d(TAG, "Create MFCC Object: INITIALIZE");
        final MFCC mfccs  = new MFCC(frame_size, sample_rate, cepstrum_c, melFilter, low_freq, hi_freq);
        Log.d(TAG, "Create MFCC Object: COMPLETE");

        Log.d(TAG, "Assign MFCC to Audio Processor: INITIALIZING");
        audioDispatcher.addAudioProcessor(mfccs);
        Log.d(TAG, "Assign MFCC to Audio Processor: COMPLETE");

        Log.d(Thread.currentThread().getName(), "Audio Processor: CALLING");
        Log.i(TAG, "Time: " + dateFormat.format(new Date()));
        audioDispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                counter_process++;
                if (counter_process > 802)
                    return false;
                // Log.d(Thread.currentThread().getName(), "Get MFCC: EXTRACTING (" + counter_process + ")");
                try {
                    mfccs_BQ.put(mfccs.getMFCC());
                    // saver.run();
                } catch (Exception e) {
                    Log.e(Thread.currentThread().getName(), "Get MFCC: FAILED");
                    e.printStackTrace();
                }

                //Log.d(Thread.currentThread().getName(), "Get MFCC: COMPLETE");

                return true;
            }

            @Override
            public void processingFinished() {
                Log.i(TAG, "Time: " + dateFormat.format(new Date()));
                Log.d(Thread.currentThread().getName(), "Get MFCC: COMPLETE\n" +
                        "\tProcess: " + counter_process +
                        "\n\tWriter: " + counter_writer);
                isDone = true;
            }
        });

        audioDispatcher.run();
    }

    private void padding() {
        counter_writer++;
        if (counter_writer <= 401)
        {
            Log.d(Thread.currentThread().getName(), "Writing to CSV file: PADDING (" + counter_writer + ")");
            for (int i = 0; i < cepstrum_c; i++)
                printWriter.print("0, ");
            printWriter.print("\r\n");
            printWriter.flush();
        }
    }

    /**
     * This function is to save our extracted features which is stored in BlockingQueue to a CSV file.
     */
    private void saveToCSV() throws InterruptedException {
        counter_writer++;
        float[] receivedFeatures    = mfccs_BQ.take();

        // Log.d(Thread.currentThread().getName(), "Writing to CSV file: WRITING (" + counter_writer + ")");
        for (float element : receivedFeatures)
        {
            //Log.i(TAG, "Writing elements: WRITING");
            printWriter.print(element + ", ");
            //Log.i(TAG, "Writing elements: COMPLETE");
        }
        printWriter.print("\r\n");
        printWriter.flush();
    }

    /**
     * This function is to check the path to save the CSV file whether it's exist or not.
     */
    void pathChecker() {
        String CSV_Path = "/storage/emulated/0/CSV";
        String CSV_File;

        Log.d(TAG, "Creating new file: INITIALIZING");
        File CSV_Dir = new File(CSV_Path);
        Log.d(TAG, "Creating new file: COMPLETE");

        Log.d(TAG, "Checking file path: CHECKING");
        boolean fileExistence = CSV_Dir.exists();
        if (!fileExistence) {
            Log.d(TAG, "Folder not found: CREATING");
            fileExistence = CSV_Dir.mkdirs();
            Log.d(TAG, "Folder created");
        }
        if (fileExistence) {
            Log.i(TAG, "Folder found");
            try {
                CSV_File = CSV_Dir.toString() + File.separator + "features_8kHz_25.csv";

                Log.d(TAG, "Create PrintWriter: CREATING");
                printWriter = new PrintWriter(new FileWriter(CSV_File, false));
                Log.d(TAG, "Create PrintWriter: COMPLETE --> " + CSV_File);
            } catch (IOException e) {
                Log.e(TAG, "Create PritWriter: FAILED");
                e.printStackTrace();
            }
        }
    }

    /**
     * This is a function to terminate the running AudioDispatcher to make sure no extraction is
     * running anymore.
     * It also interrupt the saverThread and close the PrintWriter.
     */
    void killDispatcher() {
        Log.d(TAG, "Kill Dispatcher: TERMINATING");
        audioDispatcher.stop();
        audioDispatcher = null;
        mfccs_BQ.clear();
        Log.d(TAG, "Kill Dispatcher: COMPLETE");
    }
}

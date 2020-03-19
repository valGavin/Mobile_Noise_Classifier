# Mobile_Noise_Classifier
A noise classifier on Android smartphones.

This project is a simple Android application that classifies the incoming audio signals into five classes, namely:
  * Car Horn
  * Crowd
  * Dog Bark
  * Siren
  * Vehicles

The classification process runs by utilizing the TensorFlow Lite model where it gets the input feature vector from the `Extractor.java`
class by utilizing the TarsosDSP library.
> The details on how to get the TensorFlow Lite model is available on https://github.com/WinRoedily/NoiseClassifier_TF1

This application gets the audio signals from either the phone's default microphone or a .wav file. It extracts the MFCCs from the
sampled audio waves and pass the result either to TensorFlow Lite `Interpreter` for classification or `saveToCSV` to save it to .csv file.
For the classification of the audio coming from microphone, the program calls the `classify` method every second, while the classification
of a .wav file runs as the extraction process finished. I use six .wav files with six seconds in duration for each file as samples where each of the five files
represents a class according to the labels I provide, and the sixth file is a mix of those five classes.

## Here's how it works ##
1. The app requires input from the user to choose the audio source:
  * From File
    * horn.wav
    * crowd.wav
    * dog.wav
    * siren_wav
    * traffic.wav
    * mix.wav
  * From Microphone
2. If you select the **From File** radio button, it shows the list of available audio files in the application. Select one of those and click on select file
3. If you select the **From Microphone** radio button, you need to press on the Start button to start recording and classifying sequentially

## Here's how it looks like ##
|Start|From File|From Mic|
|:---:|:-------:|:------:|
|<img src="https://github.com/WinRoedily/Mobile_Noise_Classifier/blob/master/pics/start.jpeg" width="270" height="480" allign="middle" />|<img src="https://github.com/WinRoedily/Mobile_Noise_Classifier/blob/master/pics/file.jpeg" width="270" height="480" allign="middle" />|<img src="https://github.com/WinRoedily/Mobile_Noise_Classifier/blob/master/pics/mic.jpeg" width="270" height="480" allign="middle" />|

I hope this simple code can be useful, and may God bless you. :angel:

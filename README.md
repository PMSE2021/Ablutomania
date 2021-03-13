# Ablutomania
Wear OS application for evaluating and recording inertial motion data and classifying excessive hand washing as a occurrence of Obsessive-Compulsive Disorder (OCD).

## Usage
### Sensor
The following sensors are required to run the application on Wear OS:

	- gyroscope
	- accelerometer
	- magnetic field
	- rotation vector

All sensors are recorded and processed at 50Hz.

### Detection of OCD
 All sensor values are divided into blocks of 10s each and evaluated by a TenserFlow Lite Convolutional Neural Network with Long Short-Term Memory (CNN LSTM). The classification of the movement is returned as the result: Everyday movement (0), Hand washing (-1) or Compulsive hand washing (1). If the result differs from everyday movements, the user is additionally asked whether the classification of the machine learning model is correct.
 The sensor streams are extended by the classification and recorded.


### Recording
Data is stored locally on device in matroska/mkv format. FFMpeg is used to compress this data by the wavpack codec. Android sensors as well as other streams can be recorded and compressed in a single file. Metadata like recording date, sample rate, sample format etc. is stored side-by-side with the data streams.

    ffprobe "2021-03-11T23_48+0000_Ablutomania_1234567890.mkv"
    Input #0, matroska,webm, from '2021-03-11T23_48+0000_Ablutomania_1234567890.mkv':
      Metadata:
        BEGINNING       : 2021-03-11T23:48Z
        RECORDER        : Ablutomania 1.0
        ANDROID_ID      : 
        PLATFORM        : mooneye mooneye 26
        FINGERPRINT     : 
        ENCODER         : Lavf57.39.102
      Duration: 00:01:50.00, start: 0.000000, bitrate: 31 kb/s
        Stream #0:0: Audio: wavpack, 50 Hz, 5.0, fltp (default)
        Metadata:
          NAME            : ROTATION_VECTOR_SENSOR
          ENCODER         : Lavc57.46.100 wavpack
          DURATION        : 00:01:50.000000000
        Stream #0:1: Audio: wavpack, 50 Hz, 2.1, fltp (default)
        Metadata:
          NAME            : ACCELEROMETER_SENSOR
          ENCODER         : Lavc57.46.100 wavpack
          DURATION        : 00:01:50.000000000
        Stream #0:2: Audio: wavpack, 50 Hz, 2.1, fltp (default)
        Metadata:
          NAME            : GYROSCOPE_SENSOR
          ENCODER         : Lavc57.46.100 wavpack
          DURATION        : 00:01:50.000000000
        Stream #0:3: Audio: wavpack, 50 Hz, 2.1, fltp (default)
        Metadata:
          NAME            : MAGNETIC_FIELD_SENSOR
          ENCODER         : Lavc57.46.100 wavpack
          DURATION        : 00:01:50.000000000
        Stream #0:4: Audio: wavpack, 50 Hz, mono, fltp (default)
        Metadata:
          NAME            : ML_RESULT
          ENCODER         : Lavc57.46.100 wavpack
          DURATION        : 00:01:50.000000000

## Reference
Reference project for a sensor recorder see: [automotion by pscholl](https://github.com/pscholl/automotion).

OCD dataset and recorded file organization see: [Ablutomania-Set, University of Basel and University of Freiburg](https://earth.informatik.uni-freiburg.de/uploads/handwashing/).

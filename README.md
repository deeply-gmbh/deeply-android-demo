# Deeply Android Demo

This simple demo project shows how to use deeply with our Java wrapper under Android.

## Project setup

### Deeply SDK

First things first. After cloning this repo to your home dir you need to copy your
Deeply shared library, the java wrapper shared library and the deeply.jar
to the libs and jniLibs directories:

```
cp ~/deeply-java-v${VERSION}/deeply.jar ~/deeply-android-demo/app/libs/

cp ~/deeply-java-v${VERSION}/libdeeply*.so ~/deeply-android-demo/app/src/main/jniLibs/armeabi-v7a/
```

Your project tree should look like this:

![](doc/deeply-libs-overview.png)

### Run

Simply use Android Studio to start the app on your device.

## Overview components

### MainActivity

The MainActivity does the basic things in the onCreate() method
 * Creating the de.deeply.Processor instance
 * Enrolling the known users for face recognition module
 * Initializing the CameraFragment

### CameraFragment and OnImageAvailableListener

The CameraFragment handles the complete processing pipeline based
on the Andriod [camera2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary)
and uses an implementation of the ImageReader.OnImageAvailableListener
interface to process the images with Deeply.

#### Layout

The MainActivity content will be replaced by the CameraFragments layout.
It consists of:
 * an ImageView to display the camera view
 * a ContentView to display the results for the found faces

#### Important parts

CameraFragment:
 * onCreate(): Searches for the available cameras and uses back facing.
 * onViewCreated(): Gets the needed view components an sets them up
 * CreateCameraPreviewSession(): Setups the processing pipline up
 * UseProcessor():
   * Gets the processor instance from the MainActivity
   (must implement the ProcessorProvider interface)
   * Creates a new image listener and configures the usage.

OnImagAvailableListener: Listener implementation to feed the processor
with new images.

### Drawing utils

The package de.deeply.demo.ui contains helper classes to display the
Deeply results in the preview:
 * AutoFitTextureView: Updates the texture keep aspect ratio in the view
 * ObjectGraphic: Implementation of Graphic interface to draw Deeply
 object previews
 * ContentView: View that uses the ObjectGraphics to draw the complete result

# Auto Receiver

## Overview

This library enables developers to easily implement broadcast receivers programmatically using annotations (as opposed to static receivers defined in the Android manifest). Use of this library simplifies implementation, because the developer need only focus on the implementation of the receiver callback. Registration and unregistration of the receiver is handled automatically.

## Usage

Add the `@OnReceiveBroadcast` annotation to a method in a class where you'd like the broadcast receiver to reside. The requirements for the _method_ are:

* Not private
* Not static
* Void return type
* Parameters can only include `Intent` and/or `BroadcastReceiver` or none at all

The _class_ must have access to a `Context` instance (used for registration / unregistration). This can be fulfilled in a number of ways:

* The class itself extends from `Context`, e.g. `Activity`
* There is an accessible field whose type is or extends from `Context`
* There is an accessible, 0-parameter method that returns a `Context` or a subclass of it and doesn't throw any checked exceptions

The annotation itself requires the broadcast intent action (or actions) for which the annotated method should be called when the intent fires. This could be a custom action that your app broadcasts.

Some examples:

    @OnReceiveBroadcast(Intent.ACTION_BATTERY_LOW)
    protected void onLowBattery(Intent intent) {
        Toast.makeText(this, "Low Battery!", LENGTH_SHORT).show();
    }
    
    @OnReceiveBroadcast({ACTION_POWER_CONNECTED, ACTION_POWER_DISCONNECTED})
    public void onPowerStateChanged(Intent intent, BroadcastReceiver receiver) {
        updatePowerState(intent);
    }
    
    @OnReceiveBroadcast(value = ACTION_TIME_TICK, registerIn = "onResume", unregisterIn = "onPause")
    public void onTimeTick() {
        updateTimeView();
    }
    
    @OnReceiveBroadcast(value = AppConstants.BROADCAST_ACTION_AUTO_RECEIVER,
            registerIn = "init",
            unregisterIn = "deinit")
    protected void onCustomBroadcast(Intent intent) {
        Toast.makeText(this, R.string.toast_custom_broadcast, LENGTH_SHORT).show();
    }

A broadcast receiver will automatically be generated for each annotated method. The class is assumed to be an `Activity` or a `Fragment`, so the broadcast receivers will be registered in `onStart` and unregistered in `onStop` by default.

To specify different methods in which to register and unregister a particular broadcast receiver, specify the method names via the `registerIn` and `unregisterIn` attributes on the annotation. Each annotation may have different `registerIn` and `unregisterIn` values. Note that this library does not invoke these registration and unregistration methods. If this is not handled by the framework, you will be responsible for calling them at the appropriate times. Also note that exceptions may still be generated by the framework when registering or unregistering broadcast receivers (e.g. when attempting to unregister a receiver that wasn't previously registered). In these cases, make sure the registration and unregistration methods are being invoked correctly.

## Download

#### Gradle

Add the following entries to your buildscript. This may be in the top level build.gradle file.

    buildscript {
        dependencies {
            compile 'com.fjordnet.autoreceiver:gradle-plugin:1.0.0'
        }
    }

In your app's build.gradle file, apply the plugin.

    apply plugin: 'com.fjordnet.autoreceiver'

Your app will also need to be compiled with Java 8.

    android {
        compileOptions {
            sourceCompatibility JavaVersion.VERSION_1_8
            targetCompatibility JavaVersion.VERSION_1_8
        }
    }

## Library developers

### Local installation

Installing a build of the library locally allows you to test the library in the context of an app you're building in parallel.
This does not replace the need for a sample app module in the library code repo. It just allows for deeper testing and validation in a real development context.

To install a build of the library, issue the following commands in terminal from the root project's directory:

    ./gradlew :annotations:install
    ./gradlew :processor:install
    ./gradlew :plugin:install

This will produce the appropriate binaries and copy them into your local maven repository (~/.m2/repository).

In your project, include your compile dependency as you normally would. For example:

    dependencies {
        classpath 'com.fjordnet.autoreceiver:gradle-plugin:1.1.0-SNAPSHOT'
    }

You will need to add the local maven repository for Gradle to be able to find it.

    repositories {
        mavenLocal()
    }

### Implementation notes

This library uses annotation processing and aspect-oriented programming. The aspects used in the project are dependent on the annotated methods and their annotation attributes, so they are generated at compile time, during the annotation processing phase.

The auto-generated broadcast receivers are incorporated with package scope into a separate class, which is then inserted into the type hierarchy as a parent to the class containing the annotated methods. Advice are generated for initializing, registering, and unregistering the broadcast receivers in the appropriate methods. When a broadcast is received, the corresponding annotated method is invoked.

## License

    Copyright 2017 Fjord

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.



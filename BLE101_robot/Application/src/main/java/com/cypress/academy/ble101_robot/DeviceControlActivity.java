/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * This file was modified by Cypress Semiconductor Corporation in order to
 * customize it for specific hardware used for training videos.
 */

package com.cypress.academy.ble101_robot;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * This Activity provides the user interface to control the robot.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {

    // Objects to access the layout items for Tach, Buttons, and Seek bars
    private static TextView mTachLeftText;
    private static TextView mTachRightText;
    private static SeekBar mSpeedLeftSeekBar;
    private static SeekBar mSpeedRightSeekBar;
    private static Switch mEnableLeftSwitch;
    private static Switch mEnableRightSwitch;

    // This tag is used for debug messages
    private static final String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private static String mDeviceName;
    private static String mDeviceAddress;
    private static BleCar mBleCar;
    private static boolean mConnected = false;

    /**
     * This manages the lifecycle of the BLE service.
     * When the service starts we get the service object, initialize the service, and connect.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            mBleCar = ((BleCar.LocalBinder) service).getService();
            if (!mBleCar.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the car database upon successful start-up initialization.
            mBleCar.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBleCar = null;
        }
    };

    /**
     * Handle broadcasts from the Car service object. The events are:
     * ACTION_CONNECTED: connected to the car.
     * ACTION_DISCONNECTED: disconnected from the car.
     * ACTION_DATA_AVAILABLE: received data from the car.  This can be a result of a read
     * or notify operation.
     */
    private final BroadcastReceiver mCarUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case BleCar.ACTION_CONNECTED:
                    mConnected = true;
                    invalidateOptionsMenu();
                    break;
                case BleCar.ACTION_DISCONNECTED:
                    mConnected = false;
                    invalidateOptionsMenu();
                    break;
                case BleCar.ACTION_DATA_AVAILABLE:
                    // This is called after a Notify completes
                    mTachLeftText.setText(String.format("%d", BleCar.getTach(BleCar.Motor.LEFT)));
                    mTachRightText.setText(String.format("%d", BleCar.getTach(BleCar.Motor.RIGHT)));
                    break;
            }
        }
    };

    /**
     * This sets up the filter for broadcasts that we want to be notified of.
     * This needs to match the broadcast receiver cases.
     *
     * @return intentFilter
     */
    private static IntentFilter makeCarUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleCar.ACTION_CONNECTED);
        intentFilter.addAction(BleCar.ACTION_DISCONNECTED);
        intentFilter.addAction(BleCar.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.robot_control);

        // Assign the various layout objects to the appropriate variables
        mTachLeftText = (TextView) findViewById(R.id.tach_left);
        mTachRightText = (TextView) findViewById(R.id.tach_right);
        mEnableLeftSwitch = (Switch) findViewById(R.id.enable_left);
        mEnableRightSwitch = (Switch) findViewById(R.id.enable_right);
        mSpeedLeftSeekBar = (SeekBar) findViewById(R.id.speed_left);
        mSpeedRightSeekBar = (SeekBar) findViewById(R.id.speed_right);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

            getActionBar().setTitle(mDeviceName);
            getActionBar().setDisplayHomeAsUpEnabled(true);

        // Bind to the BLE service
        Log.i(TAG, "Binding Service");
        Intent carServiceIntent = new Intent(this, BleCar.class);
        bindService(carServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        /* This will be called when the left motor enable switch is changed */
        mEnableLeftSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                enableMotorSwitch(isChecked, BleCar.Motor.LEFT);
            }
        });

        /* This will be called when the right motor enable switch is changed */
        mEnableRightSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                enableMotorSwitch(isChecked, BleCar.Motor.RIGHT);
            }
        });

        /* This will be called when the left speed seekbar is moved */
        mSpeedLeftSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int speed, boolean fromUser) {
                /* Scale the speed from what the seek bar provides to what the PSoC FW expects */
                speed = scaleSpeed(speed);
                mBleCar.setMotorSpeed(BleCar.Motor.LEFT, speed);
                Log.d(TAG, "Left Speed Change to:" + speed);
            }
        });

        /* This will be called when the right speed seekbar is moved */
        mSpeedRightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int speed, boolean fromUser) {
                /* Scale the speed from what the seek bar provides to what the PSoC FW expects */
                speed = scaleSpeed(speed);
                mBleCar.setMotorSpeed(BleCar.Motor.RIGHT, speed);
                Log.d(TAG, "Right Speed Change to:" + speed);
             }
        });
    } /* End of onCreate method */

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mCarUpdateReceiver, makeCarUpdateIntentFilter());
        if (mBleCar != null) {
            final boolean result = mBleCar.connect(mDeviceAddress);
            Log.i(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mCarUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBleCar = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBleCar.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBleCar.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Scale the speed read from the slider (0 to 20) to
     * what the car object expects (-100 to +100).
     *
     * @param speed Input speed from the slider
     * @return scaled value of the speed
     */
    private int scaleSpeed(int speed) {
        final int SCALE = 10;
        final int OFFSET = 100;

        return ((speed * SCALE) - OFFSET);
    }

    /**
     * Enable or disable the left/right motor
     *
     * @param isChecked used to enable/disable motor
     * @param motor is the motor to enable/disable (left or right)
     */
    private void enableMotorSwitch(boolean isChecked, BleCar.Motor motor) {
        if (isChecked) { // Turn on the specified motor
            mBleCar.setMotorState(motor, true);
            Log.d(TAG, (motor == BleCar.Motor.LEFT ? "Left" : "Right") + " Motor On");
        } else { // turn off the specified motor
            mBleCar.setMotorState(motor, false);
            mBleCar.setMotorSpeed(motor, 0); // Force motor off
            if(motor == BleCar.Motor.LEFT) {
                mSpeedLeftSeekBar.setProgress(10); // Move slider to middle position
            } else {
                mSpeedRightSeekBar.setProgress(10); // Move slider to middle position
            }
            Log.d(TAG, (motor == BleCar.Motor.LEFT ? "Left" : "Right") + " Motor Off");
        }
    }
 }
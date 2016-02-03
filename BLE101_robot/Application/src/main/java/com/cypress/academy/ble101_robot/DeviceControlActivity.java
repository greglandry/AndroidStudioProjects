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
 */

package com.cypress.academy.ble101_robot;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
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
import java.util.List;
import java.util.UUID;

/**
 * This Activity provides the user interface to control the robot.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {

    // State (on/off) and speed of the motors
    private boolean motorLeftState = false;
    private boolean motorRightState = false;
    private int motorLeftSpeed = 0;
    private int motorRightSpeed = 0;

    // Objects to access the layout items for Tach, Buttons, and Seek bars
    private TextView mTachLeftText;
    private TextView mTachRightText;
    private SeekBar mSpeedLeftSeekBar;
    private SeekBar mSpeedRightSeekBar;
    private Switch mEnableLeftSwitch;
    private Switch mEnableRightSwitch;

    // This tag is used for debug messages
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;

    /**
     * This manages the lifecycle of the BLE service.
     * When the service starts we get the service object, initialize the service, and connect.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the GATT database upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    /**
     * Handle broadcasts from the BLE service. The events are:
     * ACTION_GATT_CONNECTED: connected to a GATT server.
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
     * ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of write, read
     * or notification operations.
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case BluetoothLeService.ACTION_GATT_CONNECTED:
                    mConnected = true;
                    invalidateOptionsMenu();
                    break;
                case BluetoothLeService.ACTION_GATT_DISCONNECTED:
                    mConnected = false;
                    invalidateOptionsMenu();
                    break;
                case BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED:
                    // Find the motor service and get the characteristics for it
                    mBluetoothLeService.getMotorCharacteristics(mBluetoothLeService.getSupportedGattServices());
                    // Set the CCCD to notify us for the two tach readings
                    mBluetoothLeService.setCharacteristicNotification(mBluetoothLeService.mTachLeftCharacteristic, true);
                    mBluetoothLeService.setCharacteristicNotification(mBluetoothLeService.mTachRightCharacteristic, true);
                    break;
                case BluetoothLeService.ACTION_DATA_AVAILABLE:
                    // This is called after a Read or Notify completes
                    final String[] dataString = intent.getStringArrayExtra(BluetoothLeService.EXTRA_DATA);
                    switch (dataString[0]) {
                        case BluetoothLeService.tachLeftCharUUID:
                            // Set the left tach value on the screen
                            mTachLeftText.setText(dataString[1]);
                            break;
                        case BluetoothLeService.tachRightCharUUID:
                            // Set the right tach value on the screen
                            mTachRightText.setText(dataString[1]);
                            break;
                    }
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
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
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
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        /* This will be called when the left motor enable switch is changed */
        mEnableLeftSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int speed;
                if (buttonView.isChecked()) {
                    motorLeftState = true;
                    speed = motorLeftSpeed;
                    Log.d(TAG, "Left Motor On");
                } else {
                    motorLeftState = false;
                    speed = 0;
                    mSpeedLeftSeekBar.setProgress(10); /* Move slider to middle position */
                    Log.d(TAG, "Left Motor Off");
                }
                /* Set the characteristic value locally and then write to the device */
                if (mBluetoothLeService.mSpeedLeftCharacteristic != null) {
                    mBluetoothLeService.mSpeedLeftCharacteristic.setValue(speed, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                    mBluetoothLeService.writeCharacteristic(mBluetoothLeService.mSpeedLeftCharacteristic);
                }
            }
        });

        /* This will be called when the right motor enable switch is changed */
        mEnableRightSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int speed;
                if (buttonView.isChecked()) {
                    motorRightState = true;
                    speed = motorRightSpeed;
                    Log.d(TAG, "Right Motor On");
                } else {
                    motorRightState = false;
                    speed = 0; /* If thw switch is off, force speed to 0 */
                    mSpeedRightSeekBar.setProgress(10); /* Move slider to middle position */
                    Log.d(TAG, "Right Motor Off");
                }
                /* Set the characteristic value locally and then write to the device */
                if (mBluetoothLeService.mSpeedRightCharacteristic != null) {
                    mBluetoothLeService.mSpeedRightCharacteristic.setValue(speed, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                    mBluetoothLeService.writeCharacteristic(mBluetoothLeService.mSpeedRightCharacteristic);
                }
            }
        });

        /* This will be called when the left speed seekbar is moved */
        mSpeedLeftSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int speed, boolean fromUser) {
                /* The seek bar is 0 to 20 but we need -100 to 100 for the PSoC FW */
                speed = (speed * 10) - 100;
                Log.d(TAG, "Left Speed Change to:" + speed);
                motorLeftSpeed = speed;
                /* If the switch is off, force speed to 0 */
                if (!motorLeftState) {
                    speed = 0;
                }
                /* Set the characteristic value locally and then write to the device */
                if (mBluetoothLeService.mSpeedLeftCharacteristic != null) {
                    mBluetoothLeService.mSpeedLeftCharacteristic.setValue(speed, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                    mBluetoothLeService.writeCharacteristic(mBluetoothLeService.mSpeedLeftCharacteristic);
                }
            }
        });

        /* This will be called when the left speed seekbar is moved */
        mSpeedRightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int speed, boolean fromUser) {
                /* The seek bar is 0 to 20 but we need -100 to 100 for the PSoC FW */
                speed = (speed * 10) - 100;
                motorRightSpeed = speed;
                Log.d(TAG, "Right Speed Change to:" + speed);
                /* If thw switch is off, force speed to 0 */
                if (!motorRightState) {
                    speed = 0;
                }
                /* Set the characteristic value locally and then write to the device */
                if (mBluetoothLeService.mSpeedRightCharacteristic != null) {
                    mBluetoothLeService.mSpeedRightCharacteristic.setValue(speed, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                    mBluetoothLeService.writeCharacteristic(mBluetoothLeService.mSpeedRightCharacteristic);
                }
            }
        });
    } /* End of onCreate method */

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.i(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
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
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
 }
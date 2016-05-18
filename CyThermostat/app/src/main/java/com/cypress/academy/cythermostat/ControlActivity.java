/*
Copyright (c) 2016, Cypress Semiconductor Corporation
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


For more information on Cypress BLE products visit:
http://www.cypress.com/products/bluetooth-low-energy-ble
 */

package com.cypress.academy.cythermostat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * This Activity provides the user interface to interact with the thermostat.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class ControlActivity extends AppCompatActivity {

    // Objects to access the layout items for temperatures and buttons
    private static TextView mMeasTempText;
    private static TextView mSetTempText;
    private static Button mSetTempUp;
    private static Button mSetTempDown;

    // This tag is used for debug messages
    private static final String TAG = ControlActivity.class.getSimpleName();

    private static String mDeviceAddress;
    private static PSoCBleThermostatService mPSoCBleThermostatService;

    /**
     * This manages the lifecycle of the BLE service.
     * When the service starts we get the service object, initialize the service, and connect.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            mPSoCBleThermostatService = ((PSoCBleThermostatService.LocalBinder) service).getService();
            if (!mPSoCBleThermostatService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the thermostat database upon successful start-up initialization.
            mPSoCBleThermostatService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mPSoCBleThermostatService = null;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        // Assign the various layout objects to the appropriate variables
        mMeasTempText = (TextView) findViewById(R.id.meas_temp);
        mSetTempText = (TextView) findViewById(R.id.set_temp);
        mSetTempUp = (Button) findViewById(R.id.set_temp_up);
        mSetTempDown = (Button) findViewById(R.id.set_temp_down);

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(ScanActivity.EXTRAS_BLE_ADDRESS);

        // Bind to the BLE service
        Log.i(TAG, "Binding Service");
        Intent ThermostatServiceIntent = new Intent(this, PSoCBleThermostatService.class);
        bindService(ThermostatServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

         /* This will be called when the up button is pressed or released */
        mSetTempUp.setOnTouchListener(new Button.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mPSoCBleThermostatService.changeSetTemp(PSoCBleThermostatService.Dir.UP);
                } else {
                    mPSoCBleThermostatService.changeSetTemp(PSoCBleThermostatService.Dir.STOP);
                }
                return true;
            }
        });

        /* This will be called when the down button is pressed or released */
        mSetTempDown.setOnTouchListener(new Button.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    mPSoCBleThermostatService.changeSetTemp(PSoCBleThermostatService.Dir.DOWN);
                } else {
                    mPSoCBleThermostatService.changeSetTemp(PSoCBleThermostatService.Dir.STOP);
                }
                return true;
            }
        });

        // Display initial temperature values to the screen. This is needed for rotation
        mMeasTempText.setText(String.format("%d", PSoCBleThermostatService.getMeasTemp()));
        mSetTempText.setText(String.format("%d", PSoCBleThermostatService.getSetTemp()));

    } /* End of onCreate method */

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mThermostatUpdateReceiver, makeThermostatUpdateIntentFilter());
        if (mPSoCBleThermostatService != null) {
            final boolean result = mPSoCBleThermostatService.connect(mDeviceAddress);
            Log.i(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mThermostatUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mPSoCBleThermostatService = null;
    }

    /**
     * Handle broadcasts from the Thermostat service object. The events are:
     * ACTION_CONNECTED: connected to the device.
     * ACTION_DISCONNECTED: disconnected from the device.
     * ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of a read
     * or notify operation.
     */
    private final BroadcastReceiver mThermostatUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case PSoCBleThermostatService.ACTION_CONNECTED:
                    // Nothing needed here.
                    break;
                case PSoCBleThermostatService.ACTION_DISCONNECTED:
                    mPSoCBleThermostatService.close();
                    break;
                case PSoCBleThermostatService.ACTION_DATA_AVAILABLE:
                    // This is called after a Notify completes
                    mMeasTempText.setText(String.format("%d", PSoCBleThermostatService.getMeasTemp()));
                    mSetTempText.setText(String.format("%d", PSoCBleThermostatService.getSetTemp()));
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
    private static IntentFilter makeThermostatUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(PSoCBleThermostatService.ACTION_CONNECTED);
        intentFilter.addAction(PSoCBleThermostatService.ACTION_DISCONNECTED);
        intentFilter.addAction(PSoCBleThermostatService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
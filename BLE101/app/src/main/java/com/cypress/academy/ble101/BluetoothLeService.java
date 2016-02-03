package com.cypress.academy.ble101;

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

import android.bluetooth.BluetoothGattCharacteristic;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    /* UUIDs for the service and characteristics that I'm interested in */
    private final static String capsenseLedServiceUUID =     "00000000-0000-1000-8000-00805f9b34f0";
    public  final static String ledCharacteristicUUID =      "00000000-0000-1000-8000-00805f9b34f1";
    public  final static String capsenseCharacteristicUUID = "00000000-0000-1000-8000-00805f9b34f2";
    private final static String CccdUUID =                   "00002902-0000-1000-8000-00805f9b34fb";

    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mLeDevice;
    private BluetoothGattService mService;
    private BluetoothGattCharacteristic mLedCharacterisitc;
    private BluetoothGattCharacteristic mCapsenseCharacteristic;
    private BluetoothGattDescriptor mCapSenseCccd;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;

    // Actions used during broadcasts
    public final static String ACTION_BLESCAN_CALLBACK =
            "com.cypress.academy.ble101.ACTION_BLESCAN_CALLBACK";
    public final static String ACTION_GATT_CONNECTED =
            "com.cypress.academy.ble101.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.cypress.academy.ble101.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.cypress.academy.ble101.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.cypress.academy.ble101.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.cypress.academy.ble101.EXTRA_DATA";

    /**
     * Implements the callback for when scanning for devices has found a device with
     * the service we are looking for.
     */
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    mLeDevice = device;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback); /* Stop scanning after the first device is found */
                    broadcastUpdate(ACTION_BLESCAN_CALLBACK); /* Tell the main activity that the device has been found */
                }
            };

    /**
     * Implements callback methods for GATT events that the app cares about.  For example,
     * connection change and services discovered.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(ACTION_GATT_CONNECTED);
                Log.i(TAG, "Connected to GATT server.");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        /**
         * This is called when a service discovery has completed.
         *
         * It gets the characteristics we are interested in and then
         * broadcasts an update to the main activity.
         *
         * @param gatt The GATT database object
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
             /* Get just the service that we are looking for */
            mService = gatt.getService(UUID.fromString(capsenseLedServiceUUID));
            /* Get characteristics from our desired service */
            mLedCharacterisitc = mService.getCharacteristic(UUID.fromString(ledCharacteristicUUID));
            mCapsenseCharacteristic = mService.getCharacteristic(UUID.fromString(capsenseCharacteristicUUID));
            /* Get the CapSense CCCD */
            mCapSenseCccd = mCapsenseCharacteristic.getDescriptor(UUID.fromString(CccdUUID));

            /* Broadcast that service/characteristic/descriptor discovery is done */
            broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        /**
         * This is called when a characteristic with notify set changes.
         * It broadcasts an update to the main activity with the changed data.
         *
         * @param gatt The GATT database object
         * @param characteristic The characteristic that was changed
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    }; /* End of GATT event callback */

    /**
     * Sends a broadcast to the listener in the main activity.
     *
     * @param action The type of action that occurred.
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /**
     * Sends a broadcast with EXTRA_DATA to the listener in the main activity.
     *
     * The data contains the UUID for the characteristic
     * that was changed and the value itself.
     *
     * @param action         The type of action that occurred.
     * @param characteristic the characteristic that was changed.
     */
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        // Write 2 strings - first is the characteristic UUID and second is the data itself
        final String[] dataString = new String[2];
        dataString[0] = characteristic.getUuid().toString();
        final byte[] data = characteristic.getValue();
        // Convert the byte array to a decimal string
        if (data.length == 2) {
            dataString[1] = Integer.toString(((data[1] & 0xff) << 8) | (data[0] & 0xff));
        } else {
            dataString[1] = Integer.toString(data[0]);
        }
        intent.putExtra(EXTRA_DATA, dataString);
        sendBroadcast(intent);
    }

    /**
     * This is a binder for the BluetoothLeService
     */
    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }


    /**
     * Scans for BLE devices that support the service we are looking for
     */
    public void scan() {
        /* Scan for devices and look for the one with the service that we want */
        UUID[] capsenseLedServiceArray = {UUID.fromString(capsenseLedServiceUUID)};
        mBluetoothAdapter.startLeScan(capsenseLedServiceArray, mLeScanCallback);
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect() {
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                return true;
            } else {
                return false;
            }
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = mLeDevice.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        return true;
    }

    /**
     * Runs service discovery on the connected device.
     */
    public void discoverServices() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.discoverServices();
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * This method is used to read the state of the LED from the device
     */
     public void readLedCharacteristic() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(mLedCharacterisitc);
    }

    /**
     * This method is used to turn the LED on or off
     *
     * @param value Turns the LED on (1) or off (0)
     */
    public void writeLedCharacteristic(byte[] value) {
        mLedCharacterisitc.setValue(value);
        mBluetoothGatt.writeCharacteristic(mLedCharacterisitc);
    }

    /**
     * This method enables or disables notifications for the CapSense slider
     *
     * @param value Turns notifications on (1) or off (0)
     */
    public void writeCapSenseNotification(boolean value) {
        mBluetoothGatt.setCharacteristicNotification(mCapsenseCharacteristic, value);
        byte[] byteVal = new byte[1];
        if (value) {
            byteVal[0] = 1;
        } else {
            byteVal[0] = 0;
        }
        mCapSenseCccd.setValue(byteVal);
        mBluetoothGatt.writeDescriptor(mCapSenseCccd);
    }
}
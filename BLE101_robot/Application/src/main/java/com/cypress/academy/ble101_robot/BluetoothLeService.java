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

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {

    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    //  Queues for BLE events
    //  The queue priority is such that descriptor writes occur first, then characteristic
    //  writes, and finally characteristic reads.
    private Queue<BluetoothGattDescriptor> BleDescriptorWriteQueue = new LinkedList<>();
    private Queue<BluetoothGattCharacteristic> BleCharacteristicWriteQueue = new LinkedList<>();
    private Queue<BluetoothGattCharacteristic> BleCharacteristicReadQueue = new LinkedList<>();

    /* UUID for the custom motor characteristics */
    public final static String motorServiceUUID = "00000000-0000-1000-8000-00805f9b34f0";
    public final static String speedLeftCharUUID = "00000000-0000-1000-8000-00805f9b34f1";
    public final static String speedRightCharUUID = "00000000-0000-1000-8000-00805f9b34f2";
    public final static String tachLeftCharUUID = "00000000-0000-1000-8000-00805f9b34f3";
    public final static String tachRightCharUUID = "00000000-0000-1000-8000-00805f9b34f4";
    public final static String CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    // Bluetooth Characteristics that we need to read/write
    public BluetoothGattCharacteristic mSpeedLeftCharacteristic;
    public BluetoothGattCharacteristic mSpeedRightCharacteristic;
    public BluetoothGattCharacteristic mTachLeftCharacteristic;
    public BluetoothGattCharacteristic mTachRightCharacteristic;

    /* Actions used during broadcasts */
    public final static String ACTION_GATT_CONNECTED =
            "com.cypress.academy.ble101_robot.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.cypress.academy.ble101_robot.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.cypress.academy.ble101_robot.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.cypress.academy.ble101_robot.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.cypress.academy.ble101_robot.EXTRA_DATA";

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
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        /**
         * This is called when a service discovery has completed.
         *
         * It broadcasts an update to the main activity.
         *
         * @param gatt The GATT database object
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        /**
         * This is called when a characteristic read has completed.
         * The data that was read from the characteristic is broadcasted so that the main
         * activity can operate on the data.
         * Is uses a queue to determine if additional BLE actions are still pending and launches
         * the next one if there are.
         *
         * @param gatt The GATT database object
         * @param characteristic The characteristic that was read.
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
            // Pop the item that was written from the queue
            BleCharacteristicReadQueue.remove();
            // See if there are more items in the BLE queue
            if (BleCharacteristicReadQueue.size() > 0) {
                mBluetoothGatt.readCharacteristic(BleCharacteristicReadQueue.element());
            }
        }

        /**
         * This is called when a characteristic write has completed. Is uses a queue to determine if
         * additional BLE actions are still pending and launches the next one if there are.
         *
         * @param gatt The GATT database object
         * @param characteristic The characteristic that was written.
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            // Pop the item that was written from the queue
            BleCharacteristicWriteQueue.remove();
            // See if there are more items in the BLE queues
            if (BleCharacteristicWriteQueue.size() > 0) {
                mBluetoothGatt.writeCharacteristic(BleCharacteristicWriteQueue.element());
            } else if (BleCharacteristicReadQueue.size() > 0) {
                mBluetoothGatt.readCharacteristic(BleCharacteristicReadQueue.element());
            }
        }


        /**
         * This is called when a CCCD write has completed. Is uses a queue to determine if
         * additional BLE actions are still pending and launches the next one if there are.
         *
         * @param gatt The GATT database object
         * @param descriptor The CCCD that was written.
         * @param status Status of whether the write was successful.
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            // Pop the item that was written from the queue
            BleDescriptorWriteQueue.remove();
            // See if there are more items in the BLE queues
            if (BleDescriptorWriteQueue.size() > 0) {
                mBluetoothGatt.writeDescriptor(BleDescriptorWriteQueue.element());
            } else if (BleCharacteristicWriteQueue.size() > 0) {
                mBluetoothGatt.writeCharacteristic(BleCharacteristicWriteQueue.element());
            } else if (BleCharacteristicReadQueue.size() > 0) {
                mBluetoothGatt.readCharacteristic(BleCharacteristicReadQueue.element());
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
    };

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
        // Write 2 strings - first is the characteristic UUID and second is the data
        final String[] dataString = new String[2];
        dataString[0] = characteristic.getUuid().toString();
        final byte[] data = characteristic.getValue();
        // Convert the byte array to a decimal string
        if (data.length > 1) {
            dataString[1] = Integer.toString(java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt());
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
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.i(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.i(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
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
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BleCharacteristicReadQueue.add(characteristic);
        if ((BleCharacteristicReadQueue.size() == 1) && (BleCharacteristicWriteQueue.size() == 0) && (BleDescriptorWriteQueue.size() == 0)) {
            mBluetoothGatt.readCharacteristic(characteristic);
            Log.i(TAG, "Reading Characteristic");
        }
    }

    /**
     * Request a write on a given {@code BluetoothGattCharacteristic}.
     *
     * @param characteristic The characteristic to write.
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BleCharacteristicWriteQueue.add(characteristic);
        if ((BleCharacteristicWriteQueue.size() == 1) && (BleDescriptorWriteQueue.size() == 0)) {
            mBluetoothGatt.writeCharacteristic(characteristic);
            Log.i(TAG, "Writing Characteristic");
        }
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized");
            return;
        }

        /* Enable or disable the callback notification on the phone */
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        /* Set CCCD value locally and then write to the device to register for notifications */
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(CCCD_UUID));
        if (enabled) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        // Put the descriptor into the write queue
        BleDescriptorWriteQueue.add(descriptor);
        // If there is only 1 item in the queue, then write it. If more than one, then the callback
        // will handle it
        if (BleDescriptorWriteQueue.size() == 1) {
            mBluetoothGatt.writeDescriptor(descriptor);
            Log.i(TAG, "Writing Notification");
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    /**
     * Get characteristics from the Motor Service
     *
     * @param gattServices The service to interrogate for characteristics
     */
    public void getMotorCharacteristics(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;

        String uuid;

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();

            /* Get the characteristics for the motorService */
            if (uuid.equals(BluetoothLeService.motorServiceUUID)) {
                mSpeedLeftCharacteristic = gattService.getCharacteristic(UUID.fromString(BluetoothLeService.speedLeftCharUUID));
                mSpeedRightCharacteristic = gattService.getCharacteristic(UUID.fromString(BluetoothLeService.speedRightCharUUID));
                mTachLeftCharacteristic = gattService.getCharacteristic(UUID.fromString(BluetoothLeService.tachLeftCharUUID));
                mTachRightCharacteristic = gattService.getCharacteristic(UUID.fromString(BluetoothLeService.tachRightCharUUID));
            }
        }
    }
}

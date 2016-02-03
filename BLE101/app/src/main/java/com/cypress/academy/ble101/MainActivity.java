package com.cypress.academy.ble101;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // TAG is used for informational messages
    private final static String TAG = MainActivity.class.getSimpleName();

    // Variables to access objects from the layout such as buttons, switches, values
    private TextView mCapsenseValue;
    private Button start_button;
    private Button search_button;
    private Button connect_button;
    private Button discover_button;
    private Button disconnect_button;
    private Switch led_switch;
    private Switch cap_switch;

    // Variables to manage BLE connection
    private boolean mConnectState;
    private boolean mServiceConnected;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;

    private static final int REQUEST_ENABLE_BT = 1;

    //This is required for Android 6.0 (Marshmallow)
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    /************************************************************************/
    /*  Code to manage the BLE Service */
    /************************************************************************/
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            mServiceConnected = true;
            mBluetoothLeService.initialize();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected");
            mBluetoothLeService = null;
        }
    };

    @TargetApi(23) // This is required for Android 6.0 (Marshmallow) to work
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Set up a variable to point to the CapSense value on the display */
        mCapsenseValue = (TextView) findViewById(R.id.capsense_value);

        /* Set up variables for accessing buttons and slide switches */
        start_button = (Button) findViewById(R.id.start_button);
        search_button = (Button) findViewById(R.id.search_button);
        connect_button = (Button) findViewById(R.id.connect_button);
        discover_button = (Button) findViewById(R.id.discoverSvc_button);
        disconnect_button = (Button) findViewById(R.id.disconnect_button);
        led_switch = (Switch) findViewById(R.id.led_switch);
        cap_switch = (Switch) findViewById(R.id.capsense_switch);

        /* Initialize service and connection state variable */
        mServiceConnected = false;
        mConnectState = false;

        //This section required for Android 6.0 (Marshmallow)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access ");
                builder.setMessage("Please grant location access so this app can detect devices.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        } //End of section for Android 6.0 (Marshmallow)

         /* This will be called when the LED On/Off switch is touched */
        led_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                byte[] value = new byte[1];
                if (buttonView.isChecked()) {
                    value[0] = (byte) (1);
                    Log.i(TAG, "LED On");
                } else {
                    value[0] = (byte) (0);
                    Log.i(TAG, "LED Off");
                }
                mBluetoothLeService.writeLedCharacteristic(value);
            }
        });

         /* This will be called when the CapSense On/Off switch is touched */
        cap_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                boolean value;
                value = buttonView.isChecked();
                /* Set notifications in the CCCD locally and write to the server to tell it to notify us */
                Log.i(TAG, "CapSense Notification " + value);
                mBluetoothLeService.writeCapSenseNotification(value);
            }
        });
    }

    //This method required for Android 6.0 (Marshmallow)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permission for 6.0:", "Coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
            }
        }
    } //End of section for Android 6.0 (Marshmallow)

    @Override
    protected void onResume() {
        super.onResume();
        /* Register the broadcast receiver. This receives messages from the BLE service */
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothLeService.ACTION_BLESCAN_CALLBACK);
        filter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        filter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        filter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        filter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        registerReceiver(mBleUpdateReceiver, filter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBleUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothLeService.close();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    /************************************************************************/
    /* Button handler functions and state machine */
    /************************************************************************/
    public void startBluetooth(View view) {

        // Find BLE service and adapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Start the BLE Service
        Log.d(TAG, "Starting BLE Service");
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        /* Disable the start button and turn on the search  button */
        start_button.setEnabled(false);
        search_button.setEnabled(true);
        Log.d(TAG, "Bluetooth is Enabled");
    }

    public void searchBluetooth(View view) {
        if(mServiceConnected) {
            mBluetoothLeService.scan();
        }

        /* After this we wait for the scan callback to detect that a device has been found */
        /* The callback broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    public void connectBluetooth(View view) {
        mBluetoothLeService.connect();

        /* After this we wait for the gatt callback to report the device is connected */
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    public void discoverServices(View view) {
        /* This will discover both services and characteristics */
        mBluetoothLeService.discoverServices();

        /* After this we wait for the gatt callback to report the services and characteristics */
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

     public void Disconnect(View view) {
        mBluetoothLeService.disconnect();

        /* After this we wait for the gatt callback to report the device is disconnected */
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }


    /************************************************************************/
    /* Listener for BLE event broadcasts */
    /************************************************************************/
    private final BroadcastReceiver mBleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case BluetoothLeService.ACTION_BLESCAN_CALLBACK:
                    /* Disable the search button and enable the connect button */
                    search_button.setEnabled(false);
                    connect_button.setEnabled(true);
                    break;

                case BluetoothLeService.ACTION_GATT_CONNECTED:
                    /* This if statement is needed because we sometimes get a GATT_CONNECTED */
                    /* action when sending Capsense notifications */
                    if (!mConnectState) {
                        /* Disable the connect button, enable the discover services and disconnect buttons */
                        connect_button.setEnabled(false);
                        discover_button.setEnabled(true);
                        disconnect_button.setEnabled(true);
                        mConnectState = true;
                        Log.d(TAG, "Connected to Device");
                    }
                    break;
                case BluetoothLeService.ACTION_GATT_DISCONNECTED:
                    /* Disable the disconnect, discover svc, discover char button, and enable the search button */
                    disconnect_button.setEnabled(false);
                    discover_button.setEnabled(false);
                    search_button.setEnabled(true);
                    /* Turn off and disable the LED and CapSense switches */
                    led_switch.setChecked(false);
                    led_switch.setEnabled(false);
                    cap_switch.setChecked(false);
                    cap_switch.setEnabled(false);
                    mConnectState = false;
                    Log.d(TAG, "Disconnected");
                    break;
                case BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED:
                    /* Disable the discover services button */
                    discover_button.setEnabled(false);
                    /* Enable the LED and CapSense switches */
                    led_switch.setEnabled(true);
                    cap_switch.setEnabled(true);
                    /* Initiate a read of the current state of the LED from the peer */
                    /* The onCharacteristicRead callback will be called when it completes */
                    mBluetoothLeService.readLedCharacteristic();
                    Log.d(TAG, "Services Discovered");
                    break;
                case BluetoothLeService.ACTION_DATA_AVAILABLE:
                    String[] resultArray = intent.getStringArrayExtra(BluetoothLeService.EXTRA_DATA);
                    switch (resultArray[0]) {
                        case BluetoothLeService.ledCharacteristicUUID:
                            /* Check the state of the LED on the device and update the slider switch */
                            if (resultArray[1].equals("1")) {
                                led_switch.setChecked(true);
                            } else {
                                led_switch.setChecked(false);
                            }
                            break;
                        case BluetoothLeService.capsenseCharacteristicUUID:
                            /* Update CapSense value display on the screen */
                            if(resultArray[1].equals("65535")) { // No Touch results in 0xFFFF
                                mCapsenseValue.setText("No Touch");
                            } else {
                                mCapsenseValue.setText(resultArray[1]);
                            }
                            break;
                    }
                default:
                    break;
            }
        }
    };
}

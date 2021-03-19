package eu.h2020.helios_social.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Bundle;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import eu.h2020.helios_social.modules.smartenvironments.HeliosSmartEnvironments;

public class MainActivity extends AppCompatActivity {

    int REQUEST_ENABLE_BT = 600;
    int REQUEST_FINE_LOCATION = 700;
    int REQUEST_COARSE_LOCATION = 800;

    TextView textMsg;

    private BluetoothAdapter bluetoothAdapter;

    //Strings for intent filters for registering the receivers
    private static final String ACTION_STRING_SERVICE = "ToService";
    private static final String ACTION_STRING_ACTIVITY = "ToActivity";

    //String to send name of environment in Intent
    public static final String EXTRA_NAME = "ENVIRONMENT_NAME";

    //Create a broadcast receiver
    private BroadcastReceiver activityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String env = getIntent().getStringExtra(EXTRA_NAME);

            Log.v("BT", "Data received: " + env);

            Toast.makeText(getApplicationContext(), "New Helios Smart Environment found: " + env, Toast.LENGTH_LONG).show();
            textMsg.setText(env);
        }
    };

    //Only one instance of service running
    boolean serviceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //TextView to show received messages in UI
        textMsg = (TextView) findViewById(R.id.receivedMsg);

        //Check permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_FINE_LOCATION);
        }
        else {
            Log.v("BT", "Fine Location Permission granted");
            //Check coarse location
            checkCoarseLocation();
        }

        //Register the broadcast receiver
        if (activityReceiver != null) {
            //Create an intent filter to listen to the broadcast sent with the action "ACTION_STRING_ACTIVITY"
            IntentFilter intentFilter = new IntentFilter(ACTION_STRING_ACTIVITY);
            //Map the intent filter to the receiver
            registerReceiver(activityReceiver, intentFilter);
        }

        if (!serviceRunning) {
            //Start the service on launching the application
            startService(new Intent(this, HeliosSmartEnvironments.class));
            serviceRunning = true;
            Log.v("BT", "MAIN ACTIVITY SERVICE CALLING");
        }

    }


    private void connection () {
        //Bluetooth connection
        boolean isBTavailable = isBTavailable();

        if (isBTavailable) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                Log.v("BT", "Bluetooth Permission granted");

                //Bluetooth is enabled. Starting service. Searching for paired devices.
                Intent serviceIntent = new Intent(this, HeliosSmartEnvironments.class);
                startService(serviceIntent);
            }

        }
    }

    private boolean isBTavailable() {

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.v("BT", "No Bluetooth available.");
            return false;
        } else {
            Log.v("BT", "Bluetooth available.");
            return true;
        }

    }

    private void checkCoarseLocation () {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_COARSE_LOCATION);
        }
        else {
            //Location is enabled. Start connection.
            Log.v("BT", "Coarse Location Permission granted");
            connection();
        }
    }

    //Enable Bluetooth Callback
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //Result of request to enable BT
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {

                //Calling HeliosSmartEnvironment Service. Scanning BT devices
                Intent serviceIntent = new Intent(this, HeliosSmartEnvironments.class);
                startService(serviceIntent);

            } else {
                Log.v("BT", "Bluetooth request REFUSED by user");
            }
        }
    }

    //Enable Location Callback
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

        if (requestCode==REQUEST_FINE_LOCATION) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                //Check other permissions
                checkCoarseLocation();

            } else {

                Log.v("BT", "Permission request REFUSED by user");
            }
        }
        else if (requestCode==REQUEST_COARSE_LOCATION) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                //Start connection
                connection();

            } else {

                Log.v("BT", "Permission request REFUSED by user");
            }
            return;
        }
    }


    public void setUIText(String text) {
        textMsg.setText(text);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.v("BT", "Unregistering Broadcast receiver");
        unregisterReceiver(activityReceiver);
    }

    //Send broadcast from activity to all receivers listening to the action "ACTION_STRING_SERVICE"
    private void sendBroadcast() {
        Intent new_intent = new Intent();
        new_intent.setAction(ACTION_STRING_SERVICE);
        sendBroadcast(new_intent);
    }
}

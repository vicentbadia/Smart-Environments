package eu.h2020.helios_social.modules.smartenvironments;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class HeliosSmartEnvironments extends Service {

    //To read received message by handler
    final byte delimiter = 33;
    int readBufferPosition = 0;

    Handler handler;

    private boolean smartEnvironmentFound = false;

    BluetoothSocket mmSocket = null;

    boolean connectedOk;

    //Local instance of Bluetooth adapter
    BluetoothAdapter BTadapter;

    public static boolean serviceRunning = false;

    //Strings for intent filter for registering the receivers
    private static final String ACTION_STRING_SERVICE = "ToService";
    private static final String ACTION_STRING_ACTIVITY = "ToActivity";

    //String to send name of environment in Intent
    public static final String EXTRA_NAME = "ENVIRONMENT_NAME";

    //Broadcast receiver
    private BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(getApplicationContext(), "Received message in service!", Toast.LENGTH_LONG).show();
            Log.v("BT", "Sending broadcast to activity");
            sendBroadcast("Received message by service!");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        // Register for broadcasts when a BT device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);


        //Handler to manage messages sent by Raspi
        handler = new Handler();

        //Listener to receive messages from Raspi
        msgListener();

        //Register the receiver
        if (serviceReceiver != null) {
            //Create an intent filter to listen to the broadcast sent with the action  "ACTION_STRING_SERVICE"
            IntentFilter intentFilter = new IntentFilter(ACTION_STRING_SERVICE);
            //Map the intent filter to the receiver
            registerReceiver(serviceReceiver, intentFilter);
        }

        connectedOk = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!serviceRunning) {
            Log.v("BT", "Smart Environment Service running");
            connectDevice();
            serviceRunning = true;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    // Create a BroadcastReceiver for ACTION_FOUND > BTadapter.startDiscovery()
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                Log.v("BT", "Device found: " + deviceName);

                if (deviceName != null)
                    if (deviceName.contains("helios")) startConnect(device);
            }
        }
    };

    private void connectDevice() {

        BTadapter = BluetoothAdapter.getDefaultAdapter();

        Log.v("BT", "Connecting to sincronized devices...");
        Set<BluetoothDevice> pairedDevices = BTadapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                //Searching for Helios Smart environment devices
                if  (deviceName.contains("helios")) {
                    Log.v("BT", "Helios Smart Environment is now paired with name: " + deviceName);
                    Log.v("BT", "MAC adress: " + deviceHardwareAddress);

                    smartEnvironmentFound = true;
                    startConnect(device);
                }
            }
        }

        //No paired devices for Helios Smart Environment. Scanning other Bluetooth devices.
        if (!smartEnvironmentFound) {
            Log.v("BT", "No sincronized devices found");

            //Start to scan BT devices
            scanDevices();
        }

    }

    private void scanDevices() {
        BTadapter.startDiscovery();
    }

    private void startConnect(BluetoothDevice device) {

        //Stop scanning devices
        BTadapter.cancelDiscovery();

        Log.v("BT", "Connecting device...");

        sendData(device,"Message from ANDROID");
    }

    private void sendData(BluetoothDevice bluetoothDevice, String msg2send){
        //UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        UUID uuid = UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"); //Standard SerialPortService ID
        Log.v("BT", "Creando socket");
        try {
            mmSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
        } catch (IOException e1) {
            Log.v("BT", "Socket not created. Message: " + e1);
            Log.v("BT", "Scanning devices again");
            scanDevices();
            return;
        }

        //Connexion
        try {
            mmSocket.connect();
            Log.v("BT", "Connecting...");
        } catch (IOException e2) {
            e2.printStackTrace();
            try {
                mmSocket.close();
                Log.v("BT", "Cannot connect to device: " + e2);
            } catch (IOException e3) {
                e3.printStackTrace();
                Log.v("BT", "Socket not closed: " + e3);
            }
            scanDevices();
            return;
        }
        connectedOk = true;

        String msg = msg2send;
        try {
            OutputStream mmOutputStream = mmSocket.getOutputStream();
            mmOutputStream.write(msg.getBytes());
        } catch (IOException e4) {
            e4.printStackTrace();
            Log.v("BT","Error sending message: " + e4);
            //Close connection and scanning again
            try {
                mmSocket.close();
            } catch (IOException e5) {
                e5.printStackTrace();
                Log.v("BT", "Socket not closed: " + e5);
            }
            connectedOk = false;
            scanDevices();
            return;
        }
        Log.v("BT", "Sending message: " + msg2send);
    }

    private void msgListener() {

        final class workerThread implements Runnable {

            public workerThread() {
            }

            public void run()
            {
                while(!Thread.currentThread().isInterrupted())
                {
                    if (connectedOk) {
                        int bytesAvailable;
                        boolean workDone = false;

                        try {

                            final InputStream mmInputStream;
                            mmInputStream = mmSocket.getInputStream();
                            if (mmInputStream.available() >0 )
                            {
                                bytesAvailable = mmInputStream.available();
                                byte[] packetBytes = new byte[bytesAvailable];
                                Log.v("BT","bytes available from Raspi");
                                byte[] readBuffer = new byte[1024];
                                mmInputStream.read(packetBytes);

                                for(int i=0;i<bytesAvailable;i++)
                                {
                                    byte b = packetBytes[i];
                                    if(b == delimiter)
                                    {
                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                        final String data = new String(encodedBytes, "US-ASCII");
                                        readBufferPosition = 0;

                                        //The variable data now contains our full command
                                        handler.post(new Runnable()
                                        {
                                            public void run()
                                            {
                                                Log.v("BT", "Message received from Raspi: " + data);
                                                sendBroadcast(data);
                                            }
                                        });

                                        workDone = true;
                                        break;


                                    }
                                    else
                                    {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }

                                if (workDone == true){
                                    break;
                                }

                            } else {
                                Log.v("BT", "No message received from Raspi");
                            }

                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                }
            }
        };

        //Waiting for connection
        //Message sent. Waiting response
        (new Thread(new workerThread())).start();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver);

        Log.v("BT", "onDestroy service");
        unregisterReceiver(serviceReceiver);
    }

    //Send broadcast from activity to all receivers listening to the action "ACTION_STRING_ACTIVITY"
    private void sendBroadcast(String data) {
        Intent new_intent = new Intent();
        new_intent.setAction(ACTION_STRING_ACTIVITY);

        Log.v("BT", "Sending data: " + data);

        new_intent.putExtra(EXTRA_NAME, data);

        sendBroadcast(new_intent);

        //Log.v("BT", "Stopping the service");
        //stopSelf();
    }

}

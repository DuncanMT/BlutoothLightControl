package com.duncan.blutoothlightcontrol;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends Activity implements SensorEventListener {

    // SPP UUID service - this should work for most devices
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    final int handlerState = 0;                        //used to identify handler message
    TextView txtString, txtStringLength, xCoor, yCoor, zCoor;
    Button test;
    Handler bluetoothIn;
    private BluetoothAdapter btAdapter = null;
    private StringBuilder recDataString = new StringBuilder();
    private BluetoothSocket btSocket = null;
    private ConnectedThread mConnectedThread;
    private boolean posMode = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        xCoor = (TextView) findViewById(R.id.xcoor);
        yCoor = (TextView) findViewById(R.id.ycoor);
        zCoor = (TextView) findViewById(R.id.zcoor);
        test = (Button) findViewById(R.id.test);
        txtString = (TextView) findViewById(R.id.txtString);
        txtStringLength = (TextView) findViewById(R.id.testView1);

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        // add listener. The listener will be HelloAndroid (this) class

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
               SensorManager.SENSOR_DELAY_NORMAL);

        test.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(posMode){
                    posMode = false;
                    mConnectedThread.write("m");
                }else {
                    posMode = true;
                    mConnectedThread.write("m");
                }
            }
        });
        btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
        checkBTState();
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void onSensorChanged(SensorEvent event) {

        // check sensor type
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (posMode){
                float x = event.values[0];
                int xi = (int) Math.round((x + 10) * 2.5);
                if (xi >=50){
                    xi =49;
                }
                xCoor.setText("X: " + xi);
                mConnectedThread.write(xi+";");
            }else {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                int xi = (int) Math.round((x + 10) * 12.75);
                int yi = (int) Math.round((y + 10) * 12.75);
                int zi = (int) Math.round((z + 10) * 12.75);

                if (xi > 255) {
                    xi = 255;
                }
                if (yi > 255) {
                    yi = 255;
                }
                if (zi > 255) {
                    zi = 255;
                }

                xCoor.setText("X: " + xi);
                yCoor.setText("Y: " + yi);
                zCoor.setText("Z: " + zi);
                mConnectedThread.write(xi + "," + yi + "," + zi + ";");
            }
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connecetion with BT device using UUID
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("Main Activity", "Resume");

        //Get MAC address from DeviceListActivity via intent
        Intent intent = getIntent();

        //Get the MAC address from the DeviceListActivty via EXTRA
        String address = intent.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        //create device and set the MAC address
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
        }
        // Establish the Bluetooth socket connection.
        try {
            btSocket.connect();
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                //insert code to deal with this
            }
        }
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        //I send a character when resuming.beginning transmission to check device is connected
        //If it is not an exception will be thrown in the write method and finish() will be called
        //mConnectedThread.write("x");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("Main Activity", "Pause");
        try {
            //Don't leave Bluetooth sockets open when leaving activity
            btSocket.close();
            mConnectedThread.interrupt();
            Log.d("Main Activity", "Thread interrupted");
        } catch (IOException e2) {
            Log.d("Main Activity", "Write Fail");
        }
    }

    //Checks that the Android device Bluetooth is available and prompts to be turned on if off
    private void checkBTState() {

        if (btAdapter == null) {
            Toast.makeText(getBaseContext(), "Device does not support bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (!btAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    //create new class for connect thread
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "ConnectedThread Fail", Toast.LENGTH_LONG).show();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            // Keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);            //read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                Log.d("Main Activity", "Write Fail");
                Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}

/*********************************************************************************
 Author	            :  Duncan Tyrell
 Last Modified      :  23/4/2015
 File		        :  MainActivity.java
 Target Hardware    :  Samsung Galaxy S4 Running android 4.4.2
 Version	        :  1.0.0

 Description	    :  This file is the source of the main activity of the Android
 application and is responsible for all the apps main functionality
 *********************************************************************************/
package com.duncan.blutoothlightcontrol;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends Activity implements SensorEventListener {

    //Initialise Variables
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    TextView redVal, greenVal, blueVal, red, green, blue, circle;
    Button changeMode;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private ConnectedThread mConnectedThread;
    private boolean posMode = false;
    private boolean notInterupted;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Create variables to access the elements of the screen
        redVal = (TextView) findViewById(R.id.red_value);
        greenVal = (TextView) findViewById(R.id.green_value);
        blueVal = (TextView) findViewById(R.id.blue_value);
        red = (TextView) findViewById(R.id.red);
        green = (TextView) findViewById(R.id.green);
        blue = (TextView) findViewById(R.id.blue);
        changeMode = (Button) findViewById(R.id.change_mode);
        circle = (TextView) findViewById(R.id.circle);

        //Initialise the accelerometer
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);

        //Listen for button presses
        changeMode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!posMode) {
                    posMode = true;
                    mConnectedThread.write("m");
                    red.setText("Red Value: ");
                    green.setText("Green Value: ");
                    blue.setText("Blue Value: ");
                } else {
                    posMode = false;
                    red.setText("Green Position: ");
                    green.setText("");
                    blue.setText("");
                    greenVal.setText("");
                    blueVal.setText("");
                    SetColorCirlce(0, 255, 0);
                    mConnectedThread.write("m");
                }
            }
        });
        // get Bluetooth adapter
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();
    }

    //Function for changing the colour of the circle
    private void SetColorCirlce(int i, int j, int k) {
        Resources res = getResources();
        final Drawable drawable = res.getDrawable(R.drawable.coloured_circle);
        ((GradientDrawable) drawable).setColor(Color.rgb(i, j, k));
        drawable.mutate();

        circle.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
    }

    //Forced to implement this function by the accelerometer, does nothing
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //Function runs when the a sensor is ready
    public void onSensorChanged(SensorEvent event) {

        // check sensor type
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //If in moving LED mode else in change all mode
            if (!posMode) {
                SetColorCirlce(0, 255, 0);
                //get X axis value then convert to range 0 - 255
                float x = event.values[0];
                int xi = (int) Math.round((x + 10) * 2.5);
                //Check for rouge values
                if (xi > 49) {
                    xi = 49;
                }
                if (xi < 1) {
                    xi = 0;
                }
                //Display the current position of the Green LED
                redVal.setText(Integer.toString(xi));
                //Send current position of green LED to Teensy
                mConnectedThread.write(xi + ";");
            } else {
                //Get x, y and z values then convert to range 0 -255
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                int xi = (int) Math.round((x + 10) * 12.75);
                int yi = (int) Math.round((y + 10) * 12.75);
                int zi = (int) Math.round((z + 10) * 12.75);
                //Check for rouge values
                if (xi > 255) {
                    xi = 255;
                }
                if (yi > 255) {
                    yi = 255;
                }
                if (zi > 255) {
                    zi = 255;
                }
                if (xi < 0) {
                    xi = 0;
                }
                if (yi < 0) {
                    yi = 0;
                }
                if (zi < 0) {
                    zi = 0;
                }

                //Display current x, y and z values
                redVal.setText(Integer.toString(xi));
                greenVal.setText(Integer.toString(yi));
                blueVal.setText(Integer.toString(zi));
                //change teh colour of the circle
                SetColorCirlce(xi, yi, zi);
                //Send x, y  and z values to the teensy
                mConnectedThread.write(xi + "," + yi + "," + zi + ";");
            }
        }
    }


    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        //creates secure outgoing connecetion with BT device using UUID
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
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
                Log.d("MainActivity", "Socket Close fail");
            }
        }
        //Set flag to true so the thread can run
        notInterupted = true;
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("Main Activity", "Pause");
        try {
            //Stop thread from running this is still buggy, .interupt() should work but doesn't
            btSocket.close();
            mConnectedThread.interrupt();
            notInterupted = false;
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
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "ConnectedThread Fail", Toast.LENGTH_LONG).show();
            }
            mmOutStream = tmpOut;
        }

        //write method
        public void write(String input) {
            if (notInterupted) {
                byte[] msgBuffer = input.getBytes();
                try {

                    mmOutStream.write(msgBuffer);

                } catch (IOException e) {
                    //if you cannot write, close the application
                    Log.d("Main Activity", "Write Fail");
                    Toast.makeText(getBaseContext(), "Connection Failure", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }
}

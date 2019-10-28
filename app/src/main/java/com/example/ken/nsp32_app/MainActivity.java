package com.example.ken.nsp32_app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class MainActivity extends AppCompatActivity {

    public static final String LOG_TAG = "NSP32_APP";

    TextView T01 ;
    Button B_pair ;
    Button B_acquire ;
    EditText Edit_SS ;
    EditText Edit_Avg ;
    CheckBox CB_Flag_AR;
    private BluetoothAdapter mBluetoothAdapter = null;

    private static BluetoothSerialService mSerialService = null;

    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_CONNECT_DEVICE = 1;

    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    ProgressDialog mProgressDialog ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        T01 = (TextView)this.findViewById(R.id.T01);
        Edit_SS = (EditText)this.findViewById(R.id.edit_SS) ;
        Edit_Avg = (EditText) this.findViewById(R.id.edit_Avg) ;
        CB_Flag_AR = (CheckBox) this.findViewById(R.id.Flag_AE) ;

        B_pair = (Button) this.findViewById(R.id.B_pair);
        B_acquire = (Button) this.findViewById(R.id.B_acquire);
        mSerialService = new BluetoothSerialService(this);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            finishDialogNoBluetooth();
            return;
        }

        B_acquire.setEnabled(false);
        /*
        B_pair.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
                    // Launch the DeviceListActivity to see devices and do scan
                    Intent serverIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                }
                else
                if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
                    String temp_send = "4\n1" ;
                    byte[] buffer = temp_send.getBytes() ;
                    send(buffer) ;
                    mSerialService.stop();
                    mSerialService.start();
                    B_pair.setText("Connect");
                    B_acquire.setEnabled(false);
                }
            }
        });
        */

        CB_Flag_AR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (((CheckBox) v).isChecked()) {
                    Edit_SS.setEnabled(false);
                }
                else{
                    Edit_SS.setEnabled(true) ;
                }
            }
        });
    }

    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            String str1 = (String) msg.obj;
            GraphView graph = (GraphView) findViewById(R.id.G01) ;
            double x,y;
            String[] Data = str1.split("\n") ; // Seperate the receive message by "\n"
            int Str_length = Data.length - 3 ;
            float start_wavelength = Float.parseFloat(Data[1]) ;
            float end_wavelength = Float.parseFloat(Data[2]) ;
            float step_wavelength = (end_wavelength - start_wavelength)/(Str_length - 1) ;
            double maxIntensity = 0 ;

            DataPoint[] dp = new DataPoint[Str_length] ;
            x = start_wavelength ;
            for (int i = 0; i<Str_length; i++){
                x = x + step_wavelength ;
                y = Float.parseFloat(Data[3+i]) ;
                dp[i] = new DataPoint(x, y) ;
                if (y > maxIntensity) {
                    maxIntensity = y ;
                }
            }
            graph.removeAllSeries();
            LineGraphSeries<DataPoint> series = new LineGraphSeries<>(dp);
            graph.addSeries(series);
            graph.setCursorMode(true);
            graph.setTitle("NSP32 measured spectrum");
            graph.setTitleTextSize(30);
            graph.getViewport().setMinX(start_wavelength);
            graph.getViewport().setMaxX(end_wavelength);
            graph.getViewport().setMinY(0);
            graph.getViewport().setMaxY(maxIntensity);
            graph.getViewport().setXAxisBoundsManual(true);
            graph.getViewport().setYAxisBoundsManual(true);

            mProgressDialog.dismiss() ;
        }
    } ;

    public void ClickConnect(View view){

        //B_pair = (Button) this.findViewById(R.id.B_pair);
        //B_acquire = (Button) this.findViewById(R.id.B_acquire);

        if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(MainActivity.this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
        }
        else
        if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
            String temp_send = "4\n1" ;
            byte[] buffer = temp_send.getBytes() ;
            send(buffer) ;
            mSerialService.stop();
            mSerialService.start();
            B_pair.setText("Connect");
            B_acquire.setEnabled(false);
        }
    }


    public void ClickAcquire(View view){
        mProgressDialog = ProgressDialog.show(MainActivity.this, "Measuring", "Please wait....", true) ;
        String temp_send ;
        if (CB_Flag_AR.isChecked()){
            temp_send = "2\n1" ;
        }
        else{
            temp_send = "2\n0" + "\n" + Edit_SS.getText() + "\n" + Edit_Avg.getText() ;
        }
        byte[] buffer = temp_send.getBytes() ;
        send(buffer);

        Runnable r = new Runnable() {
            @Override
            public void run() {
                String str1 = "";
                String tmp;

                tmp = mSerialService.read();

                // Receive the first segments of the data from RPi
                str1 = str1 + tmp ;
                String[] tempData = str1.split("\n") ;
                // Interpretate the first symbol to see how many bytes are sended.

                int K = Integer.parseInt(tempData[0])/1024 ;
                if (K > 0){
                    for (int i = 0; i< K; i++){
                        tmp = mSerialService.read();
                        str1 = str1 + tmp ;
                    }
                }
                Message tempMsg = mHandler.obtainMessage() ;
                tempMsg.obj = str1 ;
                mHandler.sendMessage(tempMsg) ;
            }
        } ;

        Thread AcquireThread = new Thread(r) ;
        AcquireThread.start();
    }

    public void send(byte[] out) {
        if ( out.length > 0 ) {
            mSerialService.write( out );
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //T01 = (TextView)this.findViewById(R.id.T01);
        //B_pair = (Button) this.findViewById(R.id.B_pair);
        //B_acquire = (Button) this.findViewById(R.id.B_acquire) ;
        //if(DEBUG) Log.d(LOG_TAG, "onActivityResult " + resultCode);
        switch (requestCode) {

            case REQUEST_CONNECT_DEVICE:

                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mSerialService.connect(device);
                    B_pair.setText("Disconnected");
                    B_acquire.setEnabled(true);
                }
                break;

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode != Activity.RESULT_OK) {
                    Log.d(LOG_TAG, "BT not enabled");

                    finishDialogNoBluetooth();
                }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        }
        /*else {
            if (mChatService == null) setupChat();*/
    }


    public void finishDialogNoBluetooth() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_dialog_no_bt)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.app_name)
                .setCancelable( false )
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public int getConnectionState() {
        return mSerialService.getState();
    }
}

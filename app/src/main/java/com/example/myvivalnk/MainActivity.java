package com.example.myvivalnk;

import androidx.appcompat.app.AppCompatActivity;
import androidx.multidex.MultiDex;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.vivalnk.sdk.Callback;
import com.vivalnk.sdk.CommandRequest;
import com.vivalnk.sdk.DataReceiveListener;
import com.vivalnk.sdk.VitalClient;
import com.vivalnk.sdk.ble.BluetoothConnectListener;
import com.vivalnk.sdk.ble.BluetoothScanListener;
import com.vivalnk.sdk.command.base.CommandType;
import com.vivalnk.sdk.common.ble.BleManagerInterface;
import com.vivalnk.sdk.common.ble.connect.BleConnectOptions;
import com.vivalnk.sdk.common.ble.scan.BleScanListener;
import com.vivalnk.sdk.common.ble.scan.ScanOptions;
import com.vivalnk.sdk.exception.VitalCode;
import com.vivalnk.sdk.model.BatteryInfo;
import com.vivalnk.sdk.model.Device;
import com.vivalnk.sdk.model.Motion;
import com.vivalnk.sdk.model.PatchStatusInfo;
import com.vivalnk.sdk.model.SampleData;
import com.vivalnk.sdk.open.config.ClockSyncConfig;
import com.vivalnk.sdk.open.config.LocationGrantConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String targetDeviceName = "ECGRec_202002/C740032";

    private Device targetDevice = null;

    private Button startBtn, stopBtn;
    private EditText fileNameEdit;


//            getCommandRequest(CommandType.eraseFlash, 3000);
//                VitalClient.getInstance().execute(eraseFlashRequest); // step 2. Clear flash data when connected

    private BluetoothScanListener myScanListener = new BluetoothScanListener() {
        @Override
        public void onDeviceFound(Device device) {
            //store the device
            if(device.getName().equals(targetDeviceName)){
                targetDevice = device;
                Log.e(TAG, "已扫描到设备"+ device.getName());
            }

            // TODO: 2020/11/12 扫描和连接应该分开，具体怎么把扫描到设备通知给连接？
            //connect
            BleConnectOptions opts = new BleConnectOptions.Builder()
                    .setConnectTimeout(10 * 1000)
                    .setAutoConnect(true)
                    .build();

            if(targetDevice!=null){
                VitalClient.getInstance().connect(targetDevice,opts,myConnectListener);
            }else {
                Log.e(TAG, "targetDevice=null" );
            }

            //communicate
            VitalClient.getInstance().registDataReceiver(device, myDataReceiveListener);

        }
    };

    private BluetoothConnectListener myConnectListener = new BluetoothConnectListener(){

        @Override
        public void onConnected(Device device) {
//            Toast.makeText(MainActivity.this,"已连接到设备"+ device.getName(),Toast.LENGTH_SHORT).show();
            Log.e(TAG, "已连接到设备"+ device.getName());
        }

        @Override
        public void onServiceReady(Device device) {

        }

        @Override
        public void onEnableNotify(Device device) {

        }

        @Override
        public void onDeviceReady(Device device) {

        }
    };


    private DataReceiveListener myDataReceiveListener = new DataReceiveListener() {
        @Override
        public void onReceiveRawData(Device device, Map<String, Object> map) {
        }

        @Override
        public void onReceiveSimpleData(Device device, Map<String, Object> map) {

        }

        @Override
        public void onReceiveData(Device device, Map<String, Object> map) {

            SampleData data = (SampleData) map.get("data");
            if(startBtn.getText().toString().equals("停止采")){
                try {
                    xieru((int[])data.extras.get("ecg"),fileNameEdit.getText().toString());
//                    Toast.makeText(MainActivity.this,"接收数据...",Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "接收数据..." );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }else {
                Log.e(TAG, "丢弃数据..." );
            }

//            if(stop){
//
//            }

        }

        @Override
        public void onBatteryChange(Device device, Map<String, Object> map) {
        }

        @Override
        public void onDeviceInfoUpdate(Device device, Map<String, Object> map) {

        }

        @Override
        public void onLeadStatusChange(Device device, boolean b) {

        }

        @Override
        public void onFlashStatusChange(Device device, int i) {

        }

        @Override
        public void onFlashUploadFinish(Device device) {

        }
    };

    //mutidex相关
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startBtn = findViewById(R.id.start);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                eraseFlash(); //清除之前的数据
                if(startBtn.getText().toString().equals("开始采")){
                    startBtn.setText("停止采");
                }else {
                    startBtn.setText("开始采");
                }
            }
        });

        stopBtn = findViewById(R.id.stop);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                VitalClient.getInstance().disconnect(targetDevice);
                Log.e(TAG, "蓝牙连接已断开");
            }
        });
        fileNameEdit = findViewById(R.id.fileName);

        //try to use sdk
        //init
        VitalClient.Builder builder = new VitalClient.Builder();

        //allow sdk obtain GPS location, default is not allow
        LocationGrantConfig locationGrantConfig = new LocationGrantConfig();
        locationGrantConfig.allow = true;
        builder.setLocationGrantConfig(locationGrantConfig);

        //set clock sync gap time once connected, default gapTime = 0, means
        //Do not force the synchronize the clock if the gapTime has been exceeded since the last synchronization
        ClockSyncConfig clockSyncConfig = new ClockSyncConfig();
        builder.setLocationGrantConfig(locationGrantConfig);

        VitalClient.getInstance().init(this, builder);

        //check ble
        int resultCode = VitalClient.getInstance().checkBle();
        if (resultCode != VitalCode.RESULT_OK) {
            Toast.makeText(MainActivity.this, "Vital Client runtime check failed", Toast.LENGTH_LONG).show();
        }

        //scan
        ScanOptions options = new ScanOptions.Builder()
                .setTimeout(5 * 1000)
                .build();
        VitalClient.getInstance().startScan(options, myScanListener);

        //connect

//        BleConnectOptions opts = new BleConnectOptions.Builder()
//                .setConnectTimeout(10 * 1000)
//                .setAutoConnect(true)
//                .build();
//
//        if(targetDevice!=null){
//            VitalClient.getInstance().connect(targetDevice,opts,myConnectListener);
//        }else {
//            Log.e(TAG, "targetDevice=null" );
//        }


    }

    public void xieru(int[] a, String filename){
        String path = "/wangc_vivalnk/";
        //如果不存在，就创建目录
        File dir = new File(Environment.getExternalStorageDirectory() + path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        FileWriter fileWriter= null;
        try {
            if(filename==null) filename = "noName";
            fileWriter = new FileWriter(""+dir+"/"+filename+".txt",true);
            for (int i = 0; i < a.length; i++) {
                fileWriter.write(String.valueOf(a[i])+"\n");//换行转意
            }
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void eraseFlash(){
        CommandRequest eraseFlashRequest = new CommandRequest.Builder()
                //set the request timeout, default is 10 second
                .setTimeout(100)
                //set the request type
                .setType(CommandType.eraseFlash)
                //set the request parameter, deferent request has deferent parameter, see sections follows for more detail
                .addParam("info", "VivaLnk")
                .build();

        VitalClient.getInstance().execute(targetDevice, eraseFlashRequest, new Callback() {
            @Override
            public void onStart() {
                Log.e(TAG, "开始擦除旧数据" );
            }
            @Override
            public void onComplete(Map<String, Object> data) {
//                Toast.makeText(MainActivity.this,"已擦除旧数据",Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "已擦除旧数据" );
            }
        });
    }
}
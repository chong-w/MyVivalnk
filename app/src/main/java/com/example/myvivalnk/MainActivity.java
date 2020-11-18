package com.example.myvivalnk;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.multidex.MultiDex;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
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
import com.vivalnk.sdk.open.config.NetworkGrantConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final int chargeChange = 1;
    private static final int getData = 2;

    private Device targetDevice;
    private ArrayList<Device> scannedDeviceList = new ArrayList<Device>();

    private Button receiveBtn, unreceiveBtnBtn, showScanBtn, endBtn;
    private EditText fileNameEdit;
    private TextView charge, dataNum;
    private int dataN = 0;//记录接收了多少组数据
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case chargeChange:
                    charge.setText("" + msg.arg1);
                    break;
                case getData:
                    dataNum.setText("" + msg.arg1);
                    break;
                default:
                    break;
            }
        }

        ;
    };

//    //扫描，连接，接收数据，擦除数据的回调
//    private BluetoothScanListener myScanListener = ;
//
//    private BluetoothConnectListener myConnectListener = ;
//
//    private DataReceiveListener myDataReceiveListener = ;
//
//    private Callback eraseFlashCallback = ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        charge = findViewById(R.id.charge);
        dataNum = findViewById(R.id.dataNum);
        showScanBtn = findViewById(R.id.scan);
        showScanBtn.setOnClickListener(this);
        receiveBtn = findViewById(R.id.receive);
        receiveBtn.setOnClickListener(this);
        unreceiveBtnBtn = findViewById(R.id.unreceive);
        unreceiveBtnBtn.setOnClickListener(this);
//        eraseBtn = findViewById(R.id.erase);
//        eraseBtn.setOnClickListener(this);
        endBtn = findViewById(R.id.end);
        endBtn.setOnClickListener(this);
        fileNameEdit = findViewById(R.id.fileName);

        //申请权限
        permissionReq();
        //开始扫描
        init();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.scan:
//                Log.e(TAG, "scan" );
                showScannedDevice();
                break;
            case R.id.receive:
//                Log.e(TAG, "receive" );
                eraseFlash(targetDevice);  //接收之前先把老数据清空，防止接收老数据
                registDataReceiver(targetDevice);
                break;
            case R.id.unreceive:
//                Log.e(TAG, "unreceive" );
                unregistDataReceiver(targetDevice);
                break;
//            case R.id.erase:
//                eraseFlash(targetDevice);
//                break;
            case R.id.end:
                disConnect(targetDevice);
                break;
            default:
                break;
        }
    }

    //内存，蓝牙，定位权限申请
    public void permissionReq() {
        String[] permissions = new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
        };
        List<String> mPermissionList = new ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            if (ContextCompat.checkSelfPermission(this, permissions[i]) !=
                    PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permissions[i]);//添加还未授予的权限到mPermissionList中
            }
        }
        //申请权限
        if (mPermissionList.size() > 0) {//有权限没有通过，需要申请
            ActivityCompat.requestPermissions(this, permissions, 100);
        }
    }

    //初始化、蓝牙扫描
    public void init() {

        VitalClient.Builder builder = new VitalClient.Builder();
        NetworkGrantConfig networkGrantConfig = new NetworkGrantConfig();
        networkGrantConfig.allow = true;
        builder.setNetworkGrantConfig(networkGrantConfig);
        //allow sdk obtain GPS location
        //default is not allow
        LocationGrantConfig locationGrantConfig = new LocationGrantConfig();
        locationGrantConfig.allow = true;
        builder.setLocationGrantConfig(locationGrantConfig);
        //set clock sync gap time once connected
        //default gapTime = 0, means
        //Do not force the synchronize the clock if the gapTime has been exceeded since the last synchronization
        ClockSyncConfig clockSyncConfig = new ClockSyncConfig();
        builder.setClockSyncConfig(clockSyncConfig);
        VitalClient.getInstance().init(MainActivity.this, builder);
        //扫描
        ScanOptions options = new ScanOptions.Builder()
                .setTimeout(10 * 1000)
                .build();
        VitalClient.getInstance().startScan(options, new BluetoothScanListener() {
            @Override
            public void onDeviceFound(Device device) {
//                Log.e(TAG, device.getName() );
                if (!scannedDeviceList.contains(device)) {
                    scannedDeviceList.add(device);
                }
            }

            @Override
            public void onStart() {
//                Log.e(TAG, "开始扫描");
                Toast.makeText(MainActivity.this, "开始扫描", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStop() {
//                Log.e(TAG, "扫描结束");
                Toast.makeText(MainActivity.this, "扫描结束", Toast.LENGTH_SHORT).show();
            }
        });
    }

    //蓝牙扫描结果
    public void showScannedDevice() {
        List<String> nameList = new ArrayList<>();
        for (Device bd : scannedDeviceList) {
            nameList.add(bd.getName());
        }
        //弹出
        final Context context = MainActivity.this;
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.activity_device_list, null);
        ListView deviceListView = (ListView) layout.findViewById(R.id.binded_devices);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, R.layout.support_simple_spinner_dropdown_item, nameList);
        deviceListView.setAdapter(adapter);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(layout);
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
                targetDevice = scannedDeviceList.get(position);
                connectDevice(targetDevice);
                alertDialog.dismiss();
            }
        });
    }

    //蓝牙连接
    public void connectDevice(Device device) {
        BleConnectOptions opts = new BleConnectOptions.Builder()
                .setConnectTimeout(10 * 1000)
                .setAutoConnect(true)
                .build();
        VitalClient.getInstance().connect(device, opts, new BluetoothConnectListener() {

            @Override
            public void onConnected(Device device) {
                Toast.makeText(MainActivity.this, "已连接到设备" + device.getName(), Toast.LENGTH_LONG).show();
//                eraseFlash(targetDevice);
//                Log.e(TAG, "已连接到设备"+ device.getName());
            }

            @Override
            public void onServiceReady(Device device) {
//                Toast.makeText(MainActivity.this,"onServiceReady"+ device.getName(),Toast.LENGTH_LONG).show();
//                Log.e(TAG, "onServiceReady"+ device.getName());
            }

            @Override
            public void onDeviceReady(Device device) {
//                Toast.makeText(MainActivity.this,"onDeviceReady"+ device.getName(),Toast.LENGTH_LONG).show();
//                Log.e(TAG, "onDeviceReady"+ device.getName());
            }
        });
    }

    //注册数据接收
    public void registDataReceiver(Device device) {
        VitalClient.getInstance().registDataReceiver(device, new DataReceiveListener() {
            @Override
            public void onReceiveData(Device device, Map<String, Object> map) {
                dataN++;
                Message msg = new Message();
                msg.what = getData;
                msg.arg1 = dataN;
                handler.sendMessage(msg);
//                Log.e(TAG, "onReceiveData: ");
                SampleData data = (SampleData) map.get("data");
                try {
                    write((int[]) data.extras.get("ecg"), fileNameEdit.getText().toString());
//                    Toast.makeText(MainActivity.this,"接收数据...",Toast.LENGTH_SHORT).show();
//                    Log.e(TAG, "接收数据..." );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onBatteryChange(Device device, Map<String, Object> map) {
                int battery = ((BatteryInfo) map.get("data")).getPercent();
                Message msg = new Message();
                msg.what = chargeChange;
                msg.arg1 = battery;
                handler.sendMessage(msg);
//                Log.e(TAG, "onBatteryChange: "+ battery);
            }

            @Override
            public void onFlashStatusChange(Device device, int i) {
                //没上传的数据块数 一块4kb, i是块数
            }
        });
    }

    //反注册数据接收
    public void unregistDataReceiver(Device device) {
        VitalClient.getInstance().unregistDataReceiver(device);
        dataN = 0;
        Message msg = new Message();
        msg.what = getData;
        msg.arg1 = dataN;
        handler.sendMessage(msg);
    }

    //蓝牙断开
    public void disConnect(Device device) {
        VitalClient.getInstance().disconnect(device);
        Toast.makeText(MainActivity.this, "蓝牙连接已断开", Toast.LENGTH_SHORT).show();
//        Log.e(TAG, "蓝牙连接已断开");
    }

    //擦除flash
    public void eraseFlash(Device device) {
        CommandRequest eraseFlashRequest = new CommandRequest.Builder()
                //set the request timeout, default is 10 second
                .setTimeout(3000)
                //set the request type
                .setType(CommandType.eraseFlash)
                //set the request parameter, deferent request has deferent parameter, see sections follows for more detail
                .addParam("info", "VivaLnk")
                .build();
        VitalClient.getInstance().execute(device, eraseFlashRequest, new Callback() {
            @Override
            public void onStart() {
//                Toast.makeText(MainActivity.this,"已擦除旧数据",Toast.LENGTH_SHORT).show();
                Log.e(TAG, "开始擦除旧数据");
            }

            @Override
            public void onComplete(Map<String, Object> data) {
//                Toast.makeText(MainActivity.this,"已擦除旧数据",Toast.LENGTH_SHORT).show();
                Log.e(TAG, "已擦除旧数据");
            }
        });
    }

    //保存数据
    public void write(int[] a, String filename) {
//        Log.e(TAG, "write FirstECG: "+ a[0] );
        String path = "/wangc_vivalnk/";
        //如果不存在，就创建目录
        File dir = new File(Environment.getExternalStorageDirectory() + path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        FileWriter fileWriter = null;
        try {
            if (filename == null) filename = "noName";
            fileWriter = new FileWriter("" + dir + "/" + filename + ".txt", true);
            for (int i = 0; i < a.length; i++) {
                fileWriter.write(String.valueOf(a[i]) + "\n");//换行转意
            }
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
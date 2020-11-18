package com.example.myvivalnk;

import com.vivalnk.sdk.VitalClient;
import com.vivalnk.sdk.ble.BluetoothScanListener;
import com.vivalnk.sdk.common.ble.scan.ScanOptions;

public class ScanThread implements Runnable {

    private BluetoothScanListener myScanListener;

    public void setMyScanListener(BluetoothScanListener myScanListener) {
        this.myScanListener = myScanListener;
    }

    @Override
    public void run() {
        ScanOptions options = new ScanOptions.Builder()
                .setTimeout(5 * 1000)
                .build();
        VitalClient.getInstance().startScan(options, myScanListener);
    }
}

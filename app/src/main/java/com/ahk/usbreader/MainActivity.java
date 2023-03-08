package com.ahk.usbreader;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;


public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final int TARGET_VENDOR_ID= 1155;
    private static final int TARGET_PRODUCT_ID = 22336;
    private static final String ACTION_USB_PERMISSION =  "com.android.example.USB_PERMISSION";
    PendingIntent mPermissionIntent;


    UsbDevice mUsbDevice = null;
    UsbManager mUsbManager = null ;
    UsbInterface mUsbInterface = null;
    UsbEndpoint mEndPointIn = null;
    UsbEndpoint mEndPointOut = null;
    UsbDeviceConnection mUsbDeviceConnection;
    UsbRequest mUsbRequest ;

    ScrollView scrollview ;
    TextView deviceInfo, manufacturer, mDisplay ;
    EditText etCmdOne, etCmdTwo, etCmdThree;
    Button btnCmdOne, btnCmdTwo, btnCmdThre, btnClear;

    private Handler handler;

    Boolean isActivityAlive = true ;
    String usbRxBuffer = "received : \r\n";
    String UsbRTxBuffer  = "\n" ;
    Boolean newDataToSend = false ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPermissionIntent = PendingIntent.getBroadcast(this, 1, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        handler = new Handler(Looper.getMainLooper());

        scrollview = ((ScrollView) findViewById(R.id.scroll_view));

        deviceInfo = findViewById(R.id.device_info);
        manufacturer = findViewById(R.id.device_manufacturer);

        btnCmdOne = findViewById(R.id.btn_cmd_one);
        btnCmdTwo = findViewById(R.id.btn_cmd_two);
        btnCmdThre = findViewById(R.id.btn_cmd_three);
        btnClear = findViewById(R.id.btn_clear);

        etCmdOne = findViewById(R.id.et_cmd_one);
        etCmdTwo  = findViewById(R.id.et_cmd_two);
        etCmdThree  = findViewById(R.id.et_cmd_three);

        mDisplay = findViewById(R.id.rx_bytes);

        btnCmdOne.setOnClickListener(this);
        btnCmdTwo.setOnClickListener(this);
        btnCmdThre.setOnClickListener(this);
        btnClear.setOnClickListener(this);

        registerReceiver(mUsbDeviceReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        registerReceiver(mUsbDeviceReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
 //       initUsbInterface();
    }

    @Override
    protected void onResume() {
        if(initUsbInterface() == true){
            if(initUsbCommunication() == true){

            }
        }
        super.onResume();
    }

    @Override
    protected void onDestroy() {
//        releaseUsb();
//        unregisterReceiver(mUsbReceiver);
//        unregisterReceiver(mUsbDeviceReceiver);

        isActivityAlive = false ;
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        try {
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        switch (view.getId()){
            case R.id.btn_cmd_one:
                usbTransmit(etCmdOne.getText().toString());
                break;
            case R.id.btn_cmd_two:
                usbTransmit(etCmdTwo.getText().toString());
                break;
            case R.id.btn_cmd_three:
                usbTransmit(etCmdThree.getText().toString());
                break;
            case R.id.btn_clear:
                usbRxBuffer = "";
                mDisplay.setText("");
                break;
            default:
                break;
        }
    }

    private Boolean initUsbInterface(){
        Boolean status = false ;
        mUsbInterface = null;
        mEndPointOut = null;
        mEndPointIn = null;

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if(device.getVendorId() == TARGET_VENDOR_ID){
                if(device.getProductId() == TARGET_PRODUCT_ID){
                    mUsbDevice = device;
                    break;
                }
            }
        }
        if(mUsbDevice != null){
            String s = "DeviceID : " + mUsbDevice.getDeviceId() + "\n" + "DeviceName: " + mUsbDevice.getDeviceName() + "\n" ;
            deviceInfo.setText(s);
            UsbInterface usbInterfacew = null;
            UsbEndpoint endpOut = null;
            UsbEndpoint endpIn = null;

            for(int i=0; i<mUsbDevice.getInterfaceCount(); i++){
                usbInterfacew = mUsbDevice.getInterface(i);
                int tEndpointCnt = usbInterfacew.getEndpointCount();
                if(tEndpointCnt>=2){
                    for(int j=0; j<tEndpointCnt; j++){
                        if(usbInterfacew.getEndpoint(j).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK){
                            if(usbInterfacew.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_OUT){
                                endpOut = usbInterfacew.getEndpoint(j);
                            }else if(usbInterfacew.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_IN){
                                endpIn = usbInterfacew.getEndpoint(j);
                            }
                        }
                    }
                }
            }
            if(endpOut != null && endpIn != null && usbInterfacew != null){
                mUsbInterface = usbInterfacew;
                mEndPointOut = endpOut;
                mEndPointIn = endpIn;
                status = true;
            }else{
                s += "\n no interface found " ;
                deviceInfo.setText(s);
            }
        }else{
            deviceInfo.setText("device not found");
        }
        return status ;

//        usbSearchEndPoint();
//        if(mUsbInterface != null){
//            if(initUsbCommunication() == true){
//                new Thread(new UsbReceiver()).start();
//                new Thread(new UsbTranmitter()).start();
//                //usbTransmit("test data ");
//                //usbReceive(mUsbDeviceConnection, mEndPointIn);
//                //usbTransmit("tx started");
//            }
//        }
    }

    private void usbRelease(){
        if(mUsbDeviceConnection != null){
            if(mUsbInterface != null){
                mUsbDeviceConnection.releaseInterface(mUsbInterface);
                mUsbInterface = null;
            }
            mUsbDeviceConnection.close();
            mUsbDeviceConnection = null;
        }

        mUsbDevice = null;
        mUsbInterface = null;
        mEndPointIn = null;
        mEndPointOut = null;
    }

    private boolean initUsbCommunication(){

        final int RQSID_SET_LINE_CODING = 0x20;
        final int RQSID_SET_CONTROL_LINE_STATE = 0x22;

        boolean success = false;

        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        Boolean permitToRead = manager.hasPermission(mUsbDevice);
        if(!permitToRead) {
            manager.requestPermission(mUsbDevice, mPermissionIntent);
        }
        else{
            mUsbDeviceConnection = manager.openDevice(mUsbDevice);
            if(mUsbDeviceConnection != null){
                mUsbDeviceConnection.claimInterface(mUsbInterface, true);

                showRawDescriptors(); //skip it if you no need show RawDescriptors

                int usbResult = mUsbDeviceConnection.controlTransfer(
                        0x21,        //requestType
                        RQSID_SET_CONTROL_LINE_STATE, //SET_CONTROL_LINE_STATE
                        0,     //value
                        0,     //index
                        null,    //buffer
                        0,     //length
                        0);    //timeout

                Toast.makeText(MainActivity.this, "controlTransfer(SET_CONTROL_LINE_STATE): " + usbResult, Toast.LENGTH_LONG).show();

                //baud rate = 9600
                //8 data bit
                //1 stop bit
                byte[] encodingSetting = new byte[] {(byte)0x80, 0x25, 0x00, 0x00, 0x00, 0x00, 0x08 };
                usbResult = mUsbDeviceConnection.controlTransfer(
                        0x21,       //requestType
                        RQSID_SET_LINE_CODING,   //SET_LINE_CODING
                        0,      //value
                        0,      //index
                        encodingSetting,  //buffer
                        7,      //length
                        0);     //timeout
                success = true ;
                new Thread(new UsbReceiver()).start();
                new Thread(new UsbTranmitter()).start();
            }
        }
        return success;
    }
    private void usbSearchEndPoint(){
        if(mUsbDevice == null){
            mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            while (deviceIterator.hasNext()) {
                UsbDevice device = deviceIterator.next();
                if(device.getVendorId() == TARGET_VENDOR_ID){
                    if(device.getProductId() == TARGET_PRODUCT_ID){
                        mUsbDevice = device;
                    }
                }
            }
        }

        if(mUsbDevice==null){
            deviceInfo.setText("device not found");
        }else{
            String s = "DeviceID : " + mUsbDevice.getDeviceId() + "\n" + "DeviceName: " + mUsbDevice.getDeviceName() + "\n" ;
            deviceInfo.setText(s);
            for(int i=0; i<mUsbDevice.getInterfaceCount(); i++){
                UsbInterface usbInterfacew = mUsbDevice.getInterface(i);
                UsbEndpoint endpOut = null;
                UsbEndpoint endpIn = null;

                int tEndpointCnt = usbInterfacew.getEndpointCount();
                if(tEndpointCnt>=2){
                    for(int j=0; j<tEndpointCnt; j++){
                        if(usbInterfacew.getEndpoint(j).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK){
                            if(usbInterfacew.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_OUT){
                                endpOut = usbInterfacew.getEndpoint(j);
                            }else if(usbInterfacew.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_IN){
                                endpIn = usbInterfacew.getEndpoint(j);
                            }
                        }
                    }
                    if(endpOut != null && endpIn != null){
                        mUsbInterface = usbInterfacew;
                        mEndPointOut = endpOut;
                        mEndPointIn = endpIn;
                    }
                }

            }
            if(mUsbInterface==null){
                s += "\n no interface found " ;
                deviceInfo.setText(s);
            }
        }
    }

    private void showRawDescriptors(){
        final int STD_USB_REQUEST_GET_DESCRIPTOR = 0x06;
        final int LIBUSB_DT_STRING = 0x03;

        byte[] buffer = new byte[255];
        int indexManufacturer = 14;
        int indexProduct = 15;
        String stringManufacturer = "";
        String stringProduct = "";

        byte[] rawDescriptors = mUsbDeviceConnection.getRawDescriptors();

        int lengthManufacturer = mUsbDeviceConnection.controlTransfer(
                UsbConstants.USB_DIR_IN|UsbConstants.USB_TYPE_STANDARD,   //requestType
                STD_USB_REQUEST_GET_DESCRIPTOR,         //request ID for this transaction
                (LIBUSB_DT_STRING << 8) | rawDescriptors[indexManufacturer], //value
                0,   //index
                buffer,  //buffer
                0xFF,  //length
                0);   //timeout
        try {
            stringManufacturer = new String(buffer, 2, lengthManufacturer-2, "UTF-16LE");
        } catch (UnsupportedEncodingException e) {
            Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_LONG).show();
            //textStatus.setText(e.toString());
        }

        int lengthProduct = mUsbDeviceConnection.controlTransfer(
                UsbConstants.USB_DIR_IN|UsbConstants.USB_TYPE_STANDARD,
                STD_USB_REQUEST_GET_DESCRIPTOR,
                (LIBUSB_DT_STRING << 8) | rawDescriptors[indexProduct],
                0,
                buffer,
                0xFF,
                0);
        try {
            stringProduct = new String(buffer, 2, lengthProduct-2, "UTF-16LE");
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        manufacturer.setText("Manufacturer: " + stringManufacturer + "\n" + "Product: " + stringProduct);
    }
    void usbTransmit(String txData){
        UsbRTxBuffer = txData ;
        newDataToSend = true;
    }
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) == true) {
                        if(device != null){
                            if(initUsbCommunication() == true){

                            }
                        }
                    }
                    else {
                        Toast.makeText(MainActivity.this, "usb perimission denied" + device, Toast.LENGTH_LONG).show();
                        // textStatus.setText("permission denied for device " + device);
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mUsbDeviceReceiver =  new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                        mUsbDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        //Toast.makeText(MainActivity.this, "ACTION_USB_DEVICE_ATTACHED: \n" + mUsbDevice.toString(), Toast.LENGTH_LONG).show();
                        //connectUsb();
                    }else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                        UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        // Toast.makeText(MainActivity.this, "ACTION_USB_DEVICE_DETACHED: \n" + device.toString(), Toast.LENGTH_LONG).show();
                        // textStatus.setText("ACTION_USB_DEVICE_DETACHED: \n" + device.toString());
                        if(device!=null){
                            if(device == mUsbDevice){
                                usbRelease();
                            }
                        }
                        // textInfo.setText("");
                    }
                }
            };

    class UsbTranmitter implements Runnable{
        @Override
        public void run() {
            UsbRequest request = new UsbRequest();
            if(request.initialize(mUsbDeviceConnection, mEndPointOut)){
                int bufferMaxLength = mEndPointOut.getMaxPacketSize();
                ByteBuffer byteBuffer = ByteBuffer.allocate(bufferMaxLength);
                while (isActivityAlive) {
                    if(newDataToSend){
                        byteBuffer = byteBuffer.wrap(UsbRTxBuffer.getBytes(StandardCharsets.UTF_8));
                        try{
                            if (request.queue(byteBuffer) == true) {
                                if (mUsbDeviceConnection.requestWait() == request) {

                                }
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                        newDataToSend = false;
                        byteBuffer.clear();
                    }else{
                        try{ Thread.sleep(100);}catch(Exception e){ e.printStackTrace();}
                    }
                }
            }
            else {
                return;
            }
        }
    }
    class UsbReceiver  implements Runnable{
        @Override
        public void run() {
            UsbRequest request = new UsbRequest();
            if(request.initialize(mUsbDeviceConnection, mEndPointIn)){
                int bufferMaxLength = mEndPointIn.getMaxPacketSize();
                ByteBuffer byteBuffer = ByteBuffer.allocate(bufferMaxLength);
                while (isActivityAlive) {
                    if (request.queue(byteBuffer, 10) == true) {
                        if (mUsbDeviceConnection.requestWait() == request) {
                            String newContent = new String(byteBuffer.array(), StandardCharsets.UTF_8);
                            usbRxBuffer += "\n";
                            usbRxBuffer += newContent;
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mDisplay.setText(usbRxBuffer);
                                    scrollview.fullScroll(ScrollView.FOCUS_DOWN);
//                                    scrollview.post(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            scrollview.fullScroll(ScrollView.FOCUS_DOWN);
//                                        }
//                                    });
                                }
                            });
                        }
                    }
                    byteBuffer.clear();
                }
            }else{
                return ;
            }
        }
    }
}
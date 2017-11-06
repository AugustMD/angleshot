package com.example.osko.angleshot;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.app.Activity;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.widget.Toast;

import com.example.osko.angleshot.driver.UsbSerialDriver;
import com.example.osko.angleshot.driver.UsbSerialPort;
import com.example.osko.angleshot.driver.UsbSerialProber;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CameraPreview extends Activity {

    private static final String TAG = "카메라 작동";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;

    //private ImageView imageView;
    private boolean inProgress;
    //private Button button;
    private boolean isSend;

    private static final int SOCKET_TIMEOUT = 5000;
    public static BufferedOutputStream out;
    public static DataInputStream in;
    public static ObjectOutputStream oos;

    private boolean mPressFirstBackKey = false;
    private Timer timer;
    private BroadcastReceiver receiver = null;
    private final IntentFilter intentFilter = new IntentFilter();
    public WifiP2pManager manager;
    public WifiP2pManager.Channel channel;

    public boolean isShare;

    private UsbSerialPort sPort = null;

    UsbSerialDriver driver;
    UsbDeviceConnection connection;
    byte[] byteArray;

    public void portOpen() {
        if (sPort == null) {
            Toast.makeText(this, "Opening SerialPort failed", Toast.LENGTH_SHORT).show();
        } else {
            try {
                sPort.open(connection);
                sPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Toast.makeText(this, "Error opening device : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                try {
                    sPort.close();
                } catch (IOException e2) {
                }
                sPort = null;
                return;
            }
        }
    }

    public void Up() {
        if(connection != null) {
            byteArray[0] = 'u';
            try {
                sPort.write(byteArray, 100);
            } catch (IOException e) {
                Toast.makeText(this, "Up Key don't respond", Toast.LENGTH_SHORT).show();
            }
        }
    }
    public void Down() {
        if(connection != null) {
            byteArray[0] = 'd';
            try {
                sPort.write(byteArray, 100);
            } catch (IOException e) {
                Toast.makeText(this, "Down Key don't respond", Toast.LENGTH_SHORT).show();
            }
        }
    }
    public void Left() {
        if(connection != null) {
            byteArray[0] = 'l';
            try {
                sPort.write(byteArray, 100);
            } catch (IOException e) {
                Toast.makeText(this, "Left Key don't respond", Toast.LENGTH_SHORT).show();
            }
        }
    }
    public void Right() {
        if(connection != null) {
            byteArray[0] = 'r';
            try {
                sPort.write(byteArray, 100);
            } catch (IOException e) {
                Toast.makeText(this, "Right Key don't respond", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void start(){
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }
        driver = availableDrivers.get(0);
        connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            Toast.makeText(this,"연결에 실패했습니다.",Toast.LENGTH_SHORT).show();
            return;
        }
        else{
            Toast.makeText(this,"연결 되었습니다."+driver.getDevice().getDeviceName(),Toast.LENGTH_SHORT).show();
        }
        sPort = driver.getPorts().get(0);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.camera_preview);

        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(surfaceListener);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        isShare = false;

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        byteArray = new byte[1];

        start();
        portOpen();

        new SocketConnectThread().start();

    }
    @Override
    public void onResume() {
        super.onResume();
        receiver = new DisconnectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
            }

            @Override
            public void onSuccess() {
            }
        });
    }
    @Override
    public void onStop() {
        super.onStop();
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
            }

            @Override
            public void onSuccess() {
            }
        });

        if(camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
            Log.i(TAG, "카메라 기능 해제");
        }
    }



    public void onBackPressed() {
        if (mPressFirstBackKey == false) {
            Toast.makeText(CameraPreview.this, "\'뒤로\' 버튼을 한번 더 누르면 종료합니다.", Toast.LENGTH_SHORT).show();
            mPressFirstBackKey = true;
            TimerTask second = new TimerTask() {
                @Override
                public void run() {
                    timer.cancel();
                    timer = null;
                    mPressFirstBackKey = false;
                }
            };
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            timer = new Timer();
            timer.schedule(second, 2000);
        } else {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

                @Override
                public void onFailure(int reasonCode) {
                    Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

                }

                @Override
                public void onSuccess() {
                }

            });
        }
    }

    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback()
    {
        public void onPreviewFrame(byte[] data, Camera camera)
        {
            try
            {
                    Camera.Parameters mParameters = camera.getParameters();
                    Camera.Size mSize = mParameters.getPreviewSize();
                    int mWidth = mSize.width;
                    int mHeight = mSize.height;

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, mWidth, mHeight, null);
                    yuvImage.compressToJpeg(new Rect(0, 0, mWidth, mHeight), 30, out);

                    byte[] imageBytes = out.toByteArray();

                if(data != null && isSend && !isShare) {

                    isSend = false;
                    new ImageSendThread(imageBytes).execute();
                }

            }
            catch(Exception e)
            {

            }
        }

    };

    private Camera.PictureCallback takepicture = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.i(TAG, "샷다 누름 확인");

            if(data != null) {
                Log.i(TAG, "JPEG 사진 찍었음!");
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

                new StorePhoto(bitmap).execute();

                camera.startPreview();

                inProgress = false;
            }
        }
    };

    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
        /* Empty Callbacks play a sound! */
        }
    };

    private SurfaceHolder.Callback surfaceListener = new SurfaceHolder.Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(width, height);
            camera.startPreview();
            Log.i(TAG, "카메라 미리보기 활성");

        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            camera = Camera.open();

            Log.i(TAG,"카메라 시작, 렌즈 오픈");

            try {
                camera.setPreviewDisplay(holder);
                for(int i=0; i<10000000; i++) {}
                camera.setPreviewCallback(previewCallback);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if(camera != null) {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
                camera = null;
                Log.i(TAG, "카메라 기능 해제");
            }

        }
    };

    class ReceiveThread extends Thread {
        DataInputStream input;

        public ReceiveThread() {
            input = in;
        }

        public void run() {
            try {
                Log.d(ACTIVITY_SERVICE, "receive");
                while (input != null) {
                    System.out.println(this + "       THREAD!");
                    Log.d(ACTIVITY_SERVICE, "receive while");
                    String msg = input.readUTF();
                    if (msg != null) {
                        Log.d(ACTIVITY_SERVICE, "receive run");
                        if(msg.equals("SendSuccess")) { // preview 전송
                            isSend = true;
                        }
                        else if(msg.equals("Shutter")) { // 사진찍기
                            camera.autoFocus(new Camera.AutoFocusCallback() {
                                public void onAutoFocus(boolean success, Camera camera) {
                                    if (success) {
                                        camera.takePicture(shutterCallback, null, takepicture);
                                    }
                                }
                            });
                        }
                        else if(msg.equals("Shutter2")) { // 사진찍고 공유하기
                            Log.d(ACTIVITY_SERVICE, "SHUTTER2222");
                            camera.autoFocus(new Camera.AutoFocusCallback() {
                                public void onAutoFocus(boolean success, Camera camera) {
                                    if (success) {
                                        isShare = true;
                                        camera.takePicture(shutterCallback, null, takepicture);
                                    }
                                }
                            });
                        }
                        else if(msg.equals("ShareSuccess")) { // 공유(저장) 성공
                            Log.d(ACTIVITY_SERVICE, "Share Success");
                            isShare = false; // 프리뷰 전송 시작
                        }
                        else if(msg.equals("up")) { Up(); }
                        else if(msg.equals("down")) { Down(); }
                        else if(msg.equals("left")) { Left(); }
                        else if(msg.equals("right")) { Right(); }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(ACTIVITY_SERVICE, "receive thread 끝");
        }
    }

    class SocketConnectThread extends Thread {

        public void run() {
            try {
                String host = DeviceDetailFragment.host;
                Socket socket = new Socket();
                int port = 8988;

                try {
                    Log.d(MainActivity.TAG, "Opening client socket - ");
                    Log.d(MainActivity.TAG, "host : " + host + "port : " + port);
                    socket.bind(null);
                    socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                    Log.d(MainActivity.TAG, "Client socket - " + socket.isConnected());
                    out = new BufferedOutputStream(socket.getOutputStream());
                    in = new DataInputStream(socket.getInputStream());

                    oos = new ObjectOutputStream(socket.getOutputStream());

                    Log.d(MainActivity.TAG, "Client: Data written");
                } catch (IOException e) {
                    Log.e(MainActivity.TAG, e.getMessage());
                }
                isSend = true;
                new ReceiveThread().start();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] getByte(int num) {
        byte[] buf = new byte[4];
        buf[0] = (byte)( (num >> 24) & 0xFF );
        buf[1] = (byte)( (num >> 16) & 0xFF );
        buf[2] = (byte)( (num >>  8) & 0xFF );
        buf[3] = (byte)( (num >>  0) & 0xFF );

        return buf;
    }
    public class ImageSendThread extends AsyncTask<Void, Void, String> {

        BufferedOutputStream output;
        ObjectOutputStream oosOutput;
        byte[] data;
        String path;
        String name;
        Bitmap photo;

        public ImageSendThread(byte[] photo) {
            output = out;
            data = photo;
            this.path = null;
            name = null;
            this.photo = null;
        }

        public ImageSendThread(Bitmap photo, String path, String name) {
            output = out;
            oosOutput = oos;
            this.photo = photo;
            this.path = path;
            this.name = name;
        }

        @Override
        protected String doInBackground(Void... params) {

            try {
                Log.d(ACTIVITY_SERVICE, "image    11111");

                if (output != null) {
                    if(path == null) {
                        Log.d(ACTIVITY_SERVICE, " path ================ null");
                        //byte의 총 크기를 4바이트에 담아 보낸다
                        byte[] size = getByte(data.length);
                        System.out.println(data.length);
                        output.write(size, 0, size.length);


                        //실제 데이터를 보낸다
                        output.write(data, 0, data.length);
                        output.flush();
                    }
                    else {
                        synchronized (this) {
                            Log.d(ACTIVITY_SERVICE, " path ================ 20160215");
                            byte[] size = getByte(20160215);
                            output.write(size, 0, size.length);
                            output.flush();

                            SharePacketObject spo = new SharePacketObject(photo, name);
                            oosOutput.writeObject(spo);
                        }
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException npe) {
                npe.printStackTrace();
            }

            return null;
        }

    }


    public class StorePhoto extends AsyncTask<Void, Void, String> {
        Bitmap bitmap;
        String file_name;
        String string_path;

        public StorePhoto(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        protected String doInBackground(Void... params) {

            String ex_storage = Environment.getExternalStorageDirectory().getAbsolutePath();
            // Get Absolute Path in External Sdcard
            String foler_name = "/AngleShot/";
            SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            file_name = date.format(new Date())+".jpg";
            string_path = ex_storage+foler_name;

            File file_path;
            try{
                file_path = new File(string_path);
                if(!file_path.isDirectory()){
                    file_path.mkdirs();
                }
                FileOutputStream out = new FileOutputStream(string_path+file_name);

                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    Intent mediaScanIntent = new Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    File f = new File(string_path+file_name);
                    Uri contentUri = Uri.fromFile(f); //out is your output file
                    mediaScanIntent.setData(contentUri);
                    sendBroadcast(mediaScanIntent);
                } else {
                    sendBroadcast(new Intent(
                            Intent.ACTION_MEDIA_MOUNTED,
                            Uri.parse("file://"
                                    + Environment.getExternalStorageDirectory())));
                }
                out.close();

            }catch(FileNotFoundException exception){
                Log.e("FileNotFoundException", exception.getMessage());
            }catch(IOException exception){
                Log.e("IOException", exception.getMessage());
            }

            return null;
        }
        //asyonTask 3번째 인자와 일치 매개변수값 -> doInBackground 리턴값이 전달됨
        //AsynoTask 는 preExcute - doInBackground - postExecute 순으로 자동으로 실행됩니다.
        //ui는 여기서 변경
        protected void onPostExecute(String value){
            super.onPostExecute(value);
            Toast.makeText(CameraPreview.this, "사진이 저장되었습니다.", Toast.LENGTH_SHORT).show();
            if(isShare) {
                Log.d(ACTIVITY_SERVICE, "사진 저장");

                new ImageSendThread(bitmap, string_path, file_name).execute();

            }

        }

    }

}

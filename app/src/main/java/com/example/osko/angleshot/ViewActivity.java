package com.example.osko.angleshot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.app.Activity;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Timer;
import java.util.TimerTask;

public class ViewActivity extends Activity {
    private SurfaceView surfaceView2;
    private SurfaceHolder surfaceHolder;
    private Button shutter;
    private Button shutter2;
    private Button up;
    private Button down;
    private Button left;
    private Button right;


    private ReceiveThread m_Thread;

    Handler msghandler;

    int width;
    int height;

    private boolean mPressFirstBackKey = false;
    private Timer timer;

    private BroadcastReceiver receiver = null;
    private final IntentFilter intentFilter = new IntentFilter();
    public WifiP2pManager manager;
    public WifiP2pManager.Channel channel;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.view_activity);

        surfaceView2 = (SurfaceView) findViewById(R.id.surfaceView2);
        surfaceHolder = surfaceView2.getHolder();
        surfaceHolder.addCallback(surfaceListener);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        shutter = (Button) findViewById(R.id.shutter);
        shutter2 = (Button) findViewById(R.id.shutter2);
        m_Thread = new ReceiveThread(surfaceHolder, ViewActivity.this);

        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay();
        width  = display.getWidth();
        height = display.getHeight();

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);


        shutter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new SendTask("Shutter").execute();
            }
        });
        shutter2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new SendTask("Shutter2").execute();
            }
        });

        up = (Button) findViewById(R.id.Up);
        down = (Button) findViewById(R.id.Down);
        left = (Button) findViewById(R.id.Left);
        right = (Button) findViewById(R.id.Right);

        up.setOnTouchListener(new RepeatListener(400, 35, new View.OnClickListener() {
            @Override
            public void onClick(View v) { new SendTask("up").execute(); }
        }));
        down.setOnTouchListener(new RepeatListener(400, 35, new View.OnClickListener() {
            @Override
            public void onClick(View v) { new SendTask("down").execute(); }
        }));
        left.setOnTouchListener(new RepeatListener(400, 35, new View.OnClickListener() {
            @Override
            public void onClick(View v) { new SendTask("left").execute(); }
        }));
        right.setOnTouchListener(new RepeatListener(400, 35, new View.OnClickListener() {
            @Override
            public void onClick(View v) { new SendTask("right").execute(); }
        }));
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
            }

            @Override
            public void onSuccess() {
            }

        });
    }


    public void onBackPressed() {
        if (mPressFirstBackKey == false) {
            Toast.makeText(ViewActivity.this, "\'뒤로\' 버튼을 한번 더 누르면 종료합니다.", Toast.LENGTH_SHORT).show();
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
                    Log.d(ACTIVITY_SERVICE, "Disconnect failed. Reason :" + reasonCode);

                }

                @Override
                public void onSuccess() {
                }

            });
        }
    }

    public void onDraw(Canvas canvas, Bitmap image) {
        try {
            canvas.drawBitmap(Bitmap.createScaledBitmap(image, width, height, true), 0, 0, null);
        } catch (NullPointerException e) {
            System.out.println("onDraw NULL");
        }
    }

    private SurfaceHolder.Callback surfaceListener = new SurfaceHolder.Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            m_Thread.setRunning(true);
            m_Thread.start();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            boolean retry = true;
            m_Thread.setRunning(false);
            while(retry) {
                try {
                    m_Thread.join();
                    retry = false;
                } catch(InterruptedException e) {

                }
            }
        }
    };

    class ReceiveThread extends Thread {
        DataInputStream input;
        private SurfaceHolder m_holder;
        private ViewActivity m_viewActivity;
        private Boolean m_run = false;


        public ReceiveThread(SurfaceHolder holder, ViewActivity viewActivity) {
            m_holder = holder;
            m_viewActivity = viewActivity;
            input = DeviceDetailFragment.in;
        }

        public void setRunning(boolean run) {
            m_run = run;
        }
        public void run() {
            Canvas canvas;
            try {
                BufferedInputStream bis = new BufferedInputStream(DeviceDetailFragment.client.getInputStream());
                ObjectInputStream ois = new ObjectInputStream(DeviceDetailFragment.client.getInputStream());
                while (input != null) {
                    byte[] imagebuffer = null;
                    int size = 0;
                    byte[] buffer = new byte[10240 * 20];
                    int read;
                    int temp_max = 0;
                    boolean isContinue = false;

                    while ((read = bis.read(buffer)) != -1 ) {

                        if (imagebuffer == null) {
                            //처음 4byte에서 비트맵이미지의 총크기를 추출해 따로 저장한다
                            byte[] sizebuffer = new byte[4];
                            System.arraycopy(buffer, 0, sizebuffer, 0, sizebuffer.length);
                            size = getInt(sizebuffer);


                            if(size == 20160215) {
                                Log.d(ACTIVITY_SERVICE, "20160215 p r e readUTF");

                                Bitmap store_bitmap = null;
                                String name = null;
                                try {
                                    SharePacketObject obj = (SharePacketObject)ois.readObject();
                                    byte[] image = obj.getByteArray();
                                    store_bitmap = BitmapFactory.decodeByteArray(image , 0 , image.length);
                                    name = obj.getPhotoName();
                                    Log.d(ACTIVITY_SERVICE, ""+store_bitmap.getWidth());
                                    Log.d(ACTIVITY_SERVICE, name);
                                } catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                }

                                new StorePhoto(store_bitmap, name).execute();
                                new SendTask("ShareSuccess").execute();
                                isContinue = true;
                                break;

                            }


                            if(size > 1000000 || size < 0) {
                                System.out.println("                                              size : " + size);
                                isContinue = true;
                                break;
                            }
                            read -= sizebuffer.length;

                            //나머지는 이미지버퍼 배열에 저장한다
                            imagebuffer = new byte[read];
                            System.arraycopy(buffer, sizebuffer.length, imagebuffer, 0, read);
                        } else {
                            //이미지버퍼 배열에 계속 이어서 저장한다
                            byte[] preimagebuffer = imagebuffer.clone();
                            imagebuffer = new byte[read + preimagebuffer.length];
                            System.arraycopy(preimagebuffer, 0, imagebuffer, 0, preimagebuffer.length);
                            System.arraycopy(buffer, 0, imagebuffer, imagebuffer.length - read, read);

                        }
                        temp_max += read;

                        System.out.println("max : " + size);
                        System.out.println("temp_max :  " + temp_max);
                        if (size == temp_max) {
                            break;
                        }
                        else if (size < temp_max) {
                            isContinue = true;
                            break;
                        }

                    }
                    if(isContinue == true) {
                        continue;
                    }

                    new SendTask("SendSuccess").execute();
                    Log.d(ACTIVITY_SERVICE, "sendSuccess!");
                    canvas = null;
                    try {
                        Bitmap image = BitmapFactory.decodeByteArray(imagebuffer, 0, imagebuffer.length);
                        temp_bitmap = image;
                        canvas = m_holder.lockCanvas(null);
                        synchronized (m_holder) {
                            m_viewActivity.onDraw(canvas, image);
                            //canvas.drawBitmap(image, 0, 0, null);
                        }
                    } finally {
                        if(canvas != null) {
                            m_holder.unlockCanvasAndPost(canvas);
                        }
                    }
                }
            } catch (IOException e) {
                /*moveTaskToBack(true);
                finish();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);*/
                e.printStackTrace();
            }

        }
    }

    private int getInt(byte[] data) {
        int s1 = data[0] & 0xFF;
        int s2 = data[1] & 0xFF;
        int s3 = data[2] & 0xFF;
        int s4 = data[3] & 0xFF;

        return ((s1 << 24) + (s2 << 16) + (s3 << 8) + (s4 << 0));
    }

    Bitmap temp_bitmap;

    class SendTask extends AsyncTask<Void, Void, String> {

        DataOutputStream output;
        String signal;

        public SendTask(String signal) {
            output = DeviceDetailFragment.out;
            this.signal = signal;
        }
        @Override
        protected String doInBackground(Void... voids) {
            // TODO Auto-generated method stub
            try {
                Log.d(ACTIVITY_SERVICE, "11111");

                if (output != null) {
                    output.writeUTF(signal);
                    Log.d(ACTIVITY_SERVICE, signal);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException npe) {
                npe.printStackTrace();
            }

            //오류시 null 반환
            return null;
        }
        //asyonTask 3번째 인자와 일치 매개변수값 -> doInBackground 리턴값이 전달됨
        //AsynoTask 는 preExcute - doInBackground - postExecute 순으로 자동으로 실행됩니다.
        //ui는 여기서 변경
        protected void onPostExecute(String value){
            super.onPostExecute(value);

        }

    }

    public class StorePhoto extends AsyncTask<Void, Void, String> {
        Bitmap bitmap;
        String string_path;
        String file_name;

        public StorePhoto(Bitmap bitmap, String file_name) {
            this.bitmap = bitmap;
            this.file_name = file_name;
            Log.d(ACTIVITY_SERVICE, ""+bitmap.getWidth());
        }

        @Override
        protected String doInBackground(Void... params) {

            String ex_storage = Environment.getExternalStorageDirectory().getAbsolutePath();
            // Get Absolute Path in External Sdcard
            String foler_name = "/AngleShot/";
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
            Toast.makeText(ViewActivity.this, "사진이 저장되었습니다.", Toast.LENGTH_SHORT).show();
        }
    }

}


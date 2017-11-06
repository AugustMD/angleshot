
package com.example.osko.angleshot;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.osko.angleshot.DeviceListFragment.DeviceActionListener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private static final int SOCKET_TIMEOUT = 5000;

    private View mContentView = null;
    private WifiP2pDevice device;
    static public WifiP2pInfo info;
    ProgressDialog progressDialog = null;
    static String host;
    private String memberIp;

    public OwnerServerAsyncTask ownerServerTask;
    public ClientAsyncTask clientTask;

    boolean isOwnerServer = false;
    boolean isClientServer = false;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.device_detail, null);
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.groupOwnerIntent = 15;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DffeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                );
                ((DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).disconnect();
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        if (info.groupFormed && info.isGroupOwner) {
                            if (isOwnerServer) {
                                new SocketConnectThread(info.groupOwnerAddress.getHostAddress()).start();
                                Intent intent = new Intent(getActivity(), CameraPreview.class);
                                startActivity(intent);
                                getActivity().finish();
                            }
                        } else {
                            if (isClientServer) {
                                host = info.groupOwnerAddress.getHostAddress();
                                new SocketConnectThread(memberIp).start();
                                Intent intent = new Intent(getActivity(), CameraPreview.class);
                                startActivity(intent);
                                getActivity().finish();
                            }
                        }
                    }
                });

        return mContentView;
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);

        // The owner IP is now known.
        //TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        //view.setText(getResources().getString(R.string.group_owner_text)
        //        + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
        //        : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        //view = (TextView) mContentView.findViewById(R.id.device_info);
        //view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());



        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            ownerServerTask = new OwnerServerAsyncTask(getActivity());
            ownerServerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            clientTask = new ClientAsyncTask(getActivity());
            clientTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        //    ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
        //            .getString(R.string.client_text));

        }

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);

        for(int i=0; i<10000000; i++) {}
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);

    }

    /**
     * Updates the UI with device data
     * 
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        //TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        //view.setText(device.deviceAddress);
        //view = (TextView) mContentView.findViewById(R.id.device_info);
        //view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        //TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        //view.setText(R.string.empty);
        //view = (TextView) mContentView.findViewById(R.id.device_info);
        //view.setText(R.string.empty);
        //view = (TextView) mContentView.findViewById(R.id.group_owner);
        //view.setText(R.string.empty);
        //view = (TextView) mContentView.findViewById(R.id.status_text);
        //view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.INVISIBLE);
    }









    static ServerSocket preServerSocket;
    static ServerSocket serverSocket;
    static Socket client;
    static DataInputStream in;
    static DataOutputStream out;
    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public class OwnerServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;

        public OwnerServerAsyncTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                preServerSocket = new ServerSocket(8989);
                client = preServerSocket.accept();
                host = new StringTokenizer(String.valueOf(client.getInetAddress()), "/").nextToken();
                DataOutputStream hostOutput = new DataOutputStream(client.getOutputStream());
                hostOutput.writeUTF(host);
                Log.d(getActivity().ACTIVITY_SERVICE, host);

                serverSocket = new ServerSocket(8988);
                Log.d(MainActivity.TAG, "Server: Socket opened");
                isOwnerServer = true;
                client = serverSocket.accept();
                if(client.getInetAddress().getHostAddress().equals(info.groupOwnerAddress.getHostAddress())) {
                    Log.d(getActivity().ACTIVITY_SERVICE, "EXIT");
                    return "exit";
                }
                Log.d(MainActivity.TAG, "Server: connection done");
                Log.d(getActivity().ACTIVITY_SERVICE, info.groupOwnerAddress.getHostAddress());
                Log.d(getActivity().ACTIVITY_SERVICE, client.getInetAddress().getHostAddress());

                in = new DataInputStream(client.getInputStream());
                out = new DataOutputStream(client.getOutputStream());

                return "view";
            } catch (IOException e) {
                Log.e(MainActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if(result == null) {
                Log.d(getActivity().ACTIVITY_SERVICE, "OwnerServer result null");
                //((DeviceActionListener) getActivity()).disconnect();
            }
            else if(result.equals("exit")) {
            }
            else if(result.equals("view")){
                Intent intent = new Intent(getActivity(), ViewActivity.class);
                startActivity(intent);
            }


        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            //statusText.setText("Opening a server socket");
        }

    }


    static ServerSocket temp_serverSocket;
    static Socket temp_client;
    static DataInputStream temp_in;
    static DataOutputStream temp_out;

    public class ClientAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;

        public ClientAsyncTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(Void... voids) {
            // TODO Auto-generated method stub
            try {
                String host = info.groupOwnerAddress.getHostAddress();
                Socket socket = new Socket();
                int port = 8988;

                try {
                    for(int i=0; i<10000000; i++) {}
                    Log.d(MainActivity.TAG, "Opening client socket - ");
                    while(!socket.isConnected()) {
                        try {
                            socket.bind(null);
                            socket.connect((new InetSocketAddress(host, 8989)), SOCKET_TIMEOUT);
                            Log.d(MainActivity.TAG, "Client socket - " + socket.isConnected());
                        } catch (ConnectException e) {
                            e.printStackTrace();
                            socket.connect((new InetSocketAddress(host, 8989)), SOCKET_TIMEOUT);
                        }
                    }
                    DataInputStream hostInput = new DataInputStream(socket.getInputStream());
                    memberIp = hostInput.readUTF();
                    Log.d(getActivity().ACTIVITY_SERVICE, memberIp);

                    temp_serverSocket = new ServerSocket(port);
                    Log.d(MainActivity.TAG, "Server: Socket opened");
                    isClientServer = true;
                    temp_client = temp_serverSocket.accept();
                    Log.d(getActivity().ACTIVITY_SERVICE, memberIp);
                    Log.d(getActivity().ACTIVITY_SERVICE, temp_client.getInetAddress().getHostAddress());
                    if(temp_client.getInetAddress().getHostAddress().equals(memberIp)) {
                        Log.d(getActivity().ACTIVITY_SERVICE, "EXIT");
                        return "exit";
                    }
                    Log.d(MainActivity.TAG, "Server: connection done");

                    temp_in = new DataInputStream(temp_client.getInputStream());
                    temp_out = new DataOutputStream(temp_client.getOutputStream());


                    if(socket.isConnected()) {
                        socket.close();
                    }

                    //결과창뿌려주기 - ui 변경시 에러
                    return "view";


                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(MainActivity.TAG, e.getMessage());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            //오류시 null 반환
            return null;
        }
        //asyonTask 3번째 인자와 일치 매개변수값 -> doInBackground 리턴값이 전달됨
        //AsynoTask 는 preExcute - doInBackground - postExecute 순으로 자동으로 실행됩니다.
        //ui는 여기서 변경
        protected void onPostExecute(String result){
            super.onPostExecute(result);
            if(result == null) {
                Log.d(getActivity().ACTIVITY_SERVICE, "Client result null");
                //((DeviceActionListener) getActivity()).disconnect();
            }
            else if(result.equals("exit")) {
            }
            else if(result.equals("view")){
                serverSocket = temp_serverSocket;
                client = temp_client;
                in = temp_in;
                out = temp_out;

                Intent intent = new Intent(getActivity(), ViewActivity.class);
                startActivity(intent);
            }
        }

    }

    class SocketConnectThread extends Thread {
        String host;
        int port = 8988;

        SocketConnectThread(String ip) {
            host = ip;
        }

        public void run() {
            try {

                Socket socket = new Socket();

                try {
                    socket.bind(null);
                    socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                } catch (IOException e) {
                    Log.e(MainActivity.TAG, e.getMessage());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}

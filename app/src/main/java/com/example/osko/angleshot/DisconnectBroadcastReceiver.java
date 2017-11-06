/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.osko.angleshot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
public class DisconnectBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager manager;
    private Channel channel;
    private CameraPreview activity;

    private ViewActivity activity2;

    /**
     * @param manager WifiP2pManager system service
     * @param channel Wifi p2p channel
     * @param activity activity associated with the receiver
     */
    public DisconnectBroadcastReceiver(WifiP2pManager manager, Channel channel,
                                       CameraPreview activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
        this.activity2 = null;
    }

    public DisconnectBroadcastReceiver(WifiP2pManager manager, Channel channel,
                                       ViewActivity activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = null;
        this.activity2 = activity;
    }

    /*
     * (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (!networkInfo.isConnected()) {
                if(activity != null) {
                    activity.moveTaskToBack(true);
                    activity.finish();
                }
                else {
                    activity2.moveTaskToBack(true);
                    activity2.finish();
                }
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }

        }
    }
}

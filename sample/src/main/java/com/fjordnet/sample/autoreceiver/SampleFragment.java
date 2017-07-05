/*
 * Copyright 2017 FJORD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fjordnet.sample.autoreceiver;


import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.fjordnet.autoreceiver.annotations.OnReceiveBroadcast;


public class SampleFragment extends HeadsetStateFragment {

    private static final String EXTRA_AIRPLANE_MODE = "state";
    private static final String EXTRA_HEADSET_STATE = "state";
    private static final int STATE_UNPLUGGED = 0;
    private static final int STATE_PLUGGED = 1;

    private TextView connectionState;

    public SampleFragment() {
        // Required empty public constructor
    }

    public static SampleFragment newInstance() {
        return new SampleFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_sample, container, false);
        connectionState = (TextView) view.findViewById(R.id.connection_state);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshConnectionState();
    }

    @Override
    protected void onHeadsetStateChanged(Intent intent) {
        Toast.makeText(getContext(),
                STATE_PLUGGED == intent.getIntExtra(EXTRA_HEADSET_STATE, STATE_UNPLUGGED)
                        ? R.string.toast_headset_plugged
                        : R.string.toast_headset_unplugged,
                Toast.LENGTH_SHORT).show();
    }

    protected void refreshConnectionState() {

        NetworkInfo info = ((ConnectivityManager) getContext().getSystemService(
                Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

        @StringRes int state = null != info && info.isConnectedOrConnecting()
                ? R.string.state_connected
                : R.string.state_disconnected;
        connectionState.setText(state);
    }

    @OnReceiveBroadcast({
            ConnectivityManager.CONNECTIVITY_ACTION,
            Intent.ACTION_AIRPLANE_MODE_CHANGED})
    protected void onNetworkStateChanged(Intent intent) {

        if (intent.getBooleanExtra(EXTRA_AIRPLANE_MODE, false)) {
            connectionState.setText(R.string.state_airplane_mode);
            return;
        }

        refreshConnectionState();
    }
}

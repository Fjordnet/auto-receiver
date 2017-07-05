package com.fjordnet.sample.autoreceiver;

import android.content.Intent;
import android.media.AudioManager;
import android.support.v4.app.Fragment;

import com.fjordnet.autoreceiver.annotations.OnReceiveBroadcast;

/**
 * Fragment that listens to headset state via
 * {@link android.media.AudioManager#ACTION_HEADSET_PLUG}.
 */
public abstract class HeadsetStateFragment extends Fragment {

    @OnReceiveBroadcast(AudioManager.ACTION_HEADSET_PLUG)
    protected abstract void onHeadsetStateChanged(Intent intent);
}

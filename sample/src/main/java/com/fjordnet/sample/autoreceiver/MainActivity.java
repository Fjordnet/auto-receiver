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

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.fjordnet.autoreceiver.annotations.OnReceiveBroadcast;

import java.text.DateFormat;

import static android.content.Intent.ACTION_BATTERY_CHANGED;
import static android.content.Intent.ACTION_POWER_CONNECTED;
import static android.content.Intent.ACTION_POWER_DISCONNECTED;
import static android.content.Intent.ACTION_TIME_TICK;
import static android.widget.Toast.LENGTH_SHORT;

public class MainActivity extends AppCompatActivity {

    private static final int MENU_FRAGMENT = Menu.FIRST;

    private DateFormat timeFormat;
    private TextView timeView;
    private TextView powerView;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_FRAGMENT, Menu.NONE, getString(R.string.menu_fragment));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MENU_FRAGMENT != item.getItemId()) {
            return super.onOptionsItemSelected(item);
        }

        startActivity(new Intent(this, SampleFragmentActivity.class));
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timeView = (TextView) findViewById(R.id.currentTime);
        powerView = (TextView) findViewById(R.id.powerStatus);

        findViewById(R.id.broadcast).setOnClickListener(
                view -> sendBroadcast(new Intent(AppConstants.BROADCAST_ACTION_AUTO_RECEIVER)));

        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deinit();
    }

    @Override
    protected void onStart() {
        super.onStart();

        timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        updateTimeView();

        updatePowerState(registerReceiver(null, new IntentFilter(ACTION_BATTERY_CHANGED)));
    }

    @OnReceiveBroadcast(value = ACTION_TIME_TICK, registerIn = "onResume", unregisterIn = "onPause")
    public void onTimeTick() {
        updateTimeView();
    }

    @OnReceiveBroadcast({ACTION_POWER_CONNECTED, ACTION_POWER_DISCONNECTED})
    public void onPowerStateChanged(Intent intent, BroadcastReceiver receiver) {
        updatePowerState(intent);
    }

    @OnReceiveBroadcast(value = AppConstants.BROADCAST_ACTION_AUTO_RECEIVER,
            registerIn = "init",
            unregisterIn = "deinit")
    protected void onCustomBroadcast(Intent intent) {
        Toast.makeText(this, R.string.toast_custom_broadcast, LENGTH_SHORT).show();
    }

    @OnReceiveBroadcast(Intent.ACTION_BATTERY_LOW)
    protected void onLowBattery(Intent intent) {
        Toast.makeText(this, "Low Battery!", Toast.LENGTH_SHORT).show();
    }

    private void init() {
    }

    private void deinit() {
    }

    private void updateTimeView() {
        timeView.setText(timeFormat.format(System.currentTimeMillis()));
    }

    private void updatePowerState(Intent intent) {

        boolean isCharging;

        String action = intent.getAction();
        if (ACTION_POWER_CONNECTED.equals(action)) {
            isCharging = true;
        } else if (ACTION_POWER_DISCONNECTED.equals(action)) {
            isCharging = false;
        } else {
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        powerView.setText(isCharging ? R.string.charging : R.string.discharging);
    }
}

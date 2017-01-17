/*
 * Copyright (C) 2013 Slimroms
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

package com.android.settings;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.android.internal.hardware.AmbientDisplayConfiguration;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.preferences.CustomSeekBarPreference;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class DozeSettingsFragment extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String KEY_DOZE_TIMEOUT = "doze_timeout";
    private static final String KEY_DOZE_WAKEUP_DOUBLETAP = "doze_wakeup_doubletap";
    private static final String KEY_DOZE_TRIGGER_PICKUP = "doze_trigger_pickup";
    private static final String KEY_DOZE_TRIGGER_SIGMOTION = "doze_trigger_sigmotion";
    private static final String KEY_DOZE_TRIGGER_NOTIFICATION = "doze_trigger_notification";
    private static final String KEY_DOZE_TRIGGER_DOUBLETAP = "doze_trigger_doubletap";
    private static final String KEY_DOZE_BRIGHTNESS = "doze_brightness";

    private static final String SYSTEMUI_METADATA_NAME = "com.android.systemui";

    private CustomSeekBarPreference mDozeTimeout;
    private SwitchPreference mDozeWakeupDoubleTap;
    private SwitchPreference mDozeTriggerPickup;
    private SwitchPreference mDozeTriggerSigmotion;
    private SwitchPreference mDozeTriggerNotification;
    private SwitchPreference mDozeTriggerDoubleTap;
    private CustomSeekBarPreference mDozeBrightness;

    private AmbientDisplayConfiguration mConfig;

    private float mBrightnessScale;
    private float mDefaultBrightnessScale;

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.DISPLAY;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Activity activity = getActivity();
        mConfig = new AmbientDisplayConfiguration(activity);

        PreferenceScreen prefSet = getPreferenceScreen();
        Resources res = getResources();

        addPreferencesFromResource(R.xml.doze_settings);

        // Doze timeout seekbar
        mDozeTimeout = (CustomSeekBarPreference) findPreference(KEY_DOZE_TIMEOUT);
        mDozeTimeout.setOnPreferenceChangeListener(this);

        // Double-tap to wake from doze
        mDozeWakeupDoubleTap = (SwitchPreference) findPreference(KEY_DOZE_WAKEUP_DOUBLETAP);
        mDozeWakeupDoubleTap.setOnPreferenceChangeListener(this);

        // Doze triggers
        if (isPickupSensorUsedByDefault(mConfig)) {
            mDozeTriggerPickup = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_PICKUP);
            mDozeTriggerPickup.setOnPreferenceChangeListener(this);
        } else {
            removePreference(KEY_DOZE_TRIGGER_PICKUP);
        }
        if (isSigmotionSensorUsedByDefault(activity)) {
            mDozeTriggerSigmotion = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_SIGMOTION);
            mDozeTriggerSigmotion.setOnPreferenceChangeListener(this);
        } else {
            removePreference(KEY_DOZE_TRIGGER_SIGMOTION);
        }
        if (isDoubleTapSensorUsedByDefault(mConfig) || isTapToWakeAvailable(res)) {
            mDozeTriggerDoubleTap = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_DOUBLETAP);
            mDozeTriggerDoubleTap.setOnPreferenceChangeListener(this);
            if (!isTapToWakeEnabled() && !isDoubleTapSensorUsedByDefault(mConfig)) {
                mDozeTriggerDoubleTap.setEnabled(false);
            }
        } else {
            removePreference(KEY_DOZE_TRIGGER_DOUBLETAP);
        }
        mDozeTriggerNotification = (SwitchPreference) findPreference(KEY_DOZE_TRIGGER_NOTIFICATION);
        mDozeTriggerNotification.setOnPreferenceChangeListener(this);

        // Doze brightness
        mDefaultBrightnessScale =
                (float) res.getInteger(
                com.android.internal.R.integer.config_screenBrightnessDoze) / res.getInteger(
                com.android.internal.R.integer.config_screenBrightnessSettingMaximum);
        mDozeBrightness = (CustomSeekBarPreference) findPreference(KEY_DOZE_BRIGHTNESS);
        mDozeBrightness.setOnPreferenceChangeListener(this);

        setHasOptionsMenu(false);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mDozeTimeout) {
            int dozeTimeout = (Integer) newValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.DOZE_TIMEOUT, dozeTimeout);
        }
        if (preference == mDozeWakeupDoubleTap) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.DOUBLE_TAP_WAKE_DOZE, value ? 1 : 0);
        }
        if (preference == mDozeTriggerPickup) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.DOZE_TRIGGER_PICKUP, value ? 1 : 0);
        }
        if (preference == mDozeTriggerSigmotion) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.DOZE_TRIGGER_SIGMOTION, value ? 1 : 0);
        }
        if (preference == mDozeTriggerDoubleTap) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.DOZE_TRIGGER_DOUBLETAP, value ? 1 : 0);
        }
        if (preference == mDozeTriggerNotification) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.DOZE_TRIGGER_NOTIFICATION, value ? 1 : 0);
        }
        if (preference == mDozeBrightness) {
            float valNav = (float) ((Integer) newValue);
            Settings.System.putFloat(getContentResolver(),
                    Settings.System.DOZE_BRIGHTNESS, valNav / 100);
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();
    }

    private void updateState() {
        final Activity activity = getActivity();

        // Update doze preferences
        if (mDozeTimeout != null) {
            final int statusDozeTimeout = Settings.System.getInt(getContentResolver(),
                    Settings.System.DOZE_TIMEOUT, dozeTimeoutDefault(activity));
            mDozeTimeout.setValue(statusDozeTimeout);
        }
        if (mDozeWakeupDoubleTap != null) {
            int value = Settings.System.getInt(getContentResolver(),
                    Settings.System.DOUBLE_TAP_WAKE_DOZE, 0);
            mDozeWakeupDoubleTap.setChecked(value != 0);
        }
        if (mDozeTriggerPickup != null) {
            int value = Settings.System.getInt(getContentResolver(),
                    Settings.System.DOZE_TRIGGER_PICKUP, 1);
            mDozeTriggerPickup.setChecked(value != 0);
        }
        if (mDozeTriggerSigmotion != null) {
            int value = Settings.System.getInt(getContentResolver(),
                    Settings.System.DOZE_TRIGGER_SIGMOTION, 1);
            mDozeTriggerSigmotion.setChecked(value != 0);
        }
        if (mDozeTriggerDoubleTap != null) {
            int value = Settings.System.getInt(getContentResolver(),
                    Settings.System.DOZE_TRIGGER_DOUBLETAP, 0);
            mDozeTriggerDoubleTap.setChecked(value != 0);
        }
        if (mDozeTriggerNotification != null) {
            int value = Settings.System.getInt(getContentResolver(),
                    Settings.System.DOZE_TRIGGER_NOTIFICATION, 1);
            mDozeTriggerNotification.setChecked(value != 0);
        }
        if (mDozeBrightness != null) {
            mBrightnessScale = Settings.System.getFloat(getContentResolver(),
                    Settings.System.DOZE_BRIGHTNESS, mDefaultBrightnessScale);
            mDozeBrightness.setValue((int) (mBrightnessScale * 100));
        }
    }

    private boolean isTapToWakeEnabled() {
        return Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.DOUBLE_TAP_TO_WAKE, 0) == 1;
    }

    private static boolean isTapToWakeAvailable(Resources res) {
        return res.getBoolean(com.android.internal.R.bool.config_supportDoubleTapWake);
    }

    private static boolean isPickupSensorUsedByDefault(AmbientDisplayConfiguration config) {
        return config.pulseOnPickupAvailable();
    }

    private static boolean isSigmotionSensorUsedByDefault(Context context) {
        return getConfigBoolean(context, "doze_pulse_on_significant_motion");
    }

    private static boolean isDoubleTapSensorUsedByDefault(AmbientDisplayConfiguration config) {
        return config.pulseOnDoubleTapAvailable();
    }

    private static int dozeTimeoutDefault(Context context) {
        return getConfigInteger(context, "doze_pulse_duration_visible");
    }

    private static Boolean getConfigBoolean(Context context, String configBooleanName) {
        int resId = -1;
        Boolean b = true;
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return null;
        }

        Resources systemUiResources;
        try {
            systemUiResources = pm.getResourcesForApplication(SYSTEMUI_METADATA_NAME);
        } catch (Exception e) {
            Log.e("DozeSettings:", "can't access systemui resources",e);
            return null;
        }

        resId = systemUiResources.getIdentifier(
            SYSTEMUI_METADATA_NAME + ":bool/" + configBooleanName, null, null);
        if (resId > 0) {
            b = systemUiResources.getBoolean(resId);
        }
        return b;
    }

    private static Integer getConfigInteger(Context context, String configIntegerName) {
        int resId = -1;
        Integer i = 1;
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return null;
        }

        Resources systemUiResources;
        try {
            systemUiResources = pm.getResourcesForApplication(SYSTEMUI_METADATA_NAME);
        } catch (Exception e) {
            Log.e("DozeSettings:", "can't access systemui resources",e);
            return null;
        }

        resId = systemUiResources.getIdentifier(
            SYSTEMUI_METADATA_NAME + ":integer/" + configIntegerName, null, null);
        if (resId > 0) {
            i = systemUiResources.getInteger(resId);
        }
        return i;
    }
}

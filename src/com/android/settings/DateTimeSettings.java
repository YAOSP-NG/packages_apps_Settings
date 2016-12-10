/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.AlertDialog;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.text.format.DateFormat;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TimePicker;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.dashboard.SummaryLoader;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedSwitchPreference;
import com.android.settingslib.datetime.ZoneGetter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

public class DateTimeSettings extends SettingsPreferenceFragment
        implements OnTimeSetListener, OnDateSetListener, OnPreferenceChangeListener, Indexable {

    private static final String HOURS_12 = "12";
    private static final String HOURS_24 = "24";

    // Used for showing the current date format, which looks like "12/31/2010", "2010/12/13", etc.
    // The date value is dummy (independent of actual date).
    private Calendar mDummyDate;

    private static final String KEY_AUTO_TIME           = "auto_time";
    private static final String KEY_AUTO_TIME_ZONE      = "auto_zone";
    private static final String KEY_CLOCK_AM_PM_STYLE   = "clock_am_pm_style";
    private static final String KEY_CLOCK_STYLE         = "clock_style";
    private static final String KEY_CLOCK_DATE_SHOW     = "clock_date_show";
    private static final String KEY_CLOCK_DATE_STYLE    = "clock_date_style";
    private static final String KEY_CLOCK_DATE_POSITION = "clock_date_position";
    private static final String KEY_CLOCK_DATE_FORMAT   = "clock_date_format";

    private static final int DIALOG_DATEPICKER = 0;
    private static final int DIALOG_TIMEPICKER = 1;

    private static final int CLOCK_STYLE_RIGHT_CLOCK        = 0;
    private static final int CLOCK_STYLE_CENTER_CLOCK       = 1;
    private static final int CLOCK_STYLE_LEFT_CLOCK         = 2;

    private static final int CLOCK_AM_PM_STYLE_NONE         = 0;
    private static final int CLOCK_AM_PM_STYLE_SMALL        = 1;
    private static final int CLOCK_AM_PM_STYLE_NORMAL       = 2;

    private static final int CLOCK_DATE_SHOW_NONE           = 0;
    private static final int CLOCK_DATE_SHOW_SMALL          = 1;
    private static final int CLOCK_DATE_SHOW_NORMAL         = 2;

    private static final int CLOCK_DATE_STYLE_NORMAL        = 0;
    private static final int CLOCK_DATE_STYLE_LOWERCASE     = 1;
    private static final int CLOCK_DATE_STYLE_UPPERCASE     = 2;

    private static final int CLOCK_DATE_POSITION_LEFT       = 0;
    private static final int CLOCK_DATE_POSITION_RIGHT      = 1;

    private static final int CLOCK_DATE_FORMAT_CUSTOM_INDEX = 18;

    // have we been launched from the setup wizard?
    protected static final String EXTRA_IS_FIRST_RUN = "firstRun";

    // Minimum time is Nov 5, 2007, 0:00.
    private static final long MIN_DATE = 1194220800000L;

    private RestrictedSwitchPreference mAutoTimePref;
    private Preference mTimePref;
    private Preference mTime24Pref;
    private SwitchPreference mAutoTimeZonePref;
    private Preference mTimeZone;
    private Preference mDatePref;
    private ListPreference mClockAmPmStyle;
    private ListPreference mClockStyle;
    private ListPreference mClockDateShow;
    private ListPreference mClockDateStyle;
    private ListPreference mClockDatePosition;
    private ListPreference mClockDateFormat;

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.DATE_TIME;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.date_time_prefs);

        initUI();
    }

    private void initUI() {
        boolean autoTimeEnabled = getAutoState(Settings.Global.AUTO_TIME);
        boolean autoTimeZoneEnabled = getAutoState(Settings.Global.AUTO_TIME_ZONE);

        mAutoTimePref = (RestrictedSwitchPreference) findPreference(KEY_AUTO_TIME);
        mAutoTimePref.setOnPreferenceChangeListener(this);
        EnforcedAdmin admin = RestrictedLockUtils.checkIfAutoTimeRequired(getActivity());
        mAutoTimePref.setDisabledByAdmin(admin);

        Intent intent = getActivity().getIntent();
        boolean isFirstRun = intent.getBooleanExtra(EXTRA_IS_FIRST_RUN, false);

        mDummyDate = Calendar.getInstance();

        // If device admin requires auto time device policy manager will set
        // Settings.Global.AUTO_TIME to true. Note that this app listens to that change.
        mAutoTimePref.setChecked(autoTimeEnabled);
        mAutoTimeZonePref = (SwitchPreference) findPreference(KEY_AUTO_TIME_ZONE);
        mAutoTimeZonePref.setOnPreferenceChangeListener(this);
        // Override auto-timezone if it's a wifi-only device or if we're still in setup wizard.
        // TODO: Remove the wifiOnly test when auto-timezone is implemented based on wifi-location.
        if (Utils.isWifiOnly(getActivity()) || isFirstRun) {
            getPreferenceScreen().removePreference(mAutoTimeZonePref);
            autoTimeZoneEnabled = false;
        }
        mAutoTimeZonePref.setChecked(autoTimeZoneEnabled);

        mTimePref = findPreference("time");
        mTime24Pref = findPreference("24 hour");
        mTimeZone = findPreference("timezone");
        mDatePref = findPreference("date");
        if (isFirstRun) {
            getPreferenceScreen().removePreference(mTime24Pref);
        }

        mTimePref.setEnabled(!autoTimeEnabled);
        mDatePref.setEnabled(!autoTimeEnabled);
        mTimeZone.setEnabled(!autoTimeZoneEnabled);

        mClockAmPmStyle = (ListPreference) findPreference(KEY_CLOCK_AM_PM_STYLE);
        mClockAmPmStyle.setOnPreferenceChangeListener(this);
        mClockAmPmStyle.setValue(Integer.toString(Settings.Secure.getInt(getActivity()
                .getContentResolver(), Settings.Secure.CLOCK_AM_PM_STYLE,
                CLOCK_AM_PM_STYLE_NONE)));
        mClockAmPmStyle.setSummary(mClockAmPmStyle.getEntry());
        mClockAmPmStyle.setEnabled(!is24Hour());

        mClockStyle = (ListPreference) findPreference(KEY_CLOCK_STYLE);
        mClockStyle.setOnPreferenceChangeListener(this);
        mClockStyle.setValue(Integer.toString(Settings.Secure.getInt(getActivity()
                .getContentResolver(), Settings.Secure.CLOCK_STYLE,
                CLOCK_STYLE_RIGHT_CLOCK)));
        mClockStyle.setSummary(mClockStyle.getEntry());

        mClockDateShow = (ListPreference) findPreference(KEY_CLOCK_DATE_SHOW);
        mClockDateShow.setOnPreferenceChangeListener(this);
        mClockDateShow.setValue(Integer.toString(Settings.Secure.getInt(getActivity()
                .getContentResolver(), Settings.Secure.CLOCK_DATE_SHOW,
                CLOCK_DATE_SHOW_NONE)));
        mClockDateShow.setSummary(mClockDateShow.getEntry());
        mClockDateStyle = (ListPreference) findPreference(KEY_CLOCK_DATE_STYLE);
        mClockDateStyle.setOnPreferenceChangeListener(this);
        mClockDateStyle.setValue(Integer.toString(Settings.Secure.getInt(getActivity()
                .getContentResolver(), Settings.Secure.CLOCK_DATE_STYLE,
                CLOCK_DATE_STYLE_NORMAL)));
        mClockDateStyle.setSummary(mClockDateStyle.getEntry());

        mClockDatePosition = (ListPreference) findPreference(KEY_CLOCK_DATE_POSITION);
        mClockDatePosition.setOnPreferenceChangeListener(this);
        mClockDatePosition.setValue(Integer.toString(Settings.Secure.getInt(getActivity()
                .getContentResolver(), Settings.Secure.CLOCK_DATE_POSITION,
                CLOCK_DATE_POSITION_LEFT)));
        mClockDatePosition.setSummary(mClockDatePosition.getEntry());

        mClockDateFormat = (ListPreference) findPreference(KEY_CLOCK_DATE_FORMAT);
        mClockDateFormat.setOnPreferenceChangeListener(this);
        String clockDateFormat = Settings.Secure.getString(getActivity().getContentResolver(),
            Settings.Secure.CLOCK_DATE_FORMAT);
        if (clockDateFormat == null)
                clockDateFormat = "EEE";
        parseClockDateFormats();
        int index = mClockDateFormat.findIndexOfValue(clockDateFormat);
        mClockDateFormat.setValue((index < 0)
                ? getString(R.string.clock_date_format_custom)
                : clockDateFormat);
        mClockDateFormat.setSummary((index < 0)
                ? mClockDateFormat.getEntries()[CLOCK_DATE_FORMAT_CUSTOM_INDEX]
                : mClockDateFormat.getEntries()[index]);

        if (mClockDateShow.getValue() == Integer.toString(CLOCK_DATE_SHOW_NONE)) {
            mClockDateStyle.setEnabled(false);
            mClockDatePosition.setEnabled(false);
            mClockDateFormat.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        ((SwitchPreference)mTime24Pref).setChecked(is24Hour());

        // Register for time ticks and other reasons for time change
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getActivity().registerReceiver(mIntentReceiver, filter, null, null);

        updateTimeAndDateDisplay(getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mIntentReceiver);
    }

    public void updateTimeAndDateDisplay(Context context) {
        final Calendar now = Calendar.getInstance();
        mDummyDate.setTimeZone(now.getTimeZone());
        // We use December 31st because it's unambiguous when demonstrating the date format.
        // We use 13:00 so we can demonstrate the 12/24 hour options.
        mDummyDate.set(now.get(Calendar.YEAR), 11, 31, 13, 0, 0);
        Date dummyDate = mDummyDate.getTime();
        mDatePref.setSummary(DateFormat.getLongDateFormat(context).format(now.getTime()));
        mTimePref.setSummary(DateFormat.getTimeFormat(getActivity()).format(now.getTime()));
        mTimeZone.setSummary(ZoneGetter.getTimeZoneOffsetAndName(now.getTimeZone(), now.getTime()));
        mTime24Pref.setSummary(DateFormat.getTimeFormat(getActivity()).format(dummyDate));
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int day) {
        final Activity activity = getActivity();
        if (activity != null) {
            setDate(activity, year, month, day);
            updateTimeAndDateDisplay(activity);
        }
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        final Activity activity = getActivity();
        if (activity != null) {
            setTime(activity, hourOfDay, minute);
            updateTimeAndDateDisplay(activity);
        }

        // We don't need to call timeUpdated() here because the TIME_CHANGED
        // broadcast is sent by the AlarmManager as a side effect of setting the
        // SystemClock time.
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        AlertDialog dialog;

        if (preference.getKey().equals(KEY_AUTO_TIME)) {
            boolean autoEnabled = (Boolean) newValue;
            Settings.Global.putInt(getContentResolver(), Settings.Global.AUTO_TIME,
                    autoEnabled ? 1 : 0);
            mTimePref.setEnabled(!autoEnabled);
            mDatePref.setEnabled(!autoEnabled);

        } else if (preference.getKey().equals(KEY_AUTO_TIME_ZONE)) {
            boolean autoZoneEnabled = (Boolean) newValue;
            Settings.Global.putInt(
                    getContentResolver(), Settings.Global.AUTO_TIME_ZONE, autoZoneEnabled ? 1 : 0);
            mTimeZone.setEnabled(!autoZoneEnabled);

        } else if (preference == mClockAmPmStyle) {
            int val = Integer.parseInt((String) newValue);
            int index = mClockAmPmStyle.findIndexOfValue((String) newValue);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.CLOCK_AM_PM_STYLE, val);
            mClockAmPmStyle.setSummary(mClockAmPmStyle.getEntries()[index]);

        } else if (preference == mClockStyle) {
            int val = Integer.parseInt((String) newValue);
            int index = mClockStyle.findIndexOfValue((String) newValue);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.CLOCK_STYLE, val);
            mClockStyle.setSummary(mClockStyle.getEntries()[index]);

        } else if (preference == mClockDateShow) {
            int val = Integer.parseInt((String) newValue);
            int index = mClockDateShow.findIndexOfValue((String) newValue);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.CLOCK_DATE_SHOW, val);
            mClockDateShow.setSummary(mClockDateShow.getEntries()[index]);
            if (val == CLOCK_DATE_SHOW_NONE) {
                mClockDateStyle.setEnabled(false);
                mClockDatePosition.setEnabled(false);
                mClockDateFormat.setEnabled(false);
            } else {
                mClockDateStyle.setEnabled(true);
                mClockDatePosition.setEnabled(true);
                mClockDateFormat.setEnabled(true);
            }

        } else if (preference == mClockDateStyle) {
            int val = Integer.parseInt((String) newValue);
            int index = mClockDateStyle.findIndexOfValue((String) newValue);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.CLOCK_DATE_STYLE, val);
            mClockDateStyle.setSummary(mClockDateStyle.getEntries()[index]);

        } else if (preference == mClockDatePosition) {
            int val = Integer.parseInt((String) newValue);
            int index = mClockDatePosition.findIndexOfValue((String) newValue);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.CLOCK_DATE_POSITION, val);
            mClockDatePosition.setSummary(mClockDatePosition.getEntries()[index]);

        } else if (preference == mClockDateFormat) {

            int index = mClockDateFormat.findIndexOfValue((String) newValue);

            if (index == CLOCK_DATE_FORMAT_CUSTOM_INDEX) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.clock_date_format_edittext_title);
                alert.setMessage(R.string.clock_date_format_edittext_summary);

                final EditText input = new EditText(getActivity());
                String oldText = Settings.Secure.getString(
                    getActivity().getContentResolver(),
                    Settings.Secure.CLOCK_DATE_FORMAT);
                if (oldText != null) {
                    input.setText(oldText);
                }
                alert.setView(input);

                alert.setPositiveButton(R.string.menu_save, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int whichButton) {
                        String value = input.getText().toString();
                        if (value.equals("")) {
                            return;
                        }
                        Settings.Secure.putString(getActivity().getContentResolver(),
                            Settings.Secure.CLOCK_DATE_FORMAT, value);

                        mClockDateFormat.setSummary(
                                mClockDateFormat.getEntries()[CLOCK_DATE_FORMAT_CUSTOM_INDEX]);

                        return;
                    }
                });

                alert.setNegativeButton(R.string.menu_cancel,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int which) {
                        return;
                    }
                });
                dialog = alert.create();
                dialog.show();
            } else {
                if ((String) newValue != null) {
                    Settings.Secure.putString(getActivity().getContentResolver(),
                        Settings.Secure.CLOCK_DATE_FORMAT, (String) newValue);
                        mClockDateFormat.setSummary(mClockDateFormat.getEntries()[index]);
                        return true;
                }
            }

        }

        parseClockDateFormats();
        int index = mClockDateFormat.findIndexOfValue(mClockDateFormat.getValue());
        mClockDateFormat.setSummary((index < 0)
                ? mClockDateFormat.getEntries()[CLOCK_DATE_FORMAT_CUSTOM_INDEX]
                : mClockDateFormat.getEntries()[index]);

        return true;
    }

    @Override
    public Dialog onCreateDialog(int id) {
        final Calendar calendar = Calendar.getInstance();
        switch (id) {
        case DIALOG_DATEPICKER:
            DatePickerDialog d = new DatePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            configureDatePicker(d.getDatePicker());
            return d;
        case DIALOG_TIMEPICKER:
            return new TimePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(getActivity()));
        default:
            throw new IllegalArgumentException();
        }
    }

    static void configureDatePicker(DatePicker datePicker) {
        // The system clock can't represent dates outside this range.
        Calendar t = Calendar.getInstance();
        t.clear();
        t.set(1970, Calendar.JANUARY, 1);
        datePicker.setMinDate(t.getTimeInMillis());
        t.clear();
        t.set(2037, Calendar.DECEMBER, 31);
        datePicker.setMaxDate(t.getTimeInMillis());
    }

    /*
    @Override
    public void onPrepareDialog(int id, Dialog d) {
        switch (id) {
        case DIALOG_DATEPICKER: {
            DatePickerDialog datePicker = (DatePickerDialog)d;
            final Calendar calendar = Calendar.getInstance();
            datePicker.updateDate(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            break;
        }
        case DIALOG_TIMEPICKER: {
            TimePickerDialog timePicker = (TimePickerDialog)d;
            final Calendar calendar = Calendar.getInstance();
            timePicker.updateTime(
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE));
            break;
        }
        default:
            break;
        }
    }
    */
    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mDatePref) {
            showDialog(DIALOG_DATEPICKER);
        } else if (preference == mTimePref) {
            // The 24-hour mode may have changed, so recreate the dialog
            removeDialog(DIALOG_TIMEPICKER);
            showDialog(DIALOG_TIMEPICKER);
        } else if (preference == mTime24Pref) {
            final boolean is24Hour = ((SwitchPreference)mTime24Pref).isChecked();
            set24Hour(is24Hour);
            updateTimeAndDateDisplay(getActivity());
            timeUpdated(is24Hour);
            mClockAmPmStyle.setEnabled(!is24Hour());
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        updateTimeAndDateDisplay(getActivity());
    }

    private void timeUpdated(boolean is24Hour) {
        Intent timeChanged = new Intent(Intent.ACTION_TIME_CHANGED);
        timeChanged.putExtra(Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT, is24Hour);
        getActivity().sendBroadcast(timeChanged);
    }

    /*  Get & Set values from the system settings  */

    private boolean is24Hour() {
        return DateFormat.is24HourFormat(getActivity());
    }

    private void set24Hour(boolean is24Hour) {
        Settings.System.putString(getContentResolver(),
                Settings.System.TIME_12_24,
                is24Hour? HOURS_24 : HOURS_12);
    }

    private boolean getAutoState(String name) {
        try {
            return Settings.Global.getInt(getContentResolver(), name) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    /* package */ static void setDate(Context context, int year, int month, int day) {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        c.set(Calendar.DAY_OF_MONTH, day);
        long when = Math.max(c.getTimeInMillis(), MIN_DATE);

        if (when / 1000 < Integer.MAX_VALUE) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    /* package */ static void setTime(Context context, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.HOUR_OF_DAY, hourOfDay);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long when = Math.max(c.getTimeInMillis(), MIN_DATE);

        if (when / 1000 < Integer.MAX_VALUE) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Activity activity = getActivity();
            if (activity != null) {
                updateTimeAndDateDisplay(activity);
            }
        }
    };

    private static class SummaryProvider implements SummaryLoader.SummaryProvider {

        private final Context mContext;
        private final SummaryLoader mSummaryLoader;

        public SummaryProvider(Context context, SummaryLoader summaryLoader) {
            mContext = context;
            mSummaryLoader = summaryLoader;
        }

        @Override
        public void setListening(boolean listening) {
            if (listening) {
                final Calendar now = Calendar.getInstance();
                mSummaryLoader.setSummary(this, ZoneGetter.getTimeZoneOffsetAndName(
                        now.getTimeZone(), now.getTime()));
            }
        }
    }

    public static final SummaryLoader.SummaryProviderFactory SUMMARY_PROVIDER_FACTORY
            = new SummaryLoader.SummaryProviderFactory() {
        @Override
        public SummaryLoader.SummaryProvider createSummaryProvider(Activity activity,
                                                                   SummaryLoader summaryLoader) {
            return new SummaryProvider(activity, summaryLoader);
        }
    };

    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new DateTimeSearchIndexProvider();

    private static class DateTimeSearchIndexProvider extends BaseSearchIndexProvider {

        @Override
        public List<SearchIndexableResource> getXmlResourcesToIndex(
                Context context, boolean enabled) {
            List<SearchIndexableResource> result = new ArrayList<>();
            // Remove data/time settings from search in demo mode
            if (UserManager.isDeviceInDemoMode(context)) {
                return result;
            }

            SearchIndexableResource sir = new SearchIndexableResource(context);
            sir.xmlResId = R.xml.date_time_prefs;
            result.add(sir);

            return result;
        }
    }

    private void parseClockDateFormats() {
        // Parse and repopulate mClockDateFormats's entries based on current date.
        String[] dateEntries = getResources().getStringArray(
                R.array.clock_date_format_entries_values);
        CharSequence parsedDateEntries[];
        parsedDateEntries = new String[dateEntries.length];
        Date now = new Date();

        int lastEntry = dateEntries.length - 1;
        int dateStyle = Settings.Secure.getInt(getActivity()
                .getContentResolver(), Settings.Secure.CLOCK_DATE_STYLE, CLOCK_DATE_SHOW_NONE);
        for (int i = 0; i < dateEntries.length; i++) {
            if (i == lastEntry) {
                parsedDateEntries[i] = dateEntries[i];
            } else {
                String newDate;
                CharSequence dateString = DateFormat.format(dateEntries[i], now);
                if (dateStyle == CLOCK_DATE_STYLE_LOWERCASE) {
                    newDate = dateString.toString().toLowerCase();
                } else if (dateStyle == CLOCK_DATE_STYLE_UPPERCASE) {
                    newDate = dateString.toString().toUpperCase();
                } else {
                    newDate = dateString.toString();
                }

                parsedDateEntries[i] = newDate;
            }
        }
        mClockDateFormat.setEntries(parsedDateEntries);
    }
}

/*
 * Copyright (C) 2023-2024 risingOS Android Project
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
package com.android.systemui.weather;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.View;
import android.os.Handler;
import android.os.UserHandle;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.util.android.OmniJawsClient;
import com.android.systemui.res.R;

import java.util.HashMap;
import java.util.Map;

public class WeatherViewController implements OmniJawsClient.OmniJawsObserver {

    private final static String LOCKSCREEN_WEATHER_ENABLED = "lockscreen_weather_enabled";
    private final static String LOCKSCREEN_WEATHER_LOCATION = "lockscreen_weather_location";
    private final static String LOCKSCREEN_WEATHER_TEXT = "lockscreen_weather_text";

    private Context mContext;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private ImageView mWeatherImageView;
    private TextView mWeatherTextView;
    private SettingsObserver mSettingsObserver;

    private boolean mClockFaceEnabled;
    private boolean mShowWeatherLocation;
    private boolean mShowWeatherText;
    private boolean mWeatherEnabled;
    private String mWeatherText;

    private static final Map<String, Integer> WEATHER_CONDITIONS = new HashMap<>();

    static {
        WEATHER_CONDITIONS.put("clouds", R.string.weather_condition_clouds);
        WEATHER_CONDITIONS.put("rain", R.string.weather_condition_rain);
        WEATHER_CONDITIONS.put("clear", R.string.weather_condition_clear);
        WEATHER_CONDITIONS.put("storm", R.string.weather_condition_storm);
        WEATHER_CONDITIONS.put("snow", R.string.weather_condition_snow);
        WEATHER_CONDITIONS.put("wind", R.string.weather_condition_wind);
        WEATHER_CONDITIONS.put("mist", R.string.weather_condition_mist);
    }

    public WeatherViewController(Context context, ImageView weatherImageView, TextView weatherTextView, String weatherText) {
        mContext = context;
        mWeatherImageView = weatherImageView;
        mWeatherTextView = weatherTextView;
        mWeatherText = weatherText;
        mWeatherClient = new OmniJawsClient(context);
        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(null);
            mSettingsObserver.observe();
        }
    }

    public void updateWeatherSettings() {
        mClockFaceEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                "clock_style",
                0, UserHandle.USER_CURRENT) != 0;
        mWeatherEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                LOCKSCREEN_WEATHER_ENABLED,
                0, UserHandle.USER_CURRENT) != 0;
        mShowWeatherLocation = Settings.System.getIntForUser(mContext.getContentResolver(),
                LOCKSCREEN_WEATHER_LOCATION,
                0, UserHandle.USER_CURRENT) != 0;
        mShowWeatherText = Settings.System.getIntForUser(mContext.getContentResolver(),
                LOCKSCREEN_WEATHER_TEXT,
                1, UserHandle.USER_CURRENT) != 0;
        if (mWeatherImageView != null) {
            mWeatherImageView.setVisibility(mWeatherEnabled ? View.VISIBLE : View.GONE);
        }
        if (mWeatherTextView != null) {
            mWeatherTextView.setVisibility(mWeatherEnabled || mWeatherText != null ? View.VISIBLE : View.GONE);
        }
        if (mWeatherEnabled) {
            enableUpdates();
        } else {
            disableUpdates();
        }
        if (mClockFaceEnabled && mWeatherEnabled) {
            if (mWeatherImageView != null && mWeatherImageView.getId() == R.id.default_weather_image) {
                mWeatherImageView.setVisibility(View.GONE);
            }
            if (mWeatherTextView != null && mWeatherTextView.getId() == R.id.default_weather_text) {
                mWeatherTextView.setVisibility(View.GONE);
            }
        }
    }

    private void enableUpdates() {
        if (mWeatherClient != null) {
            mWeatherClient.addObserver(this);
            queryAndUpdateWeather();
        }
    }

    public void disableUpdates() {
        if (mWeatherClient != null) {
            mWeatherClient.removeObserver(this);
        }
    }
    
    public void removeObserver() {
        if (mSettingsObserver != null) {
            mSettingsObserver.unobserve();
        }
    }

    @Override
    public void weatherError(int errorReason) {
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mWeatherInfo = null;
            if (mWeatherImageView != null) {
                mWeatherImageView.setImageDrawable(null);
            }
        }
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    private void queryAndUpdateWeather() {
        try {
            if (mWeatherClient == null || !mWeatherEnabled) {
                hideWeatherViews();
                return;
            }
            mWeatherClient.queryWeather();
            mWeatherInfo = mWeatherClient.getWeatherInfo();
            if (mWeatherInfo != null) {
                if (mWeatherImageView != null) {
                    Drawable d = mWeatherClient.getWeatherConditionImage(mWeatherInfo.conditionCode);
                    mWeatherImageView.setImageDrawable(d);
                }
                if (mWeatherTextView != null) {
                    String formattedCondition = mWeatherInfo.condition.toLowerCase();
                    String conditionText = getConditionText(formattedCondition);
                    mWeatherTextView.setText(mWeatherInfo.temp + mWeatherInfo.tempUnits 
                        + (mShowWeatherLocation ? " · " + mWeatherInfo.city : "") 
                        + (mShowWeatherText ? " · " + conditionText : ""));
                }
            }
        } catch (Exception e) {}
    }
    
    private void hideWeatherViews() {
        if (mWeatherImageView != null) {
            mWeatherImageView.setVisibility(View.GONE);
        }
        if (mWeatherTextView != null) {
            mWeatherTextView.setVisibility(View.GONE);
        }
    }

    private String getConditionText(String condition) {
        for (Map.Entry<String, Integer> entry : WEATHER_CONDITIONS.entrySet()) {
            if (condition.contains(entry.getKey())) {
                return mContext.getResources().getString(entry.getValue());
            }
        }
        return condition;
    }
    
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    LOCKSCREEN_WEATHER_ENABLED), false, this,
                    UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    LOCKSCREEN_WEATHER_LOCATION), false, this,
                    UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    LOCKSCREEN_WEATHER_TEXT), false, this,
                    UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                    "clock_style"), false, this,
                    UserHandle.USER_ALL);
            updateWeatherSettings();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateWeatherSettings();
        }
    }
}

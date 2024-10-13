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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;
import android.view.View;
import com.android.systemui.res.R;

public class WeatherTextView extends TextView {

    private WeatherViewController mWeatherViewController;
    private String mWeatherText;

    public WeatherTextView(Context context) {
        this(context, null);
    }

    public WeatherTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeatherTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WeatherTextView, defStyle, 0);
        mWeatherText = a.getString(R.styleable.WeatherTextView_weatherText);
        a.recycle();
        mWeatherViewController = new WeatherViewController(context, null, this, mWeatherText);
        if (mWeatherText != null && !mWeatherText.isEmpty()) {
            setText(mWeatherText);
        } else {
            setText("");
            setVisibility(View.GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mWeatherViewController.updateWeatherSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWeatherViewController.disableUpdates();
        mWeatherViewController.removeObserver();
    }
}

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
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View.MeasureSpec;
import android.widget.ImageView;

import com.android.systemui.res.R;

public class WeatherImageView extends ImageView {

    private int maxSizePx;

    private WeatherViewController mWeatherViewController;

    public WeatherImageView(Context context) {
        this(context, null);
    }

    public WeatherImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeatherImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        maxSizePx = (int) context.getResources().getDimension(R.dimen.weather_image_max_size);
        mWeatherViewController = new WeatherViewController(context, this, null, null);
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
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = Math.min(maxSizePx, MeasureSpec.getSize(widthMeasureSpec));
        int height = Math.min(maxSizePx, MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(width, height);
    }
}

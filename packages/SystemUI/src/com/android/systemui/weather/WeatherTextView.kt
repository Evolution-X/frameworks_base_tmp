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
package com.android.systemui.weather

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.android.systemui.res.R

class WeatherTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextView(context, attrs, defStyle) {

    private val mWeatherViewController: WeatherViewController
    private val mWeatherText: String?

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.WeatherTextView, defStyle, 0)
        mWeatherText = a.getString(R.styleable.WeatherTextView_weatherText)
        a.recycle()

        mWeatherViewController = WeatherViewController(context, null, this, mWeatherText)

        text = if (!mWeatherText.isNullOrEmpty()) mWeatherText else ""
        visibility = View.GONE
    }

    fun setWeatherEnabled(enabled: Boolean) {
        visibility = if (enabled) View.VISIBLE else View.GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mWeatherViewController.updateWeatherSettings()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mWeatherViewController.disableUpdates()
        mWeatherViewController.removeObserver()
    }
}

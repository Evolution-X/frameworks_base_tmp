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
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.android.internal.util.android.OmniJawsClient
import com.android.systemui.res.R

class WeatherViewController(
    private val context: Context,
    private val weatherImageView: WeatherImageView?,
    private val weatherTextView: WeatherTextView?,
    private var weatherText: String?
) : OmniJawsClient.OmniJawsObserver {

    private val weatherClient = OmniJawsClient(context)
    private var weatherInfo: OmniJawsClient.WeatherInfo? = null
    private var settingsObserver: SettingsObserver? = null

    private var clockFaceEnabled = false
    private var showWeatherLocation = false
    private var showWeatherText = false
    private var weatherEnabled = false

    init {
        settingsObserver = SettingsObserver(null).apply {
            observe()
        }
    }

    fun updateWeatherSettings() {
        clockFaceEnabled = Settings.Secure.getIntForUser(
            context.contentResolver,
            "clock_style",
            0, UserHandle.USER_CURRENT
        ) != 0

        weatherEnabled = Settings.System.getIntForUser(
            context.contentResolver,
            LOCKSCREEN_WEATHER_ENABLED,
            0, UserHandle.USER_CURRENT
        ) != 0

        showWeatherLocation = Settings.System.getIntForUser(
            context.contentResolver,
            LOCKSCREEN_WEATHER_LOCATION,
            0, UserHandle.USER_CURRENT
        ) != 0

        showWeatherText = Settings.System.getIntForUser(
            context.contentResolver,
            LOCKSCREEN_WEATHER_TEXT,
            1, UserHandle.USER_CURRENT
        ) != 0

        weatherImageView?.setWeatherEnabled(weatherEnabled)
        weatherTextView?.setWeatherEnabled(weatherEnabled)

        if (weatherEnabled) enableUpdates() else disableUpdates()

        if (weatherImageView?.id == R.id.default_weather_image) {
            weatherImageView?.setWeatherEnabled(!clockFaceEnabled && weatherEnabled)
        }
        if (weatherTextView?.id == R.id.default_weather_text) {
            weatherTextView?.setWeatherEnabled(!clockFaceEnabled && weatherEnabled)
        }
    }

    private fun enableUpdates() {
        weatherClient.addObserver(this)
        queryAndUpdateWeather()
    }

    fun disableUpdates() {
        weatherClient.removeObserver(this)
    }

    fun removeObserver() {
        settingsObserver?.unobserve()
    }

    override fun weatherError(errorReason: Int) {
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            weatherInfo = null
            weatherImageView?.setImageDrawable(null)
        }
    }

    override fun weatherUpdated() {
        queryAndUpdateWeather()
    }

    private fun queryAndUpdateWeather() {
        try {
            if (!weatherEnabled) {
                hideWeatherViews()
                return
            }

            weatherClient.queryWeather()
            weatherInfo = weatherClient.weatherInfo
            weatherInfo?.let { info ->
                weatherImageView?.setImageDrawable(weatherClient.getWeatherConditionImage(info.conditionCode))
                weatherTextView?.text = buildWeatherText(info)
            }
        } catch (e: Exception) {
        }
    }

    private fun hideWeatherViews() {
        weatherImageView?.visibility = View.GONE
        weatherTextView?.visibility = View.GONE
    }

    private fun buildWeatherText(info: OmniJawsClient.WeatherInfo): String {
        val conditionText = getConditionText(info.condition.lowercase())
        return "${info.temp}${info.tempUnits}" +
                (if (showWeatherLocation) " · ${info.city}" else "") +
                (if (showWeatherText) " · $conditionText" else "")
    }

    private fun getConditionText(condition: String): String {
        for ((key, value) in WEATHER_CONDITIONS) {
            if (condition.contains(key)) {
                return context.resources.getString(value)
            }
        }
        return condition
    }

    inner class SettingsObserver(handler: Handler?) : ContentObserver(handler) {

        fun observe() {
            context.contentResolver.apply {
                registerContentObserver(
                    Settings.System.getUriFor(LOCKSCREEN_WEATHER_ENABLED),
                    false, this@SettingsObserver, UserHandle.USER_ALL
                )
                registerContentObserver(
                    Settings.System.getUriFor(LOCKSCREEN_WEATHER_LOCATION),
                    false, this@SettingsObserver, UserHandle.USER_ALL
                )
                registerContentObserver(
                    Settings.System.getUriFor(LOCKSCREEN_WEATHER_TEXT),
                    false, this@SettingsObserver, UserHandle.USER_ALL
                )
                registerContentObserver(
                    Settings.Secure.getUriFor("clock_style"),
                    false, this@SettingsObserver, UserHandle.USER_ALL
                )
            }
            updateWeatherSettings()
        }

        fun unobserve() {
            context.contentResolver.unregisterContentObserver(this)
        }

        override fun onChange(selfChange: Boolean) {
            updateWeatherSettings()
        }
    }

    companion object {
        private const val LOCKSCREEN_WEATHER_ENABLED = "lockscreen_weather_enabled"
        private const val LOCKSCREEN_WEATHER_LOCATION = "lockscreen_weather_location"
        private const val LOCKSCREEN_WEATHER_TEXT = "lockscreen_weather_text"

        private val WEATHER_CONDITIONS = mapOf(
            "clouds" to R.string.weather_condition_clouds,
            "rain" to R.string.weather_condition_rain,
            "clear" to R.string.weather_condition_clear,
            "storm" to R.string.weather_condition_storm,
            "snow" to R.string.weather_condition_snow,
            "wind" to R.string.weather_condition_wind,
            "mist" to R.string.weather_condition_mist
        )
    }
}

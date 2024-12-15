/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.keyguard

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.widget.RelativeLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.systemui.res.R

class BatteryNowBar(context: Context) : RelativeLayout(context) {
    private var chargingIcon: ImageView? = null
    private var chargingPercentage: TextView? = null
    private var chargingSpeed: String = ""
    private var extraCurrent: Int = 0
    private var extraLevel: Int = 0
    private var extraStatus: Int = 0
    private var mBatteryReceiver: BroadcastReceiver? = null
    private val mHandler = Handler()
    private val mCurrentDivider: Int = context.resources.getInteger(R.integer.config_currentInfoDivider)
    private var colorAnimator: ValueAnimator? = null

    init {
        inflate(context, R.layout.charging_info_now_bar, this)
        init()
        initBatteryReceiver(context)
        context.registerReceiver(mBatteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun init() {
        chargingIcon = findViewById(R.id.chargingIcon)
        chargingPercentage = findViewById(R.id.chargingPercentage)
        setBarBackground(intArrayOf(0xFF4CAF50.toInt(), 0xFF4CAF50.toInt()))
    }

    private fun initBatteryReceiver(context: Context) {
        mBatteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                extraStatus = intent.getIntExtra("status", 1)
                extraLevel = intent.getIntExtra("level", 0)
                extraCurrent = intent.getIntExtra("max_charging_current", -1) / mCurrentDivider
                chargingSpeed = getChargingSpeedString(extraStatus, extraLevel)
                updateBatteryUI()
            }
        }
    }

    private fun animateChargingBackground(startColor: Int) {
        val endColor = darkenColor(startColor)
        colorAnimator?.takeIf { it.isRunning }?.cancel()
        colorAnimator = ValueAnimator.ofArgb(startColor, endColor).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                val animatedColor = animation.animatedValue as Int
                setBarBackground(intArrayOf(lightenColor(animatedColor), animatedColor, darkenColor(animatedColor)))
            }
            start()
        }
    }
    
    private fun setBarBackground(colors: IntArray) {
        val background = GradientDrawable()
        background.cornerRadius = 100f
        background.colors = colors
        background.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        setBackground(background)
    }

    private fun updateBatteryUI() {
        val color = getChargingColor(extraLevel)
        if (extraStatus == 2) {
            chargingPercentage?.text = "$extraLevel% $chargingSpeed"
            animateChargingBackground(color)
        } else {
            chargingPercentage?.text = "$extraLevel%"
            colorAnimator?.takeIf { it.isRunning }?.cancel()
            colorAnimator = null
            setBarBackground(intArrayOf(lightenColor(color), color, darkenColor(color)))
        }
        chargingIcon?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        chargingPercentage?.isSelected = true
    }

    private fun getChargingSpeedString(status: Int, level: Int): String {
        return if (status == 2 && level < 100) "- $extraCurrent mA" else ""
    }

    private fun lightenColor(color: Int): Int {
        val factor = 0.3f
        var alpha = (color shr 24) and 0xff
        var red = (color shr 16) and 0xff
        var green = (color shr 8) and 0xff
        var blue = color and 0xff
        red = (red + (255 - red) * factor).toInt().coerceIn(0, 255)
        green = (green + (255 - green) * factor).toInt().coerceIn(0, 255)
        blue = (blue + (255 - blue) * factor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun darkenColor(color: Int): Int {
        val factor = 0.8f
        var alpha = (color shr 24) and 0xff
        var red = (color shr 16) and 0xff
        var green = (color shr 8) and 0xff
        var blue = color and 0xff
        red = (red * factor).toInt().coerceIn(0, 255)
        green = (green * factor).toInt().coerceIn(0, 255)
        blue = (blue * factor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun getChargingColor(level: Int): Int {
        return when {
            level < 20 -> 0xFFFF0000.toInt()
            level < 30 -> 0xFFFFA500.toInt()
            level < 60 -> 0xFF48D5C4.toInt()
            else -> 0xFF4CAF50.toInt()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(mBatteryReceiver)
        colorAnimator?.takeIf { it.isRunning }?.cancel()
    }
}

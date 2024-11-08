/*
 * Copyright (C) 2024 the risingOS Android Project
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
package com.android.systemui.qs

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.hardware.display.BrightnessInfo
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.util.MathUtils
import android.view.Display
import android.widget.ImageView

import com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MAX
import com.android.settingslib.display.BrightnessUtils.GAMMA_SPACE_MIN
import com.android.settingslib.display.BrightnessUtils.convertGammaToLinearFloat
import com.android.settingslib.display.BrightnessUtils.convertLinearToGammaFloat

import com.android.systemui.res.R

import kotlin.math.roundToInt

class BrightnessSlider(context: Context, attrs: AttributeSet? = null) :
    VerticalSlider(context, attrs), UserInteractionListener {

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var brightnessIcon: ImageView? = null

    private val BRIGHTNESS_MODE_URI: Uri =
        Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE)
    private val BRIGHTNESS_ADJ_URI: Uri =
        Settings.System.getUriFor(Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ)
    private val mHandler = Handler(Looper.getMainLooper())
    private val mContentResolver: ContentResolver = context.contentResolver

    private val mBrightnessObserver: ContentObserver =
        object : ContentObserver(mHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                setBrightnessFromSystem()
            }
        }

    private val mDisplayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            setBrightnessFromSystem()
        }
    }

    init {
        addUserInteractionListener(this)
        mContentResolver.registerContentObserver(
            BRIGHTNESS_MODE_URI,
            false,
            mBrightnessObserver
        )
        mContentResolver.registerContentObserver(
            BRIGHTNESS_ADJ_URI,
            false,
            mBrightnessObserver
        )
        displayManager.registerDisplayListener(
            mDisplayListener,
            mHandler,
            DisplayManager.EVENT_FLAG_DISPLAY_BRIGHTNESS
        )
    }

    override fun onUserSwipe() {
        setBrightnessFromUser()
        val brightnessHapticsIntensity = Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.QS_BRIGHTNESS_SLIDER_HAPTIC, 0, UserHandle.USER_CURRENT)
        performSliderHaptics(brightnessHapticsIntensity)
    }

    override fun onUserInteractionEnd() {}
    
    override fun onLongPress() {
        toggleBrightnessMode()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        brightnessIcon = findViewById(R.id.qs_controls_brightness_slider_icon)
        brightnessIcon?.bringToFront()
        setBrightnessFromSystem()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setBrightnessFromSystem()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mContentResolver.unregisterContentObserver(mBrightnessObserver)
        displayManager.unregisterDisplayListener(mDisplayListener)
    }

    override fun updateSliderPaint() {
        super.updateSliderPaint()
        updateBrightnessIcon()
    }
    
    private fun setBrightnessFromSystem() {
        val newProgress = (getCurrentBrightness() * 100).roundToInt()
        setSliderProgress(newProgress)
        progressRect.top = (1 - newProgress / 100f) * measuredHeight
        updateBrightnessIcon()
        invalidate()
    }

    private fun toggleBrightnessMode() {
        val newMode = if (isAutomaticBrightnessEnabled()) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }
        Settings.System.putIntForUser(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            newMode,
            UserHandle.USER_CURRENT
        )
        updateBrightnessIcon()
    }

    private fun isAutomaticBrightnessEnabled(): Boolean {
        val mode = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        return mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }

    private fun updateBrightnessIcon() {
        brightnessIcon?.apply {
            setImageResource(if (isAutomaticBrightnessEnabled()) R.drawable.ic_qs_brightness_auto_on_new else R.drawable.ic_qs_brightness_auto_off_new)
            updateIconTint(this)
        }
    }

    private fun getCurrentBrightness(): Double {
        val info: BrightnessInfo? = context.getDisplay().getBrightnessInfo()
        return info?.let {
            val value = convertLinearToGammaFloat(it.brightness, it.brightnessMinimum, it.brightnessMaximum)
            getPercentage(value.toDouble(), GAMMA_SPACE_MIN.toFloat(), GAMMA_SPACE_MAX.toFloat())
        } ?: 0.0
    }
    
    private fun getLinearBrightnessValue(): Float {
        val info: BrightnessInfo? = context.getDisplay().getBrightnessInfo()
        val maxProgress = 100
        val maxLinearValue = GAMMA_SPACE_MAX // 65535 or 255 depending on the device
        val scaledProgress = (progress.toFloat() / maxProgress.toFloat() * maxLinearValue).toInt()
        val brightnessValue = info?.let {
            val linearBrightness = convertGammaToLinearFloat(scaledProgress, it.brightnessMinimum, it.brightnessMaximum)
            MathUtils.min(linearBrightness, it.brightnessMaximum).toFloat()
        } ?: 0.0f
        return brightnessValue
    }

    private fun getPercentage(value: Double, min: Float, max: Float): Double {
        return when {
            value > max -> 1.0
            value < min -> 0.0
            else -> (value - min) / (max - min)
        }
    }
    
    private fun setBrightnessFromUser() {
        val displayId = context.getDisplayId()
        val brightnessValue = getLinearBrightnessValue()
        displayManager.setTemporaryBrightness(displayId, brightnessValue)
        displayManager.setBrightness(displayId, brightnessValue)
        updateProgressRect()
    }

    override fun updateProgressRect() {
        super.updateProgressRect()
        updateBrightnessIcon()
    }
}

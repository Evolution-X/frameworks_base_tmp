/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.statusbar.policy

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout

import com.android.systemui.res.R

class CustomClockViewStub @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val settingsObserver: ContentObserver
    private var currentClockView: View? = null
    private var clockStyle = 0

    init {
        settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                updateLayout()
            }
        }
        updateLayout()
    }

    private fun updateLayout() {
        clockStyle = Settings.System.getIntForUser(
            context.contentResolver, "qs_header_clock_style", 0, UserHandle.USER_CURRENT
        )
        currentClockView?.let {
            removeView(it)
            currentClockView = null
        }
        val layoutResId = when (clockStyle) {
            1 -> R.layout.qs_header_clock_chip
            2 -> R.layout.qs_header_clock_oos
            3 -> R.layout.qs_header_clock_analog
            else -> R.layout.qs_header_clock_simple
        }
        if (clockStyle != 0) {
            currentClockView = LayoutInflater.from(context).inflate(layoutResId, this, false)
            addView(currentClockView)
        }
        visibility = if (clockStyle == 0) View.GONE else View.VISIBLE
        currentClockView?.visibility = if (clockStyle == 0) View.GONE else View.VISIBLE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("qs_header_clock_style"),
            false,
            settingsObserver
        )
        updateLayout()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.contentResolver.unregisterContentObserver(settingsObserver)
    }
}

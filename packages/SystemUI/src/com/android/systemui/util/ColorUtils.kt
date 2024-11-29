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
package com.android.systemui.util

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.android.systemui.res.R

object ColorUtils {

    fun getSurfaceColor(context: Context): Int {
        return if (isNightMode(context)) {
            ContextCompat.getColor(context, R.color.lockscreen_widget_background_color_dark)
        } else {
            ContextCompat.getColor(context, R.color.lockscreen_widget_background_color_light)
        }
    }

    fun getPrimaryColor(context: Context): Int {
        return if (isNightMode(context)) {
            ContextCompat.getColor(context, R.color.peek_title_text_color_dark)
        } else {
            ContextCompat.getColor(context, R.color.peek_title_text_color_light)
        }
    }

    fun getSecondaryColor(context: Context): Int {
        return if (isNightMode(context)) {
            ContextCompat.getColor(context, R.color.peek_summary_text_color_dark)
        } else {
            ContextCompat.getColor(context, R.color.peek_summary_text_color_light)
        }
    }
    
    fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    
    fun getActiveColor(context: Context): Int {
        return ContextCompat.getColor(
            context,
            if (isNightMode(context)) R.color.peek_accent_color_dark else R.color.peek_accent_color_light
        )
    }
}

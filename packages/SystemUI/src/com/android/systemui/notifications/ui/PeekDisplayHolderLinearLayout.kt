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
package com.android.systemui.notifications.ui

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.content.Context

import com.android.systemui.res.R

class PeekDisplayHolderLinearLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var contentObserver: ContentObserver? = null

    init {
        orientation = VERTICAL
    }

    private fun registerContentObserver() {
        val handler = Handler()
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                updateBottomPeekDisplayTopMargin()
                updatePeekDisplayVisibility()
            }
        }
        val contentResolver: ContentResolver = context.contentResolver
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("peek_display_bottom_margin"),
            false,
            contentObserver!!
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("peek_display_location"),
            false,
            contentObserver!!
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("peek_display_notifications"),
            false,
            contentObserver!!
        )
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateBottomPeekDisplayTopMargin()
        updatePeekDisplayVisibility()
        registerContentObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.contentResolver.unregisterContentObserver(contentObserver!!)
    }

    private fun updateBottomPeekDisplayTopMargin() {
        val marginTop = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_bottom_margin", 64, android.os.UserHandle.USER_CURRENT
        )
        setBottomPeekDisplayTopMargin(marginTop)
    }

    private fun setBottomPeekDisplayTopMargin(marginTopDp: Int) {
        val marginTopPx = dpToPx(marginTopDp)
        val peekDisplayBottom = findViewById<View>(R.id.peek_display_bottom)
        val params = peekDisplayBottom?.layoutParams as? LinearLayout.LayoutParams
        params?.topMargin = marginTopPx
        peekDisplayBottom?.layoutParams = params
    }

    private fun updatePeekDisplayVisibility() {
        val location = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_location", 1, android.os.UserHandle.USER_CURRENT
        )
        setContainerVisibility(location)
    }

    fun dpToPx(dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale).toInt()
    }

    private fun setContainerVisibility(location: Int) {
        val enabled = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_notifications", 0, android.os.UserHandle.USER_CURRENT
        ) != 0
        if (!enabled) {
            visibility = View.GONE
            return
        } 
        when (id) {
            R.id.peek_display_area_top -> {
                visibility = if (location == 0) View.VISIBLE else View.GONE
            }
            R.id.peek_display_area_bottom -> {
                visibility = if (location == 1) View.VISIBLE else View.GONE
            }
        }
    }
}

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

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.NonNull

import com.android.systemui.res.R

class QsControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val controller: QsControlsViewController? = QsControlsViewController(this)

    override fun onVisibilityChanged(@NonNull changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isAttachedToWindow) {
            controller?.updateResources()
        }
    }

    fun updateResources() {
        controller?.updateResources()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        controller?.onFinishInflate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller?.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller?.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (TileUtils.isQsWidgetsEnabled(context)) {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            when (id) {
                R.id.qs_controls_layout_shade -> {
                    visibility = if (isLandscape) VISIBLE else GONE
                }
            }
            updateResources()
        }
    }
}

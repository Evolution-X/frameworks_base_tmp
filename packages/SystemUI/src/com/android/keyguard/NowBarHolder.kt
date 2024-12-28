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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.android.systemui.res.R
import com.android.systemui.util.MediaSessionManagerHelper

class NowBarHolder @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), MediaSessionManagerHelper.MediaMetadataListener {

    private var mViewPager: ViewPager? = null
    private var mController: NowBarController
    private var mMediaSessionManagerHelper: MediaSessionManagerHelper
    
    private var isChargingStatusHandled = false

    private val batteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    if (isCharging(intent) && isPluggedIn(intent) && !isChargingStatusHandled) {
                        mViewPager?.setCurrentItem(1)
                        isChargingStatusHandled = true
                    }
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    mViewPager?.setCurrentItem(0)
                    isChargingStatusHandled = false
                }
            }
        }
    }

    init {
        inflate(context, R.layout.now_bar_holder, this)
        mController = NowBarController.getInstance(context)
        mViewPager = findViewById(R.id.nowBarViewPager)
        mViewPager?.adapter = NowBarAdapter(context)
        mViewPager?.setPageTransformer(false, PageTransitionTransformer())
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
        mMediaSessionManagerHelper = MediaSessionManagerHelper.getInstance(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mController.addNowBarHolder(this)
        mMediaSessionManagerHelper.addMediaMetadataListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mController.removeNowBarHolder(this)
        context.unregisterReceiver(batteryReceiver)
        mMediaSessionManagerHelper.removeMediaMetadataListener(this)
    }

    override fun onMediaMetadataChanged() {
        showMusicNowBarIfNeeded()
    }

    override fun onPlaybackStateChanged() {
        showMusicNowBarIfNeeded()
    }

    private inner class NowBarAdapter(private val context: Context) : PagerAdapter() {

        override fun getCount(): Int {
            return 2
        }

        override fun instantiateItem(container: View, position: Int): Any {
            val view: View = when (position) {
                0 -> MusicNowBar(context)
                else -> BatteryNowBar(context)
            }
            (container as ViewPager).addView(view)
            return view
        }

        override fun destroyItem(container: View, position: Int, `object`: Any) {
            (container as ViewPager).removeView(`object` as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view == `object`
        }
    }

    private inner class PageTransitionTransformer : ViewPager.PageTransformer {

        private val SCALE_FACTOR = 0.9f
        private val TRANSLATION_Y_FACTOR = 40f
        private val ALPHA_FACTOR = 0.7f

        override fun transformPage(page: View, position: Float) {
            when {
                position < -1 || position > 1 -> page.alpha = 0f
                position <= 0 -> {
                    page.apply {
                        scaleX = 1f
                        scaleY = 1f
                        translationY = 0f
                        alpha = 1f
                    }
                }
                position <= 1 -> {
                    val scale = SCALE_FACTOR + (1 - SCALE_FACTOR) * (1 - position)
                    var translationY = position * TRANSLATION_Y_FACTOR
                    page.apply {
                        scaleX = scale
                        scaleY = scale
                        this.translationY = translationY
                        alpha = ALPHA_FACTOR + (1 - ALPHA_FACTOR) * (1 - position)
                    }
                }
            }
        }
    }

    private fun isCharging(intent: Intent): Boolean {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun isPluggedIn(intent: Intent): Boolean {
        val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        return chargePlug == BatteryManager.BATTERY_PLUGGED_AC
                || chargePlug == BatteryManager.BATTERY_PLUGGED_USB
                || chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }

    private fun showMusicNowBarIfNeeded() {
        if (!mMediaSessionManagerHelper.isMediaPlaying()) return
        mViewPager?.setCurrentItem(0)
    }
}

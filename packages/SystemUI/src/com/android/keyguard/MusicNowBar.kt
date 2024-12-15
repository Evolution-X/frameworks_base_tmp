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

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import android.view.View
import com.android.systemui.common.ui.view.LaunchableConstraintLayout
import com.android.systemui.monet.ColorScheme
import com.android.systemui.res.R
import com.android.systemui.util.MediaSessionManagerHelper
import com.android.settingslib.drawable.CircleFramedDrawable

class MusicNowBar(context: Context) : RelativeLayout(context), MediaSessionManagerHelper.MediaMetadataListener {

    private var albumArt: ImageView? = null
    private var prevButton: ImageButton? = null
    private var playPauseButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var trackTitle: TextView? = null
    private val mMediaSessionManagerHelper: MediaSessionManagerHelper = MediaSessionManagerHelper.getInstance(context)
    private val mContext: Context = context
    private var mNowBarLayout: LaunchableConstraintLayout? = null

    init {
        inflate(context, R.layout.music_now_bar, this)
        mNowBarLayout = findViewById(R.id.constraintRoot)

        albumArt = findViewById(R.id.albumArt)
        trackTitle = findViewById(R.id.trackTitle)
        prevButton = findViewById(R.id.prevButton)
        playPauseButton = findViewById(R.id.playPauseButton)
        nextButton = findViewById(R.id.nextButton)
        setBackground(Color.BLACK)

        albumArt?.setOnClickListener { mMediaSessionManagerHelper.launchMediaApp() }
        prevButton?.setOnClickListener { mMediaSessionManagerHelper.prevSong() }
        playPauseButton?.setOnClickListener { mMediaSessionManagerHelper.toggleMediaPlaybackState() }
        nextButton?.setOnClickListener { mMediaSessionManagerHelper.nextSong() }

        setDefaultIcon()
        trackTitle?.text = mContext.resources.getString(R.string.nowbar_no_media)

        setOnClickListener { mMediaSessionManagerHelper.showMediaDialog(mNowBarLayout as View) }
        setOnLongClickListener {
            mMediaSessionManagerHelper.launchMediaApp()
            true
        }
    }

    private fun setBackground(color: Int) {
        val background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 100f
        }
        setBackground(background)
    }

    private fun updateMediaPlaybackState() {
        val mediaMetadata = mMediaSessionManagerHelper.getMediaMetadata()
        val isPlaying = mMediaSessionManagerHelper.isMediaPlaying()

        if (resetMediaIfNeeded()) {
            return
        }

        playPauseButton?.setImageDrawable(mContext.getDrawable(R.drawable.ic_media_pause))

        mediaMetadata?.let {
            val title = it.getString(MediaMetadata.METADATA_KEY_TITLE)
            trackTitle?.text = title?.takeIf { it.isNotEmpty() } ?: mContext.resources.getString(R.string.nowbar_unknown_title)
            trackTitle?.isSelected = true

            val bitmap = mMediaSessionManagerHelper.getMediaBitmap()
            if (bitmap != null) {
                val targetSize = mContext.resources.getDimension(R.dimen.nowbar_album_art_size).toInt()
                val roundedImg: Drawable = CircleFramedDrawable(bitmap, targetSize)
                albumArt?.setImageDrawable(roundedImg)
                albumArt?.setColorFilter(Color.TRANSPARENT)
            } else {
                setDefaultIcon()
            }
        }
    }
    
    private fun resetMediaIfNeeded(): Boolean {
        if (!mMediaSessionManagerHelper.isMediaPlaying()) {
            playPauseButton?.setImageDrawable(mContext.getDrawable(R.drawable.ic_media_play))
            trackTitle?.text = mContext.resources.getString(R.string.nowbar_no_media)
            trackTitle?.isSelected = true
            setDefaultIcon()
            setBackground(Color.BLACK)
            return true
        }
        return false
    }
    
    private fun setDefaultIcon() {
        albumArt?.setImageResource(R.drawable.ic_volume_eq)
        albumArt?.setColorFilter(Color.WHITE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mMediaSessionManagerHelper.addMediaMetadataListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mMediaSessionManagerHelper.removeMediaMetadataListener(this)
    }

    override fun onMediaMetadataChanged() {
        updateMediaPlaybackState()
    }

    override fun onPlaybackStateChanged() {
        updateMediaPlaybackState()
    }

    override fun onMediaColorsChanged() {
        if (resetMediaIfNeeded()) {
            return
        }
        val colorScheme = mMediaSessionManagerHelper.getColorScheme()
        val color = colorScheme?.accent1?.s800 ?: Color.BLACK
        setBackground(color)
    }
}

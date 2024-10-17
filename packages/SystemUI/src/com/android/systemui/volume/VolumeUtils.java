/*
 * Copyright (C) 2023-2025 The risingOS Android Project
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

package com.android.systemui.volume;

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.res.R;

import com.android.internal.util.android.ThemeUtils;
import com.android.internal.util.android.VibrationUtils;

public class VolumeUtils {
    private static final String TAG = "VolumeUtils";

    private static final String VOLUME_STYLE_CATEGORY = "android.theme.customization.volume_panel";
    private static final String VOLUME_STYLE_OVERLAY_TARGET_PKG = "com.android.systemui";
    private static final String VOLUME_STYLE_OVERLAY_PREFIX = "com.android.system.volume.style";

    private static final int SOUND_HAPTICS_DELAY = 50;
    private static final int SOUND_HAPTICS_DURATION = 2000;

    private static final long VIBRATE_HAPTICS_TIMEOUT = 80L;
    private long lastVibrateTime = 0;
    private int mVolHapticsIntensity = 1;

    private Ringtone mRingtone;
    private MediaPlayer mMediaPlayer = null;
    private Context mContext;
    private Handler mHandler;

    private final ThemeUtils mThemeUtils;

    private int mVolumeStyle = 0;

    private boolean mSoundHapticsEnabled = false;

    private static final int[] seekbarDrawables = {
            R.drawable.volume_row_seekbar,
            R.drawable.volume_row_seekbar_double_layer,
            R.drawable.volume_row_seekbar_gradient,
            R.drawable.volume_row_seekbar_neumorph,
            R.drawable.volume_row_seekbar_neumorph_outline,
            R.drawable.volume_row_seekbar_outline,
            R.drawable.volume_row_seekbar_shaded_layer
        };

    private VolumeDialogImplObserver mVolumeDialogImplObserver;

    public VolumeUtils(Context context) {
        mContext = context;
        mHandler = new Handler();
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnCompletionListener(mp -> stopPlayback());
        mThemeUtils = ThemeUtils.getInstance(context);
        mVolumeDialogImplObserver = new VolumeDialogImplObserver();
        mVolumeDialogImplObserver.observe();
    }

    public int getRowDrawable() {
        return seekbarDrawables[mVolumeStyle];
    }

    public void setSoundsHapticsEnabled(boolean enabled) {
        mSoundHapticsEnabled = enabled;
    }

    private void updateVolumeStyleOverlay() {
        setVolumeStyleOverlay(mVolumeStyle == 0 ? VOLUME_STYLE_OVERLAY_TARGET_PKG
            : VOLUME_STYLE_OVERLAY_PREFIX + mVolumeStyle);
    }

    private void setVolumeStyleOverlay(String pkgName) {
        mHandler.postDelayed(() -> {
            mThemeUtils.setOverlayEnabled(VOLUME_STYLE_CATEGORY, pkgName, VOLUME_STYLE_OVERLAY_TARGET_PKG);
        }, 1250);
    }

    public void playSoundForStreamType(int streamType) {
        if (!mSoundHapticsEnabled) return;
        Uri soundUri = null;
        switch (streamType) {
            case AudioManager.STREAM_RING:
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                break;
            case AudioManager.STREAM_NOTIFICATION:
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                break;
            case AudioManager.STREAM_ALARM:
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                break;
        }

        playSound(soundUri, streamType);
    }

    private void playSound(Uri soundUri, int streamType) {
        stopPlayback();
        if (soundUri == null) {
            soundUri = Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.raw.volume_control_sound);
        }
        try {
            if (streamType == AudioManager.STREAM_RING) {
                mRingtone = RingtoneManager.getRingtone(mContext, soundUri);
                mRingtone.play();
                mHandler.postDelayed(() -> stopPlayback(), SOUND_HAPTICS_DURATION);
            } else {
                mMediaPlayer.setDataSource(mContext, soundUri);
                mMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(getAudioUsageForStreamType(streamType))
                        .build());
                mMediaPlayer.setOnPreparedListener(mp -> {
                    mMediaPlayer.start();
                    mHandler.postDelayed(() -> stopPlayback(), SOUND_HAPTICS_DURATION);
                });
                mMediaPlayer.prepareAsync();
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not play sound: " + e.getMessage());
        }
    }

    private int getAudioUsageForStreamType(int streamType) {
        switch (streamType) {
            case AudioManager.STREAM_RING:
                return AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
            case AudioManager.STREAM_NOTIFICATION:
                return AudioAttributes.USAGE_NOTIFICATION;
            case AudioManager.STREAM_ALARM:
                return AudioAttributes.USAGE_ALARM;
            default:
                return AudioAttributes.USAGE_UNKNOWN;
        }
    }

    private void startRingtone() {
        if (mRingtone != null) {
            mRingtone.play();
        }
    }

    private void startMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo(0);
            mMediaPlayer.start();
        }
    }

    private void stopPlayback() {
        if (mRingtone != null && mRingtone.isPlaying()) {
            mRingtone.stop();
        }
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.reset();
        }
    }

    public void onDestroy() {
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mRingtone != null) {
            mRingtone.stop();
            mRingtone = null;
        }
        mHandler.removeCallbacksAndMessages(null);
        mVolumeDialogImplObserver.unobserve();
    }

    public void performVolumeHaptics(boolean showing, int progress, int maxVolume) {
        if (mVolHapticsIntensity == 0 || !showing) return;
        int volHaptics = mVolHapticsIntensity;
        if (progress >= maxVolume * 100) {
            volHaptics = 4;
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastVibrateTime >= VIBRATE_HAPTICS_TIMEOUT) {
            VibrationUtils.triggerVibration(mContext, volHaptics);
            lastVibrateTime = currentTime;
        }
    }

    private class VolumeDialogImplObserver extends ContentObserver {

        public VolumeDialogImplObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings();
        }

        void updateSettings() {
            int newVolumeStyle = Settings.System.getInt(
                    mContext.getContentResolver(),
                    "custom_volume_styles", 0);
            if (newVolumeStyle != mVolumeStyle) {
                mVolumeStyle = newVolumeStyle;
                updateVolumeStyleOverlay();
            }
            mSoundHapticsEnabled = Settings.System.getInt(
                    mContext.getContentResolver(),
                    "volume_sound_haptics", 0) != 0;
            mVolHapticsIntensity = Settings.System.getInt(
                    mContext.getContentResolver(),
                    "volume_slider_haptics_intensity", 0);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("custom_volume_styles"),
                    false, this);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("volume_sound_haptics"),
                    false, this);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("volume_slider_haptics_intensity"),
                    false, this);
            updateSettings();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }
}

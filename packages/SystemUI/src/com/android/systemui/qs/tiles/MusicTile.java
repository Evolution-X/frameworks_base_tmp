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
package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.session.MediaSessionLegacyHelper;
import android.media.MediaMetadata;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.service.quicksettings.Tile;
import android.view.KeyEvent;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.res.R;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import com.android.systemui.util.MediaSessionManagerHelper;

import java.util.List;

import javax.inject.Inject;

public class MusicTile extends QSTileImpl<BooleanState> 
    implements MediaSessionManagerHelper.MediaMetadataListener {

    public static final String TILE_SPEC = "musictile";

    private final MediaSessionManagerHelper mMediaSessionManagerHelper;
    
    private boolean mListening = false;

    @Inject
    public MusicTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mMediaSessionManagerHelper = MediaSessionManagerHelper.Companion.getInstance(mContext);
    }

    private void dispatchMediaKeyWithWakeLockToMediaSession(final int keycode) {
        final MediaSessionLegacyHelper helper = MediaSessionLegacyHelper.getHelper(mContext);
        if (helper == null) {
            return;
        }
        KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0);
        helper.sendMediaButtonEvent(event, true);
        event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
        helper.sendMediaButtonEvent(event, true);
        refreshState();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mMediaSessionManagerHelper == null) {
            return;
        }
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mMediaSessionManagerHelper.addMediaMetadataListener(this);
        } else {
            mMediaSessionManagerHelper.removeMediaMetadataListener(this);
        }
    }

    @Override
    public void onMediaMetadataChanged() {
        refreshState();
    }

    @Override
    public void onPlaybackStateChanged() {
        refreshState();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    @Override
    protected void handleLongClick(@Nullable Expandable expandable) {}

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        MediaMetadata metadata = mMediaSessionManagerHelper.getMediaMetadata();
        String trackTitle = metadata != null ? metadata.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
        String artistName = metadata != null ? metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
        if (mMediaSessionManagerHelper.isMediaPlaying()) {
            state.icon = ResourceIcon.get(R.drawable.ic_media_play);
            state.label = trackTitle != null ? trackTitle : mContext.getString(R.string.quick_settings_music_label);
            state.secondaryLabel = artistName != null ? artistName : mContext.getString(R.string.quick_settings_music_unavailable);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_media_pause);
            state.label = trackTitle != null ? trackTitle : mContext.getString(R.string.quick_settings_music_label);
            state.secondaryLabel = artistName != null ? artistName : mContext.getString(R.string.quick_settings_music_unavailable);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_music_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
}

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
package com.android.systemui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class PocketStateReceiver extends BroadcastReceiver {
    private static final String TAG = "PocketStateReceiver";

    public static final String ACTION_POCKET_STATE_CHANGED = "org.rising.server.action.POCKET_STATE_CHANGED";
    public static final String EXTRA_IN_POCKET = "in_pocket";

    private PocketStateListener mListener;

    public PocketStateReceiver(PocketStateListener listener) {
        this.mListener = listener;
    }

    public void register(Context context) {
        IntentFilter filter = new IntentFilter(ACTION_POCKET_STATE_CHANGED);
        context.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (ACTION_POCKET_STATE_CHANGED.equals(intent.getAction())) {
            boolean isInPocket = intent.getBooleanExtra(EXTRA_IN_POCKET, false);
            if (mListener != null) {
                mListener.onPocketStateChanged(isInPocket);
            }
        }
    }

    public interface PocketStateListener {
        void onPocketStateChanged(boolean isInPocket);
    }
}

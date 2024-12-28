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
package com.android.keyguard;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class NowBarController {
    private static NowBarController instance;

    private final Context context;
    private final ContentResolver contentResolver;
    private final Set<View> mViews = Collections.synchronizedSet(new HashSet<>());
    
    private boolean mEnabled = false;

    private final ContentObserver contentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateVisibility();
        }
    };

    private NowBarController(Context context) {
        this.context = context.getApplicationContext();
        this.contentResolver = context.getContentResolver();
        mEnabled = Settings.System.getIntForUser(
            contentResolver,
            "keyguard_now_bar_enabled",
            0,
            UserHandle.USER_CURRENT
        ) != 0;
    }

    public static synchronized NowBarController getInstance(Context context) {
        if (instance == null) {
            instance = new NowBarController(context);
        }
        return instance;
    }

    public void addNowBarHolder(View view) {
        if (view == null) return;
        boolean wasEmpty = mViews.isEmpty();
        mViews.add(view);
        if (wasEmpty) {
            registerListeners();
        }
        updateVisibility(view);
    }

    public void removeNowBarHolder(View view) {
        if (view == null) return;
        mViews.remove(view);
        if (mViews.isEmpty()) {
            unregisterListeners();
        }
    }

    public void show() {
        if (!mEnabled) return;
        for (View view : mViews) {
            view.post(() -> view.setVisibility(View.VISIBLE));
        }
    }

    public void hide() {
        for (View view : mViews) {
            view.post(() -> view.setVisibility(View.GONE));
        }
    }

    private void updateVisibility() {
        mEnabled = Settings.System.getIntForUser(
            contentResolver,
            "keyguard_now_bar_enabled",
            0,
            UserHandle.USER_CURRENT
        ) != 0;
        for (View view : mViews) {
            updateVisibility(view);
        }
    }

    private void updateVisibility(View view) {
        if (view == null) return;
        view.post(() -> view.setVisibility(mEnabled ? View.VISIBLE : View.GONE));
    }

    private void registerListeners() {
        contentResolver.registerContentObserver(
            Settings.System.getUriFor("keyguard_now_bar_enabled"),
            false,
            contentObserver,
            UserHandle.USER_CURRENT
        );
        updateVisibility();
    }

    private void unregisterListeners() {
        contentResolver.unregisterContentObserver(contentObserver);
    }

    public void setAlpha(float alpha) {
        for (View view : mViews) {
            if (view != null) {
                view.post(() -> view.setAlpha(alpha));
            }
        }
    }
}

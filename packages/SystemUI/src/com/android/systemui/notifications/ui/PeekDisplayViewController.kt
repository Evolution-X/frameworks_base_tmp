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

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View

import com.android.systemui.Dependency
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.NotificationListener
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet

class PeekDisplayViewController private constructor() :
    ConfigurationController.ConfigurationListener,
    NotificationListener.NotificationHandler {

    private val peekDisplayViews: MutableSet<PeekDisplayView> = CopyOnWriteArraySet()
    private lateinit var mContext: Context

    private val configurationController: ConfigurationController = Dependency.get(ConfigurationController::class.java)
    private val statusBarStateController: StatusBarStateController = Dependency.get(StatusBarStateController::class.java)
    private val notificationListener: NotificationListener = Dependency.get(NotificationListener::class.java)

    private val settingKeys = listOf(
        "peek_display_style",
        "peek_display_notifications",
        Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS
    )

    private var mCallbacksRegistered = false
    private var mDozing = false
    private var mPeekDisplayEnabled = false

    private val settingsObserver: ContentObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            updatePeekDisplayState()
        }
    }

    private val statusBarStateListener = object : StatusBarStateController.StateListener {
        override fun onStateChanged(newState: Int) {}
        override fun onDozingChanged(dozing: Boolean) {
            if (mDozing == dozing) {
                return
            }
            mDozing = dozing
            if (mDozing) {
                resetShelves()
            }
        }
    }

    fun addPeekDisplayView(view: PeekDisplayView) {
        if (peekDisplayViews.isEmpty()) {
            mContext = view.context.applicationContext
            registerCallbacks()
        }
        peekDisplayViews.add(view)
    }

    fun removePeekDisplayView(view: PeekDisplayView) {
        peekDisplayViews.remove(view)
        if (peekDisplayViews.isEmpty()) {
            unregisterCallbacks()
        }
    }

    private fun registerCallbacks() {
        statusBarStateController.addCallback(statusBarStateListener)
        statusBarStateListener.onDozingChanged(statusBarStateController.isDozing())
        configurationController.addCallback(this)
        notificationListener.addNotificationHandler(this)
        settingKeys.map { Settings.Secure.getUriFor(it) }.forEach { uri ->
            mContext.contentResolver.registerContentObserver(uri, false, settingsObserver)
        }
        updatePeekDisplayState()
    }
    
    private fun updatePeekDisplayState() {
        mPeekDisplayEnabled = Settings.Secure.getIntForUser(mContext.contentResolver,
            "peek_display_notifications", 0, android.os.UserHandle.USER_CURRENT) == 1
        peekDisplayViews.forEach {
            it.updatePeekDisplayState()
            it.updateNotificationShelf(notificationListener.getActiveNotifications().toList())
        }
        updateVisibility()
    }

    private fun unregisterCallbacks() {
        configurationController.removeCallback(this)
        statusBarStateController.removeCallback(statusBarStateListener)
        mContext.contentResolver.unregisterContentObserver(settingsObserver)
        notificationListener.removeNotificationHandler(this)
    }

    override fun onUiModeChanged() {
        updatePeekDisplayState()
    }

    override fun onThemeChanged() {
        updatePeekDisplayState()
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap
    ) {
        peekDisplayViews.forEach {
            it.currentRankingMap = rankingMap
            it.updateNotificationShelf(notificationListener.getActiveNotifications().toList())
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap,
        reason: Int
    ) {
        peekDisplayViews.forEach {
            it.currentRankingMap = rankingMap
            it.updateNotificationShelf(notificationListener.getActiveNotifications().toList())
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap
    ) {
        peekDisplayViews.forEach {
            it.currentRankingMap = rankingMap
            it.updateNotificationShelf(notificationListener.getActiveNotifications().toList())
        }
    }

    override fun onNotificationRankingUpdate(rankingMap: NotificationListenerService.RankingMap) {
        peekDisplayViews.forEach { it.currentRankingMap = rankingMap }
    }

    override fun onNotificationsInitialized() {}
    
    fun removeCurrentNotification(sbn: StatusBarNotification) {
        val sbnKey = sbn.key
        notificationListener.cancelNotification(sbnKey)
        peekDisplayViews.forEach {
            it.hideNotificationCard()
            it.updateNotificationShelf(notificationListener.getActiveNotifications().toList())
        }
    }
    
    fun clearAllNotifications() {
        try {
            notificationListener.cancelAllNotifications()
        } catch (e: Exception) {}
    }

    fun resetShelves() {
        peekDisplayViews.forEach {
            it.hideNotificationCard()
            it.resetNotificationShelf()
        }
    }

    fun hidePeekDisplayView() {
        peekDisplayViews.forEach {
            it.visibility = View.GONE
            it.hideNotificationCard()
            it.resetNotificationShelf()
        }
    }

    fun showPeekDisplayView() {
        peekDisplayViews.forEach {
            if (mPeekDisplayEnabled) {
                it.visibility = View.VISIBLE
            }
        }
    }

    fun setAlpha(alpha: Float) {
        peekDisplayViews.forEach {
            it.post { it.alpha = alpha }
        }
    }
    
    fun updateVisibility() {
        peekDisplayViews.forEach {
            it.visibility = if (mPeekDisplayEnabled) View.VISIBLE else View.GONE
        }
    }

    companion object {
        @Volatile
        private var instance: PeekDisplayViewController? = null

        fun getInstance(): PeekDisplayViewController {
            return instance ?: synchronized(this) {
                instance ?: PeekDisplayViewController().also { instance = it }
            }
        }
    }
}

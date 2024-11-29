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

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView

import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.android.systemui.Dependency
import com.android.systemui.res.R
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.NotificationListener
import com.android.systemui.util.ColorUtils
import com.android.systemui.util.NotificationUtils

class PeekDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var PEEK_DISPLAY_LOCATION_TOP = 0
    private var PEEK_DISPLAY_LOCATION_BOTTOM = 1

    private var notificationShelf: RecyclerView? = null
    private var notificationCard: CardView? = null
    private var notificationIcon: ImageView? = null
    private var notificationTitle: TextView? = null
    private var notificationSummary: TextView? = null
    private var notificationHeader: TextView? = null
    private var overflowText: TextView? = null
    private var clearAllButton: ImageButton? = null
    private var minimizeButton: ImageView? = null
    private var dismissButton: ImageView? = null
    private var clearAllHandler: Handler = Handler()
    private var currentDisplayedNotification: StatusBarNotification? = null
    public var currentRankingMap: NotificationListenerService.RankingMap? = null
    private var lastFilteredNotifications: List<StatusBarNotification> = emptyList()
    private var lastLayoutWidth: Int = ViewGroup.LayoutParams.WRAP_CONTENT

    private val notificationAdapter: NotificationAdapter = NotificationAdapter()

    private val activityStarter: ActivityStarter = Dependency.get(ActivityStarter::class.java)
    public val notificationListener: NotificationListener = Dependency.get(NotificationListener::class.java)
    private val mController: PeekDisplayViewController = PeekDisplayViewController.getInstance()

    private var allowPrivateNotifications = true
    private var isMinimalStyleEnabled = false
    public var isPeekDisplayEnabled = false
    private var showOverflow = false
    public var peekDisplayLocation = PEEK_DISPLAY_LOCATION_BOTTOM

    init {
        val layout = if (id == R.id.peek_display_top) R.layout.peek_display_top 
            else R.layout.peek_display_bottom 
        LayoutInflater.from(context).inflate(layout, this, true)
        notificationShelf = findViewById(R.id.notificationShelf)
        notificationCard = findViewById(R.id.notificationCard)
        notificationIcon = findViewById(R.id.notificationIcon)
        notificationTitle = findViewById(R.id.notificationTitle)
        notificationSummary = findViewById(R.id.notificationSummary)
        notificationHeader = findViewById(R.id.notificationHeader)
        minimizeButton = findViewById(R.id.minimizeButton)
        dismissButton = findViewById(R.id.dismissButton)
        overflowText = findViewById(R.id.overflowText)
        clearAllButton = findViewById(R.id.clearAllButton)
        notificationShelf?.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        notificationShelf?.adapter = notificationAdapter
        notificationShelf?.setOnClickListener {
            if (notificationCard?.visibility == View.VISIBLE) {
                hideNotificationCard()
            }
        }
        minimizeButton?.setOnClickListener { hideNotificationCard() }
        dismissButton?.setOnClickListener { removeCurrentNotification() }
        overflowText?.setOnClickListener { showClearAllButton() }
        clearAllButton?.setOnClickListener { clearAllNotifications() }
        notificationShelf?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                showClearAllButton()
            }
        })
    }
    
    private fun showClearAllButton() {
        overflowText?.visibility = View.GONE
        clearAllButton?.visibility = View.VISIBLE
        clearAllHandler.removeCallbacksAndMessages(null)
        clearAllHandler.postDelayed({
            if (clearAllButton?.visibility == View.VISIBLE) {
                hideClearAllButton()
            }
        }, 3000)
    }
    
    private fun hideClearAllButton() {
        clearAllButton?.visibility = View.GONE
        overflowText?.visibility = if (showOverflow) View.VISIBLE else View.GONE
    }

    fun clearAllNotifications() {
        try {
            notificationListener.cancelAllNotifications()
        } catch (e: Exception) {}
        updateNotificationShelf(emptyList())
        overflowText?.visibility = View.GONE
        clearAllButton?.visibility = View.GONE
    }

    fun removeCurrentNotification() {
        currentDisplayedNotification?.let { sbn ->
            val sbnKey = sbn.key
            notificationListener?.let { listener ->
                listener.cancelNotification(sbnKey)
                updateNotificationShelf(listener.getActiveNotifications().toList())
                hideNotificationCard()
            }
        }
    }

    fun updateNotificationShelf(notificationList: List<StatusBarNotification>) {
        val sortedNotifications = notificationList.sortedByDescending { it.postTime }
        val filteredNotifications = sortedNotifications.filter { sbn ->
            val ranking = currentRankingMap?.getRawRankingObject(sbn.key)
            val shouldFilterSensitiveNotifications = !allowPrivateNotifications && (ranking?.hasSensitiveContent() == true)
            val (title, content) = NotificationUtils.resolveNotificationContent(sbn)
            !sbn.isOngoing() && 
            !sbn.notification.isFgsOrUij() && 
            !sbn.notification.isMediaNotification() &&
            (sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0) && 
            !shouldFilterSensitiveNotifications &&
            (title.isNotBlank() || content.isNotBlank())
        }
        if (filteredNotifications == lastFilteredNotifications) {
            return
        }
        lastFilteredNotifications = filteredNotifications
        notificationAdapter.clearSelection()
        if (filteredNotifications.isNotEmpty()) {
            notificationAdapter.submitList(filteredNotifications)
            notificationShelf?.visibility = View.VISIBLE
        } else {
            notificationShelf?.visibility = View.GONE
        }
        val iconMarginEnd = context.resources.getDimensionPixelSize(R.dimen.peek_display_notification_icon_margin_end)
        val newWidth = if (filteredNotifications.size <= 4) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            (4 * getIconSize(context)) + (4 * iconMarginEnd)
        }
        if (newWidth != lastLayoutWidth) {
            val layoutParams = notificationShelf?.layoutParams as? ViewGroup.LayoutParams
            layoutParams?.width = newWidth
            notificationShelf?.layoutParams = layoutParams
            lastLayoutWidth = newWidth
        }
        showOverflow = (filteredNotifications.size > 4)
        overflowText?.visibility = if (showOverflow) View.VISIBLE else View.GONE
        clearAllButton?.visibility = View.GONE
    }
    
    private fun getIconSize(ctx: Context): Int {
        return ctx.resources.getDimensionPixelSize(
                if (isMinimalStyleEnabled) R.dimen.peek_display_notification_icon_size_minimal 
                else R.dimen.peek_display_notification_icon_size)
    }

    private fun toggleNotificationDetails(sbn: StatusBarNotification) {
        val (title, content) = NotificationUtils.resolveNotificationContent(sbn)
        if ((title.isBlank() && content.isBlank()) 
            || currentDisplayedNotification == sbn && notificationCard?.visibility == View.VISIBLE) {
            hideNotificationCard()
        } else {
            currentDisplayedNotification = sbn
            val subText = sbn.notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            val packageName = sbn.packageName
            val timeSinceArrival = android.text.format.DateUtils.getRelativeTimeSpanString(
                sbn.postTime, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
            )
            val appLabel = try {
                context.packageManager.getApplicationLabel(NotificationUtils.getApplicationInfo(sbn, context)).toString()
            } catch (e: Exception) {
                packageName
            }

            val maxLength = 17
            val subHeaderText = if (subText.length > maxLength) subText.take(maxLength) + "..." else subText
            val appLabelHeaderText = if (appLabel.length > maxLength) appLabel.take(maxLength) + "..." else appLabel

            val headerText = if (subHeaderText.isNotBlank()) {
                "$subHeaderText • $appLabelHeaderText • $timeSinceArrival"
            } else {
                "$appLabelHeaderText • $timeSinceArrival"
            }
            
            notificationHeader?.text = headerText
            notificationTitle?.text = title
            notificationSummary?.text = content
            notificationIcon?.setImageDrawable(NotificationUtils.resolveNotificationIcon(sbn, context))
            setClickListener(notificationCard, notificationSummary, notificationIcon, notificationHeader) {
                launchNotificationIntent()
            }
            notificationCard?.visibility = View.VISIBLE
            notificationCard?.scaleX = 0.8f
            notificationCard?.scaleY = 0.8f
            notificationCard?.alpha = 0f
            notificationCard?.animate()
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.alpha(1f)
                ?.setDuration(300L)
                ?.setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                ?.start()
            Settings.System.putIntForUser(context.contentResolver, "peek_display_expanded", 1, UserHandle.USER_CURRENT)
        }
    }
    
    private fun launchNotificationIntent() {
        val currentNotification = currentDisplayedNotification ?: return
        val pkgName = currentNotification.packageName ?: return
        val pendingIntent = currentNotification.notification?.contentIntent
        pendingIntent?.let {
            try {
                activityStarter.postStartActivityDismissingKeyguard(it)
            } catch (e: Exception) {
                launchAppFromPackageName(pkgName)
            }
        } ?: launchAppFromPackageName(pkgName)
        removeCurrentNotification()
    }

    private fun launchAppFromPackageName(pkgName: String) {
        val appIntent = context.packageManager.getLaunchIntentForPackage(pkgName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        appIntent?.let {
            activityStarter.startActivity(it, true)
        }
    }
    
    fun resetNotificationShelf() {
        notificationShelf?.scrollToPosition(0)
    }

    fun hideNotificationCard() {
        notificationCard?.animate()
            ?.scaleX(0.8f)
            ?.scaleY(0.8f)
            ?.alpha(0f)
            ?.setDuration(200L)
            ?.setInterpolator(android.view.animation.AccelerateInterpolator())
            ?.withEndAction {
                notificationCard?.visibility = if (peekDisplayLocation == PEEK_DISPLAY_LOCATION_TOP) View.GONE else View.INVISIBLE
                currentDisplayedNotification = null
            }
            ?.start()
            Settings.System.putIntForUser(context.contentResolver, "peek_display_expanded", 0, UserHandle.USER_CURRENT)
            notificationAdapter.clearSelection()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mController.setPeekDisplayView(this)
        mController.registerCallbacks()
        updatePeekDisplayState()
    }

    fun updatePeekDisplayState() {
        allowPrivateNotifications = Settings.Secure.getIntForUser(
                    context.contentResolver,
                    Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS,
                    1,
                    UserHandle.USER_CURRENT
                ) == 1
        isMinimalStyleEnabled = Settings.Secure.getIntForUser(context.contentResolver,
            "peek_display_style", 0, UserHandle.USER_CURRENT) == 1
        isPeekDisplayEnabled = Settings.Secure.getIntForUser(context.contentResolver,
            "peek_display_notifications", 0, UserHandle.USER_CURRENT) == 1
        peekDisplayLocation = Settings.Secure.getIntForUser(context.contentResolver,
            "peek_display_location", PEEK_DISPLAY_LOCATION_BOTTOM, UserHandle.USER_CURRENT)
        visibility = if (isPeekDisplayEnabled) View.VISIBLE else View.GONE
        if (isPeekDisplayEnabled) {
            updateNotificationShelf(notificationListener.getActiveNotifications().toList())
            updateViewColors()
        }
    }

    fun updateViewColors() {
        val surfaceColor = if (isMinimalStyleEnabled) Color.TRANSPARENT else ColorUtils.getSurfaceColor(context)
        val primaryColor = if (isMinimalStyleEnabled) Color.WHITE else ColorUtils.getPrimaryColor(context)
        val primaryTextColor = ColorUtils.getPrimaryColor(context)
        notificationCard?.setCardBackgroundColor(ColorUtils.getSurfaceColor(context))
        clearAllButton?.backgroundTintList = ColorStateList.valueOf(surfaceColor)
        notificationTitle?.setTextColor(primaryTextColor)
        notificationSummary?.setTextColor(ColorUtils.getSecondaryColor(context))
        notificationHeader?.setTextColor(primaryTextColor)
        overflowText?.setTextColor(if (isMinimalStyleEnabled) Color.WHITE else surfaceColor)
        minimizeButton?.imageTintList = ColorStateList.valueOf(primaryColor)
        dismissButton?.imageTintList = ColorStateList.valueOf(primaryColor)
        clearAllButton?.imageTintList = ColorStateList.valueOf(primaryColor)
        notificationAdapter.notifyDataSetChanged()
        notificationShelf?.invalidate()
    }
    
    private fun setClickListener(vararg views: View?, action: () -> Unit) {
        views.forEach { view ->
            view?.setOnClickListener { action() }
        }
    }

    inner class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

        private val notifications: MutableList<StatusBarNotification> = mutableListOf()
        private var selectedPosition: Int = -1
        private var isHighlighted = false 

        fun submitList(list: List<StatusBarNotification>) {
            notifications.clear()
            notifications.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val iconView = ImageView(parent.context).apply {
                val iconSize = getIconSize(parent.context)
                layoutParams = RecyclerView.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = parent.context.resources.getDimensionPixelSize(R.dimen.peek_display_notification_icon_margin_end)
                }
                background = createBackgroundDrawable(if (isMinimalStyleEnabled) Color.TRANSPARENT 
                    else ColorUtils.getSurfaceColor(parent.context))
                val padding = if (isMinimalStyleEnabled) 0 else parent.context.resources.getDimensionPixelSize(R.dimen.peek_notification_icon_padding)
                setPadding(padding, padding, padding, padding)
                isClickable = true
                imageTintList = ColorStateList.valueOf(if (isMinimalStyleEnabled) Color.WHITE 
                    else ColorUtils.getPrimaryColor(parent.context))
            }
            return NotificationViewHolder(iconView)
        }

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
            val notification = notifications[position]
            val context = holder.iconView.context
            val iconDrawable = NotificationUtils.resolveSmallIcon(notification, context)
            holder.iconView.setImageDrawable(iconDrawable)
            val isSelected = selectedPosition == position
            val newColor = if (isSelected) ColorUtils.getActiveColor(context) else ColorUtils.getSurfaceColor(context)
            val newTint = if (isSelected) ColorUtils.getSurfaceColor(context) else ColorUtils.getPrimaryColor(context)
            holder.iconView.imageTintList = ColorStateList.valueOf(if (isMinimalStyleEnabled) Color.WHITE else newTint)
            holder.iconView.background = createBackgroundDrawable(if (isMinimalStyleEnabled) Color.TRANSPARENT else newColor)
            holder.iconView.setOnClickListener {
                if (isMinimalStyleEnabled) {
                    toggleNotificationDetails(notification)
                } else {
                    if (isSelected) {
                        selectedPosition = RecyclerView.NO_POSITION
                        notifyItemChanged(position)
                        hideNotificationCard()
                    } else {
                        val prevSelectedPosition = selectedPosition
                        selectedPosition = position
                        notifyItemChanged(prevSelectedPosition)
                        notifyItemChanged(position)
                        toggleNotificationDetails(notification)
                    }
                }
            }
        }

        fun clearSelection() {
            val previousSelectedPosition = selectedPosition
            selectedPosition = RecyclerView.NO_POSITION
            if (previousSelectedPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousSelectedPosition)
            }
        }

        override fun getItemCount(): Int = notifications.size

        private fun createBackgroundDrawable(color: Int): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                cornerRadius = resources.getDimension(R.dimen.peek_notification_icon_corner_radius)
            }
        }

        inner class NotificationViewHolder(val iconView: ImageView) : RecyclerView.ViewHolder(iconView)
    }
}

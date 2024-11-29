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
package com.android.systemui.util

import android.app.Notification
import android.app.Notification.MessagingStyle
import android.app.Person
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import android.service.notification.StatusBarNotification

import androidx.core.content.ContextCompat

object NotificationUtils {

    private val TAG = "NotificationUtils"

    fun resolveNotificationContent(sbn: StatusBarNotification): Pair<String, String> {
        val titleText = sbn.notification.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: ""
        val contentText = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: sbn.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ""
        return titleText.toString() to contentText.toString()
    }

    fun resolveNotificationIcon(sbn: StatusBarNotification, context: Context): Drawable? {
        return try {
            val extras = sbn.notification.extras
            
            getAvatarIcon(sbn)?.let { return it.loadDrawable(context) }

            val iconObject = sequenceOf(
                extras.get(Notification.EXTRA_VERIFICATION_ICON),
                extras.get(Notification.EXTRA_CONVERSATION_ICON),
                extras.get(Notification.EXTRA_LARGE_ICON_BIG),
                extras.get(Notification.EXTRA_PICTURE),
                extras.get(Notification.EXTRA_LARGE_ICON),
                extras.get(Notification.EXTRA_SMALL_ICON)
            ).filterNotNull().firstOrNull()
            
            Log.d(TAG, "Icon Object Type: ${iconObject?.javaClass?.name ?: "null"}")

            when (iconObject) {
                is Bitmap -> BitmapDrawable(context.resources, iconObject)
                is Icon -> iconObject.loadDrawable(context)
                is Drawable -> iconObject
                else -> resolveAppIcon(sbn, context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving notification icon, using application icon instead", e)
            resolveAppIcon(sbn, context)
        }
    }

    private fun getAvatarIcon(sbn: StatusBarNotification): Icon? {
        return try {
            val extras: Bundle = sbn.notification.extras
            val messages =
                MessagingStyle.Message.getMessagesFromBundleArray(
                    extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                )
            val user = extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)
            for (i in messages.indices.reversed()) {
                val message = messages[i]
                val sender = message.senderPerson
                if (sender != null && sender !== user) {
                    return sender.icon
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun resolveSmallIcon(sbn: StatusBarNotification, context: Context): Drawable? {
        return try {
            sbn.notification.smallIcon?.let { icon ->
                when (icon) {
                    is Icon -> {
                        icon.loadDrawable(context)
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun resolveAppIcon(sbn: StatusBarNotification, context: Context): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(getApplicationInfo(sbn, context))
        } catch (e: Exception) {
            null
        }
    }

    fun getApplicationInfo(sbn: StatusBarNotification, context: Context): ApplicationInfo {
        return context.packageManager.getApplicationInfo(sbn.packageName, 0)
    }
}

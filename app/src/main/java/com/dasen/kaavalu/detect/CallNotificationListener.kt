package com.dasen.kaavalu.detect

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dasen.kaavalu.core.Channel
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus

/**
 * Detects answered WhatsApp calls from the ongoing-call notification.
 * We read only the title (caller name or number) and the status line. Never message content.
 *
 * TEST FIRST on every demo phone: the wording of the ongoing-call line depends on the
 * WhatsApp version and the phone language. Watch logcat for "KaavaluNotif" while you place
 * a call between two team phones, then adjust ONGOING_HINTS to whatever you actually see.
 */
class CallNotificationListener : NotificationListenerService() {

    private val watched = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val activeKeys = mutableSetOf<String>()

    override fun onListenerConnected() {
        Log.d(TAG, "notification access is live")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in watched) return
        val n = sbn.notification
        if (n.category != Notification.CATEGORY_CALL) return

        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        Log.d(TAG, "call notification title=[$title] text=[$text]")

        val lower = text.lowercase()
        // A ringing call is not an answered call. Only an ongoing one opens a session.
        if (ONGOING_HINTS.none { it in lower }) return
        if (!activeKeys.add(sbn.key)) return

        SignalBus.emit(
            Signal.CallStarted(
                number = title,
                channel = Channel.WHATSAPP,
                isKnown = looksKnown(title),
                isVideo = VIDEO_HINTS.any { it in lower },
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (activeKeys.remove(sbn.key)) SignalBus.emit(Signal.CallEnded)
    }

    /**
     * WhatsApp shows the saved contact name for people you know, and a raw number, or a
     * profile name prefixed with "~", for people you do not.
     */
    private fun looksKnown(title: String): Boolean {
        if (title.isBlank()) return false
        if (title.trimStart().startsWith("~")) return false     // unsaved profile name
        val looksLikeNumber = title.count(Char::isDigit) >= 7
        return if (looksLikeNumber) ContactsChecker.isKnown(this, title) else true
    }

    private companion object {
        const val TAG = "KaavaluNotif"
        val ONGOING_HINTS = listOf("ongoing", "in progress", "चालू", "जारी", "ಚಾಲ್ತಿ", "ನಡೆಯುತ್ತಿದೆ")
        val VIDEO_HINTS = listOf("video", "वीडियो", "ವಿಡಿಯೋ", "ವೀಡಿಯೊ")
    }
}

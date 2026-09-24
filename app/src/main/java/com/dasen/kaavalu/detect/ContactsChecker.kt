package com.dasen.kaavalu.detect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.dasen.kaavalu.Prefs

object ContactsChecker {

    /**
     * A local lookup against the phone's own contacts. Nothing leaves the device,
     * and Kaavalu never reads the contact list as a whole.
     */
    fun isKnown(ctx: Context, number: String): Boolean {
        if (number.isBlank()) return false
        if (Prefs.isTrusted(ctx, number)) return true
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
}

/**
 * The call screening service sees who is calling; the telephony callback sees when the
 * call is actually answered. This hands the first fact to the second.
 */
object CallContext {

    data class Pending(val number: String?, val known: Boolean, val unverified: Boolean)

    @Volatile
    private var pending: Pending? = null

    private val history = mutableMapOf<String, MutableList<Long>>()

    fun set(p: Pending) { pending = p }

    fun take(): Pending? = pending.also { pending = null }

    /** In-app call memory. No READ_CALL_LOG permission, no call log access. */
    @Synchronized
    fun recordAndCount(number: String?): Int {
        if (number.isNullOrBlank()) return 1
        val now = System.currentTimeMillis()
        val list = history.getOrPut(number) { mutableListOf() }
        list.removeAll { now - it > DAY_MS }
        list.add(now)
        return list.size
    }

    private const val DAY_MS = 24 * 60 * 60_000L
}

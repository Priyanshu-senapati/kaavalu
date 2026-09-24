package com.dasen.kaavalu

import android.content.Context

/**
 * Settings are tiny and the hackathon is short: SharedPreferences, not Room.
 * The event log is the v2 reason to add Room.
 */
object Prefs {

    private fun p(c: Context) = c.getSharedPreferences("kaavalu", Context.MODE_PRIVATE)

    fun guardian(c: Context): String? = p(c).getString("guardian", null)

    fun userName(c: Context): String = p(c).getString("name", "Your family member")!!

    /** The language the family picked for the parent, not the device locale. */
    fun language(c: Context): String = p(c).getString("lang", "en")!!

    fun setLanguage(c: Context, lang: String) {
        p(c).edit().putString("lang", lang).apply()
    }

    fun onboardingDone(c: Context): Boolean = p(c).getBoolean("onboarded", false)

    fun setOnboardingDone(c: Context, done: Boolean) {
        p(c).edit().putBoolean("onboarded", done).apply()
    }

    fun save(c: Context, guardian: String, name: String, lang: String) {
        p(c).edit()
            .putString("guardian", guardian.trim())
            .putString("name", name.trim())
            .putString("lang", lang)
            .apply()
    }

    private fun key(number: String) = number.filter(Char::isDigit).takeLast(10)

    fun isTrusted(c: Context, number: String): Boolean =
        key(number) in p(c).getStringSet("trusted", emptySet())!!

    fun trust(c: Context, number: String) {
        val k = key(number)
        if (k.isEmpty()) return
        val set = p(c).getStringSet("trusted", emptySet())!!.toMutableSet()
        set += k
        p(c).edit().putStringSet("trusted", set).apply()
    }

    /**
     * Recent calls from unknown numbers, encoded by [com.dasen.kaavalu.core.CallHistory].
     * Read once from the older single-call key, so a phone that recorded a call before the
     * history existed does not lose it.
     */
    fun history(c: Context): String? =
        p(c).getString("history", null) ?: p(c).getString("last_session", null)

    fun setHistory(c: Context, encoded: String) {
        p(c).edit().putString("history", encoded).remove("last_session").apply()
    }

    /** Whether the family has already been told that protection stopped, this outage. */
    fun healthAlerted(c: Context): Boolean = p(c).getBoolean("health_alerted", false)

    fun setHealthAlerted(c: Context, alerted: Boolean) {
        p(c).edit().putBoolean("health_alerted", alerted).apply()
    }

    /** Everything this app knows about the family. Synchronous: the next screen reads it. */
    fun clearAll(c: Context) {
        p(c).edit().clear().commit()
    }

    fun trustedCount(c: Context): Int = p(c).getStringSet("trusted", emptySet())!!.size
}

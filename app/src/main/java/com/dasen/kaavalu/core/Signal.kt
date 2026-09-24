package com.dasen.kaavalu.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class Channel { CELLULAR, WHATSAPP }
enum class AppKind { PAYMENT, REMOTE_ACCESS, INSTALLER }

/** Everything a detector can observe. Detectors only emit; they never score or act. */
sealed interface Signal {
    data class CallStarted(
        val number: String?,
        val channel: Channel,
        val isKnown: Boolean,
        val isVideo: Boolean = false,
        val unverified: Boolean = false,
    ) : Signal

    data object CallEnded : Signal

    data class RepeatCaller(val count: Int) : Signal

    data class SensitiveAppOpened(val pkg: String, val kind: AppKind) : Signal

    data class NoticeFlagged(val score: Int, val markers: List<String>) : Signal

    data object MarkedSafe : Signal
}

object SignalBus {
    private val _signals = MutableSharedFlow<Signal>(extraBufferCapacity = 64)
    val signals: SharedFlow<Signal> = _signals.asSharedFlow()
    fun emit(signal: Signal) { _signals.tryEmit(signal) }
}

object SensitiveApps {
    /**
     * Verify these against the Play Store URL of each app, and add whatever is actually
     * installed on the demo phones: a bank app that is not on this list is a +30 that
     * never fires. The last four were found by listing packages on the demo phone itself.
     */
    private val payment = setOf(
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",   // Google Pay
        "net.one97.paytm",
        "in.org.npci.upiapp",                       // BHIM
        "com.sbi.lotusintouch",                     // YONO SBI
        "com.csam.icici.bank.imobile",              // iMobile Pay
        "com.snapwork.hdfc",                        // HDFC Bank, older app
        "com.axis.mobile",                          // Axis Mobile
        "com.hdfcbank.android.now",                 // HDFC NOW, on the demo phone
        "com.indusind.indie",                       // IndusInd INDIE, on the demo phone
        "com.samsung.android.spay",                 // Samsung Wallet, holds cards and UPI
    )
    private val remote = setOf(
        "com.anydesk.anydeskandroid",
        "com.teamviewer.quicksupport.market",
        "com.teamviewer.teamviewer.market.mobile",
    )

    /**
     * The system screen that installs an APK. Scammers send "RTO challan.apk" or a fake bank
     * app over WhatsApp and talk the victim through installing it during the call; that
     * screen coming up is the moment the phone is handed over. Usage access sees it without
     * needing QUERY_ALL_PACKAGES, which Play restricts. The Google name covers Pixel, Nothing
     * and most Android One phones; add the installer of any phone the app is tested on.
     */
    private val installer = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.packageinstaller",                // Xiaomi, Redmi, Poco
    )

    fun kindOf(pkg: String): AppKind? = when (pkg) {
        in payment -> AppKind.PAYMENT
        in remote -> AppKind.REMOTE_ACCESS
        in installer -> AppKind.INSTALLER
        else -> null
    }
}

/**
 * Where a caller's number is from, as far as the number itself says. Most digital arrest
 * and investment scam calls come from abroad — Cambodia, Myanmar, Laos, the Gulf — often
 * over WhatsApp with a foreign code. An Indian agency calling from a foreign number is a
 * contradiction the victim is in no state to notice, so the phone notices for them.
 */
object CallerOrigin {

    /**
     * True only when the number carries an explicit non-Indian country code. A number with
     * no code at all is local by the dialling rules, and a caller shown by name (a WhatsApp
     * profile) says nothing about where it is, so both are false rather than guessed.
     */
    fun isInternational(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        val n = number.filter { it.isDigit() || it == '+' }
        return when {
            n.startsWith("+") -> n.length > 4 && !n.startsWith("+91")
            n.startsWith("00") -> n.length > 5 && !n.startsWith("0091")
            else -> false
        }
    }
}

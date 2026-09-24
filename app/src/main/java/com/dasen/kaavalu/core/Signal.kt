package com.dasen.kaavalu.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class Channel { CELLULAR, WHATSAPP }
enum class AppKind { PAYMENT, REMOTE_ACCESS }

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

    fun kindOf(pkg: String): AppKind? = when (pkg) {
        in payment -> AppKind.PAYMENT
        in remote -> AppKind.REMOTE_ACCESS
        else -> null
    }
}

package com.dasen.kaavalu.detect

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log

/**
 * The sanctioned API for caller-ID apps. Kaavalu never blocks, silences or rejects a call:
 * it only records who is calling so the call-state watcher knows what was answered.
 */
class ScamCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection == Call.Details.DIRECTION_INCOMING) {
            val number = details.handle?.schemeSpecificPart
            val known = number != null && ContactsChecker.isKnown(this, number)
            val unverified =
                details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED
            Log.d(TAG, "incoming number=$number known=$known unverified=$unverified")
            CallContext.set(CallContext.Pending(number, known, unverified))
        }
        // Empty response: allow the call through untouched.
        respondToCall(details, CallResponse.Builder().build())
    }

    private companion object {
        const val TAG = "KaavaluScreening"
    }
}

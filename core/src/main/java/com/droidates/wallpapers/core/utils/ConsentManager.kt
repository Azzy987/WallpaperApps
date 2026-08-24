package com.droidates.wallpapers.core.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.droidates.wallpapers.core.config.AppConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gathers ad consent through Google's User Messaging Platform (UMP).
 *
 * Why this exists: users in the EEA/UK must be given a consent choice by a certified CMP
 * before personalised ads may be served. Without one, that traffic falls back to
 * non-personalised ads at a much lower eCPM — and several mediation networks decline to
 * bid at all when no consent signal is present. So this is a revenue mechanism as much
 * as a compliance one.
 *
 * Outside those regions UMP reports consent as NOT_REQUIRED and every method here is a
 * no-op, so this costs nothing for the majority of traffic.
 *
 * Ordering matters: consent must be *requested* before MobileAds initializes, so the SDK
 * picks up the stored choice. [gather] is safe to call before every ad init;
 * [canRequestAds] tells callers whether loading an ad is permitted yet.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    private val gathering = AtomicBoolean(false)

    @Volatile
    private var consentInformation: ConsentInformation? = null

    private fun info(context: Context): ConsentInformation =
        consentInformation ?: UserMessagingPlatform.getConsentInformation(context).also {
            consentInformation = it
        }

    /**
     * Whether ads may be requested at all.
     *
     * True when consent was granted, or when it was never required (most non-EEA
     * traffic). Ad loading is gated on this so a request never goes out ahead of a
     * choice the user still has to make.
     */
    fun canRequestAds(context: Context): Boolean =
        runCatching { info(context).canRequestAds() }
            .getOrElse {
                // Never let a consent failure become an ads outage: if UMP itself is
                // broken, fall through and let the SDK apply its own default handling.
                Log.w(TAG, "canRequestAds() failed, allowing request", it)
                true
            }

    /**
     * Requests a consent update and shows the form if one is required.
     *
     * Must be driven from an Activity — the form is a dialog. Safe to call on every
     * launch: UMP only presents a form when a choice is actually outstanding.
     *
     * @param onComplete invoked once the flow settles, whether a form was shown, was
     *   not required, or failed. Always fires, so callers can proceed to ad init.
     */
    fun gather(activity: Activity, onComplete: () -> Unit = {}) {
        // Debug builds get a reset + EEA simulation so the form can actually be seen;
        // in release the user's real region and stored choice apply.
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (AppConfig.IS_DEBUG) {
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .build()
                    )
                }
            }
            .build()

        if (!gathering.compareAndSet(false, true)) {
            // A gather is already in flight; UMP would reject the concurrent call.
            onComplete()
            return
        }

        val consentInfo = info(activity)
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error ${formError.errorCode}: ${formError.message}")
                    } else {
                        Log.d(TAG, "Consent resolved; canRequestAds=${consentInfo.canRequestAds()}")
                    }
                    gathering.set(false)
                    onComplete()
                }
            },
            { requestError ->
                // A consent lookup failure must not block the app. Ads fall back to
                // whatever the SDK considers safe for the region.
                Log.w(TAG, "Consent update failed ${requestError.errorCode}: ${requestError.message}")
                gathering.set(false)
                onComplete()
            }
        )
    }

    /**
     * Whether a privacy-options entry point should be offered in Settings.
     *
     * Users who consented must be able to change their minds; Google requires the entry
     * point to be visible exactly when UMP says it is available.
     */
    fun isPrivacyOptionsRequired(context: Context): Boolean =
        runCatching {
            info(context).privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        }.getOrDefault(false)

    /** Reopens the consent form so the user can change a previous choice. */
    fun showPrivacyOptions(activity: Activity, onComplete: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                Log.w(TAG, "Privacy options error ${error.errorCode}: ${error.message}")
            }
            onComplete()
        }
    }
}

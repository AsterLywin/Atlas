package com.atlas.reader.ui.security

import android.os.Bundle
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.atlas.reader.ui.base.activity.BaseActivity
import com.atlas.reader.ui.base.delegate.SecureActivityDelegate
import com.atlas.reader.util.system.AuthenticatorUtil
import com.atlas.reader.util.system.AuthenticatorUtil.startAuthentication
import logcat.LogPriority
import atlas.core.common.i18n.stringResource
import atlas.core.common.util.system.logcat
import atlas.i18n.MR

/**
 * Blank activity with a BiometricPrompt.
 */
class UnlockActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAuthentication(
            stringResource(MR.strings.unlock_app_title, stringResource(MR.strings.app_name)),
            confirmationRequired = false,
            callback = object : AuthenticatorUtil.AuthenticationCallback() {
                override fun onAuthenticationError(
                    activity: FragmentActivity?,
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(activity, errorCode, errString)
                    logcat(LogPriority.ERROR) { errString.toString() }
                    finishAffinity()
                }

                override fun onAuthenticationSucceeded(
                    activity: FragmentActivity?,
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(activity, result)
                    SecureActivityDelegate.unlock()
                    finish()
                }
            },
        )
    }
}

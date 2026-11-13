package nl.tufein.openconnect.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi

/** Asks the person to prove who they are. [DeviceUnlocker] is Android's; tests use a fake. */
interface Unlocker {
    /** False when the phone has no screen lock (or fingerprint) to check against. */
    fun available(): Boolean

    /** Shows the check; [onResult] gets true only when it passed. */
    fun unlock(onResult: (Boolean) -> Unit)
}

/**
 * Android 11 and newer: the system's biometric prompt, which also accepts the PIN, pattern or password.
 * Older versions (down to Android 8): the screen lock's own confirm screen. Nothing is stored; Android keeps the secret.
 */
class DeviceUnlocker(private val activity: ComponentActivity) : Unlocker {
    private val keyguard get() = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private var pending: ((Boolean) -> Unit)? = null

    // Must be registered before the activity starts, so this class is built in onCreate.
    private val confirm = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending?.invoke(result.resultCode == Activity.RESULT_OK)
        pending = null
    }

    override fun available(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            activity.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            keyguard.isDeviceSecure
        }

    override fun unlock(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= 30) {
            prompt(onResult)
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent(activity.getString(nl.tufein.openconnect.R.string.lock_prompt_title), null)
            if (intent == null) {
                onResult(false)
            } else {
                pending = onResult
                confirm.launch(intent)
            }
        }
    }

    @RequiresApi(30)
    private fun prompt(onResult: (Boolean) -> Unit) {
        BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(nl.tufein.openconnect.R.string.lock_prompt_title))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
            .authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(false)
                },
            )
    }

    private companion object {
        @RequiresApi(30)
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

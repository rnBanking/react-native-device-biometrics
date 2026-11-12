package com.devicebiometrics

import androidx.biometric.BiometricPrompt
import com.facebook.react.bridge.Promise

/**
 * Prompt callback for [DeviceBiometricsModule.createKeys].
 * On success, runs [onAuthenticated] so the Keystore key can be created.
 * Maps [BiometricPrompt] error codes onto JS `BiometricPromptErrorCode`.
 */
class CreateKeysCallback(
  private val promise: Promise,
  private val onAuthenticated: Runnable,
) : BiometricPrompt.AuthenticationCallback() {
  override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
    super.onAuthenticationError(errorCode, errString)
    promise.reject(rejectCode(errorCode), errString.toString())
  }

  override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
    super.onAuthenticationSucceeded(result)
    onAuthenticated.run()
  }

  companion object {
    /** Maps Android [BiometricPrompt] codes to JS `BiometricPromptErrorCode`. */
    fun rejectCode(errorCode: Int): String {
      return when (errorCode) {
        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "ERROR_NEGATIVE_BUTTON"
        BiometricPrompt.ERROR_USER_CANCELED -> "ERROR_USER_CANCELED"
        BiometricPrompt.ERROR_CANCELED -> "ERROR_CANCELED"
        BiometricPrompt.ERROR_LOCKOUT -> "ERROR_LOCKOUT"
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "ERROR_LOCKOUT_PERMANENT"
        BiometricPrompt.ERROR_NO_BIOMETRICS -> "ERROR_BIOMETRY_NOT_AVAILABLE"
        else -> "BIOMETRIC_ERROR"
      }
    }
  }
}

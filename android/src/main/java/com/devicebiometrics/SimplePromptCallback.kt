package com.devicebiometrics

import androidx.biometric.BiometricPrompt
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableNativeMap

/**
 * Prompt callback for [DeviceBiometricsModule.simplePrompt].
 * Cancel resolves `{ success: false }` so JS can treat it as a soft abort.
 * Other failures reject with `BiometricPromptErrorCode`.
 */
class SimplePromptCallback(
  private val promise: Promise,
) : BiometricPrompt.AuthenticationCallback() {
  override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
    super.onAuthenticationError(errorCode, errString)
    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
      errorCode == BiometricPrompt.ERROR_USER_CANCELED
    ) {
      val resultMap = WritableNativeMap()
      resultMap.putBoolean("success", false)
      resultMap.putString("error", "User cancellation")
      promise.resolve(resultMap)
    } else {
      promise.reject(CreateKeysCallback.rejectCode(errorCode), errString.toString())
    }
  }

  override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
    super.onAuthenticationSucceeded(result)

    val resultMap = WritableNativeMap()
    resultMap.putBoolean("success", true)
    promise.resolve(resultMap)
  }
}

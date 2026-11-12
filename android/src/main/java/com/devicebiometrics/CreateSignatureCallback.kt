package com.devicebiometrics

import android.util.Base64
import androidx.biometric.BiometricPrompt
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableNativeMap

/**
 * Prompt callback for [DeviceBiometricsModule.createSignature].
 * After biometrics succeed, signs [payload] with the CryptoObject Signature
 * and resolves standard Base64.
 */
class CreateSignatureCallback(
  private val promise: Promise,
  private val payload: String,
) : BiometricPrompt.AuthenticationCallback() {
  override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
    super.onAuthenticationError(errorCode, errString)
    promise.reject(CreateKeysCallback.rejectCode(errorCode), errString.toString())
  }

  override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
    super.onAuthenticationSucceeded(result)

    try {
      val cryptoObject = result.cryptoObject
      val cryptoSignature = cryptoObject!!.signature
      cryptoSignature!!.update(payload.toByteArray())
      val signed = cryptoSignature.sign()
      var signedString = Base64.encodeToString(signed, Base64.DEFAULT)
      signedString = signedString.replace("\r", "").replace("\n", "")

      val resultMap = WritableNativeMap()
      resultMap.putBoolean("success", true)
      resultMap.putString("signature", signedString)
      promise.resolve(resultMap)
    } catch (e: Exception) {
      promise.reject("Error creating signature: " + e.message, "Error creating signature")
    }
  }
}

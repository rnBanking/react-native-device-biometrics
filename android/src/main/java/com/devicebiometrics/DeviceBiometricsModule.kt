package com.devicebiometrics

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.module.annotations.ReactModule
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.RSAKeyGenParameterSpec
import java.util.concurrent.Executors

/**
 * Android Turbo Module for RSA keys in Android Keystore, optionally gated by
 * biometrics. JS calls this through `react-native-device-biometrics`.
 *
 * Distinct keys are addressed with `keyAlias` (the app uses `DEVICE` and
 * `BIOMETRIC`). The default alias is `biometric_key`.
 */
@ReactModule(name = DeviceBiometricsModule.NAME)
class DeviceBiometricsModule(reactContext: ReactApplicationContext) :
  NativeDeviceBiometricsSpec(reactContext) {

  private val biometricKeyAlias = "biometric_key"

  override fun getName(): String = NAME

  /** Reports whether BIOMETRIC_STRONG (and passcode, if allowed) can run. */
  override fun isSensorAvailable(params: ReadableMap, promise: Promise) {
    try {
      if (isCurrentSDKMarshmallowOrLater()) {
        val allowDeviceCredentials = params.getBoolean("allowDeviceCredentials")
        val reactApplicationContext = reactApplicationContext
        val biometricManager = BiometricManager.from(reactApplicationContext)
        val canAuthenticate =
          biometricManager.canAuthenticate(getAllowedAuthenticators(allowDeviceCredentials))

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
          val resultMap = WritableNativeMap()
          resultMap.putBoolean("available", true)
          resultMap.putString("biometryType", "Biometrics")
          promise.resolve(resultMap)
        } else {
          val resultMap = WritableNativeMap()
          resultMap.putBoolean("available", false)

          when (canAuthenticate) {
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
              resultMap.putString("error", "BIOMETRIC_ERROR_NO_HARDWARE")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
              resultMap.putString("error", "BIOMETRIC_ERROR_HW_UNAVAILABLE")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
              resultMap.putString("error", "BIOMETRIC_ERROR_NONE_ENROLLED")
          }

          promise.resolve(resultMap)
        }
      } else {
        val resultMap = WritableNativeMap()
        resultMap.putBoolean("available", false)
        resultMap.putString("error", "Unsupported android version")
        promise.resolve(resultMap)
      }
    } catch (e: Exception) {
      promise.reject(
        "Error detecting biometrics availability: " + e.message,
        "Error detecting biometrics availability: " + e.message,
      )
    }
  }

  /**
   * Creates an RSA 2048 Keystore pair for `keyAlias`, replacing any existing
   * key for that alias. When `requireAuthentication` is true, shows a prompt
   * first; otherwise the key is created silently.
   */
  override fun createKeys(params: ReadableMap, promise: Promise) {
    if (!isCurrentSDKMarshmallowOrLater()) {
      promise.reject(
        "Cannot generate keys on android versions below 6.0",
        "Cannot generate keys on android versions below 6.0",
      )
      return
    }

    if (!getRequireAuthentication(params)) {
      generateAndResolvePublicKey(params, promise)
      return
    }

    UiThreadUtil.runOnUiThread {
      try {
        val promptMessage = getPromptMessage(params)
        val cancelButtonText = getCancelButtonText(params)
        val allowDeviceCredentials = params.hasKey("allowDeviceCredentials") &&
          !params.isNull("allowDeviceCredentials") &&
          params.getBoolean("allowDeviceCredentials")

        val authCallback = CreateKeysCallback(promise) {
          generateAndResolvePublicKey(params, promise)
        }
        val fragmentActivity = currentActivity as FragmentActivity
        val executor = Executors.newSingleThreadExecutor()
        val biometricPrompt = BiometricPrompt(fragmentActivity, executor, authCallback)

        biometricPrompt.authenticate(
          getPromptInfo(promptMessage, cancelButtonText, allowDeviceCredentials),
        )
      } catch (e: Exception) {
        promise.reject(
          "Error displaying local biometric prompt: " + e.message,
          "Error displaying local biometric prompt: " + e.message,
        )
      }
    }
  }

  /**
   * Writes a new RSA 2048 sign-only key into Android Keystore and resolves
   * the X.509 public key as standard Base64.
   */
  private fun generateAndResolvePublicKey(params: ReadableMap, promise: Promise) {
    try {
      val keyAlias = getKeyAlias(params)
      val requireAuthentication = getRequireAuthentication(params)
      deleteBiometricKey(keyAlias)
      val keyPairGenerator =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
      val keyGenParameterSpec = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
        .setUserAuthenticationRequired(requireAuthentication)
        .build()
      keyPairGenerator.initialize(keyGenParameterSpec)

      val keyPair = keyPairGenerator.generateKeyPair()
      val publicKey = keyPair.public
      val encodedPublicKey = publicKey.encoded
      var publicKeyString = Base64.encodeToString(encodedPublicKey, Base64.DEFAULT)
      publicKeyString = publicKeyString.replace("\r", "").replace("\n", "")

      val resultMap = WritableNativeMap()
      resultMap.putString("publicKey", publicKeyString)
      promise.resolve(resultMap)
    } catch (e: Exception) {
      val detail = e.message ?: ""
      val cause = e.cause
      val causeDetail = cause?.message ?: ""
      val missingBiometrics =
        detail.contains("At least one biometric must be enrolled") ||
          causeDetail.contains("At least one biometric must be enrolled") ||
          (cause is IllegalStateException && causeDetail.lowercase().contains("biometric"))

      if (missingBiometrics) {
        promise.reject(
          "ERROR_BIOMETRY_NOT_AVAILABLE",
          "At least one biometric must be enrolled",
          e,
        )
      } else {
        val message = if (detail.isEmpty()) {
          "Error generating public private keys"
        } else {
          "Error generating public private keys: $detail"
        }
        promise.reject("CREATE_KEYS_ERROR", message, e)
      }
    }
  }

  private fun isCurrentSDKMarshmallowOrLater(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
  }

  /** Deletes the Keystore entry for `keyAlias`. Resolves `keysDeleted: false` when none existed. */
  override fun deleteKeys(params: ReadableMap, promise: Promise) {
    val keyAlias = getKeyAlias(params)
    if (doesBiometricKeyExist(keyAlias)) {
      val deletionSuccessful = deleteBiometricKey(keyAlias)

      if (deletionSuccessful) {
        val resultMap = WritableNativeMap()
        resultMap.putBoolean("keysDeleted", true)
        promise.resolve(resultMap)
      } else {
        promise.reject(
          "Error deleting biometric key from keystore",
          "Error deleting biometric key from keystore",
        )
      }
    } else {
      val resultMap = WritableNativeMap()
      resultMap.putBoolean("keysDeleted", false)
      promise.resolve(resultMap)
    }
  }

  /**
   * Signs `payload` with SHA256withRSA. Silent keys sign immediately.
   * Biometric keys bind the Signature to [BiometricPrompt.CryptoObject] so
   * the private key is used only after a successful prompt.
   *
   * Rejects `ERROR_KEY_INVALIDATED` when the Keystore key was invalidated
   * after biometrics enrollment changed.
   */
  override fun createSignature(params: ReadableMap, promise: Promise) {
    if (isCurrentSDKMarshmallowOrLater()) {
      val keyAlias = getKeyAlias(params)
      val requireAuthentication = getRequireAuthentication(params)

      if (!requireAuthentication) {
        try {
          val payload = params.getString("payload")
          val signature = Signature.getInstance("SHA256withRSA")
          val keyStore = KeyStore.getInstance("AndroidKeyStore")
          keyStore.load(null)

          val privateKey = keyStore.getKey(keyAlias, null) as PrivateKey
          signature.initSign(privateKey)
          signature.update(payload!!.toByteArray())
          val signed = signature.sign()
          var signedString = Base64.encodeToString(signed, Base64.DEFAULT)
          signedString = signedString.replace("\r", "").replace("\n", "")

          val resultMap = WritableNativeMap()
          resultMap.putBoolean("success", true)
          resultMap.putString("signature", signedString)
          promise.resolve(resultMap)
        } catch (e: Exception) {
          promise.reject(
            "Error signing payload: " + e.message,
            "Error generating signature: " + e.message,
          )
        }
        return
      }

      UiThreadUtil.runOnUiThread {
        try {
          val promptMessage = getPromptMessage(params, "Authenticate to login")
          val payload = params.getString("payload")
          val cancelButtonText = params.getString("cancelButtonText")
          val allowDeviceCredentials = params.getBoolean("allowDeviceCredentials")

          val signature = Signature.getInstance("SHA256withRSA")
          val keyStore = KeyStore.getInstance("AndroidKeyStore")
          keyStore.load(null)

          val privateKey = keyStore.getKey(keyAlias, null) as PrivateKey
          signature.initSign(privateKey)

          // CryptoObject ties this Signature to the prompt so Keystore will
          // not sign until biometrics succeed.
          val cryptoObject = BiometricPrompt.CryptoObject(signature)

          val authCallback = CreateSignatureCallback(promise, payload!!)
          val fragmentActivity = currentActivity as FragmentActivity
          val executor = Executors.newSingleThreadExecutor()
          val biometricPrompt = BiometricPrompt(fragmentActivity, executor, authCallback)

          biometricPrompt.authenticate(
            getPromptInfo(promptMessage, cancelButtonText, allowDeviceCredentials),
            cryptoObject,
          )
        } catch (e: Exception) {
          if (isKeyPermanentlyInvalidated(e)) {
            promise.reject("ERROR_KEY_INVALIDATED", e.message, e)
          } else {
            promise.reject(
              "Error signing payload: " + e.message,
              "Error generating signature: " + e.message,
            )
          }
        }
      }
    } else {
      promise.reject(
        "Cannot generate keys on android versions below 6.0",
        "Cannot generate keys on android versions below 6.0",
      )
    }
  }

  /**
   * Builds the system biometric dialog. API 30+ forbids a negative button
   * when DEVICE_CREDENTIAL is allowed, so Cancel is only set otherwise.
   */
  private fun getPromptInfo(
    promptMessage: String,
    cancelButtonText: String?,
    allowDeviceCredentials: Boolean,
  ): BiometricPrompt.PromptInfo {
    val builder = BiometricPrompt.PromptInfo.Builder().setTitle(promptMessage)

    builder.setAllowedAuthenticators(getAllowedAuthenticators(allowDeviceCredentials))

    if (!allowDeviceCredentials || isCurrentSDK29OrEarlier()) {
      builder.setNegativeButtonText(cancelButtonText!!)
    }

    return builder.build()
  }

  /** BIOMETRIC_STRONG, plus device PIN/pattern/password when allowed on API 30+. */
  private fun getAllowedAuthenticators(allowDeviceCredentials: Boolean): Int {
    if (allowDeviceCredentials && !isCurrentSDK29OrEarlier()) {
      return BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
    return BiometricManager.Authenticators.BIOMETRIC_STRONG
  }

  private fun isCurrentSDK29OrEarlier(): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
  }

  /** Shows a biometric prompt without creating a signature. */
  override fun simplePrompt(params: ReadableMap, promise: Promise) {
    if (isCurrentSDKMarshmallowOrLater()) {
      UiThreadUtil.runOnUiThread {
        try {
          val promptMessage = params.getString("promptMessage")
          val cancelButtonText = params.getString("cancelButtonText")
          val allowDeviceCredentials = params.getBoolean("allowDeviceCredentials")

          val authCallback = SimplePromptCallback(promise)
          val fragmentActivity = currentActivity as FragmentActivity
          val executor = Executors.newSingleThreadExecutor()
          val biometricPrompt = BiometricPrompt(fragmentActivity, executor, authCallback)

          biometricPrompt.authenticate(
            getPromptInfo(promptMessage!!, cancelButtonText, allowDeviceCredentials),
          )
        } catch (e: Exception) {
          promise.reject(
            "Error displaying local biometric prompt: " + e.message,
            "Error displaying local biometric prompt: " + e.message,
          )
        }
      }
    } else {
      promise.reject(
        "Cannot display biometric prompt on android versions below 6.0",
        "Cannot display biometric prompt on android versions below 6.0",
      )
    }
  }

  /** Returns whether a Keystore entry exists for `keyAlias`. */
  override fun biometricKeysExist(params: ReadableMap, promise: Promise) {
    try {
      val doesBiometricKeyExist = doesBiometricKeyExist(getKeyAlias(params))
      val resultMap = WritableNativeMap()
      resultMap.putBoolean("keysExist", doesBiometricKeyExist)
      promise.resolve(resultMap)
    } catch (e: Exception) {
      promise.reject(
        "Error checking if biometric key exists: " + e.message,
        "Error checking if biometric key exists: " + e.message,
      )
    }
  }

  /** True when Android Keystore already has an entry for [keyAlias]. */
  private fun doesBiometricKeyExist(keyAlias: String): Boolean {
    return try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null)
      keyStore.containsAlias(keyAlias)
    } catch (e: Exception) {
      false
    }
  }

  private fun deleteBiometricKey(keyAlias: String): Boolean {
    return try {
      val keyStore = KeyStore.getInstance("AndroidKeyStore")
      keyStore.load(null)
      keyStore.deleteEntry(keyAlias)
      true
    } catch (e: Exception) {
      false
    }
  }

  /** Keystore alias from JS, or `biometric_key` when omitted. */
  private fun getKeyAlias(params: ReadableMap?): String {
    if (params != null && params.hasKey("keyAlias") && !params.isNull("keyAlias")) {
      val alias = params.getString("keyAlias")
      if (alias != null && alias.isNotEmpty()) {
        return alias
      }
    }
    return biometricKeyAlias
  }

  /** Defaults to `true` when JS omits `requireAuthentication`. */
  private fun getRequireAuthentication(params: ReadableMap?): Boolean {
    if (params != null &&
      params.hasKey("requireAuthentication") &&
      !params.isNull("requireAuthentication")
    ) {
      return params.getBoolean("requireAuthentication")
    }
    return true
  }

  private fun getPromptMessage(params: ReadableMap): String {
    return getPromptMessage(params, "Authenticate to continue")
  }

  private fun getPromptMessage(params: ReadableMap, fallback: String): String {
    if (params.hasKey("promptMessage") && !params.isNull("promptMessage")) {
      val promptMessage = params.getString("promptMessage")
      if (promptMessage != null && promptMessage.isNotEmpty()) {
        return promptMessage
      }
    }
    return fallback
  }

  /** Walks the cause chain for [KeyPermanentlyInvalidatedException]. */
  private fun isKeyPermanentlyInvalidated(error: Throwable?): Boolean {
    var current = error
    while (current != null) {
      if (current is KeyPermanentlyInvalidatedException) {
        return true
      }
      current = current.cause
    }
    return false
  }

  private fun getCancelButtonText(params: ReadableMap?): String {
    if (params != null &&
      params.hasKey("cancelButtonText") &&
      !params.isNull("cancelButtonText")
    ) {
      val cancelButtonText = params.getString("cancelButtonText")
      if (cancelButtonText != null && cancelButtonText.isNotEmpty()) {
        return cancelButtonText
      }
    }
    return "Cancel"
  }

  companion object {
    const val NAME = "DeviceBiometrics"
  }
}

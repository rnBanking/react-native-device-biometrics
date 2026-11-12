import Foundation
import LocalAuthentication
import Security

/// RSA 2048 keys in the Keychain, optionally gated by Face ID / Touch ID.
/// JS reaches this class through the ObjC++ Turbo Module in `DeviceBiometrics.mm`.
///
/// Distinct keys are addressed with `keyAlias` (the app uses `DEVICE` and
/// `BIOMETRIC`). The default tag is `com.rnBanking.biometricKey`.
@objc
public class DeviceBiometricsImpl: NSObject {
  /// Reports whether Face ID / Touch ID (or passcode, if allowed) can be evaluated.
  @objc
  public func isSensorAvailable(
    _ params: NSDictionary,
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String?, String?, Error?) -> Void
  ) {
    let context = LAContext()
    var laError: NSError?
    let allowDeviceCredentials = boolValue(params, "allowDeviceCredentials")
    var laPolicy: LAPolicy = .deviceOwnerAuthenticationWithBiometrics

    if allowDeviceCredentials {
      laPolicy = .deviceOwnerAuthentication
    }

    let canEvaluatePolicy = context.canEvaluatePolicy(laPolicy, error: &laError)

    if canEvaluatePolicy {
      resolve([
        "available": true,
        "biometryType": biometryType(from: context),
      ])
    } else {
      resolve([
        "available": false,
        "error": nsErrorString(laError),
      ])
    }
  }

  /// Creates an RSA 2048 Keychain pair for `keyAlias`, replacing any existing
  /// key for that alias. When `requireAuthentication` is true, shows Face ID
  /// first; otherwise the key is created silently.
  @objc
  public func createKeys(
    _ params: NSDictionary,
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String?, String?, Error?) -> Void
  ) {
    DispatchQueue.global(qos: .default).async {
      let requireAuthentication = self.requireAuthentication(from: params)

      let generateKeys = {
        self.generateKeys(params, resolve: resolve, reject: reject)
      }

      if !requireAuthentication {
        generateKeys()
        return
      }

      let promptMessage = self.stringValue(params, "promptMessage")
      let allowDeviceCredentials = self.boolValue(params, "allowDeviceCredentials")
      let authContext = LAContext()
      let policy: LAPolicy = allowDeviceCredentials
        ? .deviceOwnerAuthentication
        : .deviceOwnerAuthenticationWithBiometrics
      let reason =
        (promptMessage != nil && !(promptMessage?.isEmpty ?? true))
          ? promptMessage!
          : "Authenticate to continue"
      var policyError: NSError?

      if !authContext.canEvaluatePolicy(policy, error: &policyError) {
        reject(
          self.rejectCode(policyError?.code ?? 0),
          self.nsErrorString(policyError),
          nil
        )
        return
      }

      authContext.evaluatePolicy(policy, localizedReason: reason) { success, biometricError in
        if success {
          generateKeys()
        } else {
          let nsError = biometricError as NSError?
          reject(
            self.rejectCode(nsError?.code ?? 0),
            self.nsErrorString(nsError),
            nil
          )
        }
      }
    }
  }

  /// Deletes the Keychain item for `keyAlias`. Resolves `keysDeleted: false` when none existed.
  @objc
  public func deleteKeys(
    _ params: NSDictionary,
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String?, String?, Error?) -> Void
  ) {
    DispatchQueue.global(qos: .default).async {
      let biometricKeyExists = self.doesBiometricKeyExist(params)

      if biometricKeyExists {
        let status = self.deleteBiometricKey(params)

        if status == errSecSuccess {
          resolve(["keysDeleted": true])
        } else {
          let message =
            "Key not found: \(self.keychainErrorToString(status))"
          reject("deletion_error", message, nil)
        }
      } else {
        resolve(["keysDeleted": false])
      }
    }
  }

  /// Signs `payload` with RSA PKCS1 v1.5 SHA-256.
  /// On device, Keychain prompts via `kSecUseAuthenticationContext`.
  /// On Simulator that prompt is skipped, so Face ID is evaluated explicitly.
  @objc
  public func createSignature(
    _ params: NSDictionary,
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String?, String?, Error?) -> Void
  ) {
    DispatchQueue.global(qos: .default).async {
      let promptMessage = self.stringValue(params, "promptMessage")
      let payload = self.stringValue(params, "payload")
      let requireAuthentication = self.requireAuthentication(from: params)
      let allowDeviceCredentials = self.boolValue(params, "allowDeviceCredentials")

      let biometricKeyTag = self.biometricKeyTag(from: params)
      var query: [String: Any] = [
        kSecClass as String: kSecClassKey,
        kSecAttrApplicationTag as String: biometricKeyTag,
        kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
        kSecReturnRef as String: true,
      ]

      var authContext: LAContext?
      if requireAuthentication {
        let context = LAContext()
        let reason =
          (promptMessage != nil && !(promptMessage?.isEmpty ?? true))
            ? promptMessage!
            : "Authenticate to login"
        context.localizedReason = reason
        query[kSecUseAuthenticationContext as String] = context
        authContext = context
      }

      let performSign: (SecKey) -> Void = { privateKey in
        var error: Unmanaged<CFError>?
        let dataToSign = (payload ?? "").data(using: .utf8) as CFData?
        let signature = SecKeyCreateSignature(
          privateKey,
          .rsaSignatureMessagePKCS1v15SHA256,
          dataToSign ?? Data() as CFData,
          &error
        )

        if let signature = signature as Data? {
          resolve([
            "success": true,
            "signature": signature.base64EncodedString(options: []),
          ])
        } else {
          let nsError = error?.takeRetainedValue() as Error? as NSError?
          let message = "Signature error: \(self.nsErrorString(nsError))"
          reject(
            self.rejectCode(
              self.laErrorFromKeychainCode(nsError?.code ?? 0)
            ),
            message,
            nil
          )
        }
      }

      var privateKeyUnmanaged: CFTypeRef?
      let status = SecItemCopyMatching(query as CFDictionary, &privateKeyUnmanaged)

      if status != errSecSuccess {
        let message = "Key not found: \(self.keychainErrorToString(status))"
        if status == errSecUserCanceled || status == errSecAuthFailed {
          reject(
            self.rejectCode(self.laErrorFromKeychainCode(Int(status))),
            message,
            nil
          )
        } else {
          reject("storage_error", message, nil)
        }
        return
      }

      let privateKey = privateKeyUnmanaged as! SecKey

      #if targetEnvironment(simulator)
        // Simulator Keychain does not prompt through `kSecUseAuthenticationContext`.
        // Evaluate Face ID here so signing still requires biometrics.
        if requireAuthentication, let authContext {
          let policy: LAPolicy = allowDeviceCredentials
            ? .deviceOwnerAuthentication
            : .deviceOwnerAuthenticationWithBiometrics
          var policyError: NSError?
          let reason = authContext.localizedReason.isEmpty
            ? "Authenticate to login"
            : authContext.localizedReason

          if !authContext.canEvaluatePolicy(policy, error: &policyError) {
            reject(
              self.rejectCode(policyError?.code ?? 0),
              self.nsErrorString(policyError),
              nil
            )
            return
          }

          authContext.evaluatePolicy(policy, localizedReason: reason) { success, biometricError in
            if success {
              performSign(privateKey)
            } else {
              let nsError = biometricError as NSError?
              reject(
                self.rejectCode(nsError?.code ?? 0),
                self.nsErrorString(nsError),
                nil
              )
            }
          }
          return
        }
      #endif

      performSign(privateKey)
    }
  }

  /// Shows a Face ID / Touch ID prompt without creating a signature.
  /// User cancel resolves `{ success: false }`; other failures reject.
  @objc
  public func simplePrompt(
    _ params: NSDictionary,
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String?, String?, Error?) -> Void
  ) {
    DispatchQueue.global(qos: .default).async {
      let promptMessage = self.stringValue(params, "promptMessage") ?? ""
      let fallbackPromptMessage = self.stringValue(params, "fallbackPromptMessage")
      let allowDeviceCredentials = self.boolValue(params, "allowDeviceCredentials")

      let context = LAContext()
      var laPolicy: LAPolicy = .deviceOwnerAuthenticationWithBiometrics

      if allowDeviceCredentials {
        laPolicy = .deviceOwnerAuthentication
        context.localizedFallbackTitle = fallbackPromptMessage
      } else {
        context.localizedFallbackTitle = ""
      }

      context.evaluatePolicy(laPolicy, localizedReason: promptMessage) { success, biometricError in
        if success {
          resolve(["success": true])
        } else if (biometricError as NSError?)?.code == LAError.userCancel.rawValue {
          resolve([
            "success": false,
            "error": "User cancellation",
          ])
        } else {
          let nsError = biometricError as NSError?
          reject(
            self.rejectCode(nsError?.code ?? 0),
            self.nsErrorString(nsError),
            nil
          )
        }
      }
    }
  }

  /// Returns whether a Keychain key exists for `keyAlias`.
  @objc
  public func biometricKeysExist(
    _ params: NSDictionary,
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String?, String?, Error?) -> Void
  ) {
    DispatchQueue.global(qos: .default).async {
      resolve(["keysExist": self.doesBiometricKeyExist(params)])
    }
  }

  /// Writes a new RSA 2048 pair into the Keychain and resolves the SPKI
  /// public key as standard Base64. `.biometryAny` keeps the key valid after
  /// Face ID re-enrollment; `.userPresence` also accepts device passcode.
  private func generateKeys(
    _ params: NSDictionary,
    resolve: @escaping (Any?) -> Void,
    reject: @escaping (String?, String?, Error?) -> Void
  ) {
    let requireAuthentication = requireAuthentication(from: params)
    let biometricKeyTag = biometricKeyTag(from: params)
    var privateKeyAttrs: [String: Any] = [
      kSecAttrIsPermanent as String: true,
      kSecAttrApplicationTag as String: biometricKeyTag,
    ]
    var keyAttributes: [String: Any] = [
      kSecClass as String: kSecClassKey,
      kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
      kSecAttrKeySizeInBits as String: 2048,
    ]

    if requireAuthentication {
      var error: Unmanaged<CFError>?
      let allowDeviceCredentials = boolValue(params, "allowDeviceCredentials")
      var secCreateFlag: SecAccessControlCreateFlags = .biometryAny

      if allowDeviceCredentials {
        secCreateFlag = .userPresence
      }

      guard let sacObject = SecAccessControlCreateWithFlags(
        kCFAllocatorDefault,
        kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
        secCreateFlag,
        &error
      ), error == nil else {
        let errorString =
          "SecItemAdd can't create sacObject: \(nsErrorString(error?.takeRetainedValue() as Error? as NSError?))"
        reject("storage_error", errorString, nil)
        return
      }

      privateKeyAttrs[kSecUseAuthenticationUI as String] = kSecUseAuthenticationUIAllow
      privateKeyAttrs[kSecAttrAccessControl as String] = sacObject
    } else {
      privateKeyAttrs[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
    }

    keyAttributes[kSecPrivateKeyAttrs as String] = privateKeyAttrs

    _ = deleteBiometricKey(params)
    var genError: Unmanaged<CFError>?
    guard let privateKey = SecKeyCreateRandomKey(
      keyAttributes as CFDictionary,
      &genError
    ) else {
      let message =
        "Key generation error: \(nsErrorString(genError?.takeRetainedValue() as Error? as NSError?))"
      reject("storage_error", message, nil)
      return
    }

    guard let publicKey = SecKeyCopyPublicKey(privateKey) else {
      reject("storage_error", "Key generation error: (null)", nil)
      return
    }

    guard let publicKeyData = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else {
      reject("storage_error", "Key generation error: (null)", nil)
      return
    }

    let publicKeyDataWithHeader = addHeaderPublickey(publicKeyData)
    let publicKeyString = publicKeyDataWithHeader.base64EncodedString(options: [])
    resolve(["publicKey": publicKeyString])
  }

  /// Defaults to `true` when JS omits `requireAuthentication`.
  private func requireAuthentication(from params: NSDictionary) -> Bool {
    guard let value = params["requireAuthentication"], !(value is NSNull) else {
      return true
    }
    if let number = value as? NSNumber {
      return number.boolValue
    }
    return true
  }

  /// Keychain application tag from JS, or `com.rnBanking.biometricKey` when omitted.
  private func biometricKeyTag(from params: NSDictionary) -> Data {
    var biometricKeyAlias = stringValue(params, "keyAlias")
    if biometricKeyAlias == nil || biometricKeyAlias?.isEmpty == true {
      biometricKeyAlias = "com.rnBanking.biometricKey"
    }
    return biometricKeyAlias!.data(using: .utf8) ?? Data()
  }

  /// Checks existence without a prompt (`kSecUseAuthenticationUIFail`).
  /// `errSecInteractionNotAllowed` still means the key exists but needs Face ID.
  private func doesBiometricKeyExist(_ params: NSDictionary) -> Bool {
    let biometricKeyTag = biometricKeyTag(from: params)
    let searchQuery: [String: Any] = [
      kSecClass as String: kSecClassKey,
      kSecAttrApplicationTag as String: biometricKeyTag,
      kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
      kSecUseAuthenticationUI as String: kSecUseAuthenticationUIFail,
    ]

    let status = SecItemCopyMatching(searchQuery as CFDictionary, nil)
    return status == errSecSuccess || status == errSecInteractionNotAllowed
  }

  private func deleteBiometricKey(_ params: NSDictionary) -> OSStatus {
    let biometricKeyTag = biometricKeyTag(from: params)
    let deleteQuery: [String: Any] = [
      kSecClass as String: kSecClassKey,
      kSecAttrApplicationTag as String: biometricKeyTag,
      kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
    ]
    return SecItemDelete(deleteQuery as CFDictionary)
  }

  private func biometryType(from context: LAContext) -> String {
    if #available(iOS 11, *) {
      return context.biometryType == .faceID ? "FaceID" : "TouchID"
    }
    return "TouchID"
  }

  private func keychainErrorToString(_ error: OSStatus) -> String {
    var message = "\(error)"

    switch error {
    case errSecSuccess:
      message = "success"
    case errSecDuplicateItem:
      message = "error item already exists"
    case errSecItemNotFound:
      message = "error item not found"
    case errSecAuthFailed:
      message = "error item authentication failed"
    default:
      break
    }

    return message
  }

  /// Prepends the SubjectPublicKeyInfo (SPKI) header so the Base64 public key
  /// is X.509 DER, not a raw RSA modulus.
  private func addHeaderPublickey(_ publicKeyData: Data) -> Data {
    let encodedRSAEncryptionOID: [UInt8] = [
      0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86,
      0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00,
    ]

    let bitstringEncLength: Int
    if publicKeyData.count + 1 < 128 {
      bitstringEncLength = 1
    } else {
      bitstringEncLength = ((publicKeyData.count + 1) / 256) + 2
    }

    var builder = [UInt8](repeating: 0, count: 15)
    var encKey = Data()

    builder[0] = 0x30
    let sequenceLength =
      encodedRSAEncryptionOID.count + 2 + bitstringEncLength + publicKeyData.count
    let encodedSequenceLength = builder.withUnsafeMutableBufferPointer { buffer -> Int in
      self.encodeLength(buffer.baseAddress!.advanced(by: 1), sequenceLength)
    }
    encKey.append(contentsOf: builder.prefix(encodedSequenceLength + 1))
    encKey.append(contentsOf: encodedRSAEncryptionOID)

    builder = [UInt8](repeating: 0, count: 15)
    builder[0] = 0x03
    let encodedBitStringLength = builder.withUnsafeMutableBufferPointer { buffer -> Int in
      self.encodeLength(buffer.baseAddress!.advanced(by: 1), publicKeyData.count + 1)
    }
    builder[encodedBitStringLength + 1] = 0x00
    encKey.append(contentsOf: builder.prefix(encodedBitStringLength + 2))
    encKey.append(publicKeyData)

    return encKey
  }

  /// DER length encoding used by `addHeaderPublickey`.
  private func encodeLength(_ buf: UnsafeMutablePointer<UInt8>, _ length: Int) -> Int {
    var remaining = length
    if remaining < 128 {
      buf[0] = UInt8(remaining)
      return 1
    }

    let i = (remaining / 256) + 1
    buf[0] = UInt8(i + 0x80)
    for j in 0..<i {
      buf[i - j] = UInt8(remaining & 0xFF)
      remaining >>= 8
    }

    return i + 1
  }

  /// Maps `LAError` / Keychain auth failures to JS `BiometricPromptErrorCode`.
  private func rejectCode(_ code: Int) -> String {
    switch code {
    case LAError.userCancel.rawValue, LAError.userFallback.rawValue:
      return "ERROR_USER_CANCELED"
    case LAError.systemCancel.rawValue, LAError.appCancel.rawValue:
      return "ERROR_CANCELED"
    case LAError.authenticationFailed.rawValue:
      return "ERROR_AUTHENTICATION_FAILED"
    case LAError.biometryLockout.rawValue:
      return "ERROR_LOCKOUT"
    case LAError.biometryNotAvailable.rawValue,
         LAError.biometryNotEnrolled.rawValue,
         LAError.passcodeNotSet.rawValue:
      return "ERROR_BIOMETRY_NOT_AVAILABLE"
    default:
      return "BIOMETRIC_ERROR"
    }
  }

  /// Translates Keychain cancel / auth-failed statuses into `LAError` codes
  /// so `rejectCode` can map them.
  private func laErrorFromKeychainCode(_ code: Int) -> Int {
    if code == Int(errSecUserCanceled) {
      return LAError.userCancel.rawValue
    }
    if code == Int(errSecAuthFailed) {
      return LAError.authenticationFailed.rawValue
    }
    return code
  }

  private func boolValue(_ params: NSDictionary, _ key: String) -> Bool {
    guard let value = params[key], !(value is NSNull) else {
      return false
    }
    if let number = value as? NSNumber {
      return number.boolValue
    }
    return false
  }

  private func stringValue(_ params: NSDictionary, _ key: String) -> String? {
    guard let value = params[key], !(value is NSNull) else {
      return nil
    }
    return value as? String
  }

  private func nsErrorString(_ error: NSError?) -> String {
    if let error {
      return "\(error)"
    }
    return "(null)"
  }
}

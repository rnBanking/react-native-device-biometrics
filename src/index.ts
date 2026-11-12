/**
 * `react-native-device-biometrics` — Turbo Module for RSA keys in
 * iOS Keychain / Android Keystore, optionally gated by biometrics.
 *
 * Create one `DeviceBiometrics` instance per `allowDeviceCredentials` policy.
 * Distinct keys are addressed with `keyAlias` (the app uses `DEVICE` and
 * `BIOMETRIC`). Native rejects are wrapped as `BiometricPromptError`.
 *
 * @example
 * ```ts
 * import DeviceBiometrics from "react-native-device-biometrics";
 *
 * const biometrics = new DeviceBiometrics();
 *
 * const { publicKey } = await biometrics.createKeys({
 *   keyAlias: "BIOMETRIC",
 *   requireAuthentication: true,
 * });
 *
 * const { signature } = await biometrics.createSignature({
 *   keyAlias: "BIOMETRIC",
 *   payload: signingInput,
 *   promptMessage: "Confirm it is you",
 * });
 * ```
 */

import NativeDeviceBiometrics from "./NativeDeviceBiometrics";

/**
 * Hardware / OS biometry reported by `isSensorAvailable`.
 *
 * - `"TouchID"` — iOS Touch ID (`LABiometryTypeTouchID`)
 * - `"FaceID"` — iOS Face ID (`LABiometryTypeFaceID`)
 * - `"Biometrics"` — Android generic biometrics
 */
export type BiometryType = "TouchID" | "FaceID" | "Biometrics";

/** Options for constructing `DeviceBiometrics`. */
export interface DeviceBiometricsOptions {
  /**
   * When `true`, device passcode may satisfy prompts in addition to biometrics.
   *
   * @defaultValue false
   */
  allowDeviceCredentials?: boolean;
}

/** Result of `isSensorAvailable`. */
export interface IsSensorAvailableResult {
  /** Whether the requested policy can be evaluated on this device. */
  available: boolean;
  /**
   * Present when `available` is `true`.
   * Android always reports `"Biometrics"`.
   */
  biometryType?: BiometryType;
  /** Present when `available` is `false`. */
  error?: string;
}

/** Result of `createKeys`. */
export interface CreateKeysResult {
  /**
   * SPKI public key, standard Base64 (`+` `/` `=`).
   * Convert to Base64URL for compact JWT.
   */
  publicKey: string;
}

/** Options for `createKeys`. */
export interface CreateKeysOptions {
  /**
   * Keychain / Keystore alias.
   *
   * @defaultValue `com.rnBanking.biometricKey` on iOS, `biometric_key` on Android
   */
  keyAlias?: string;
  /**
   * When `true`, creating and later using the key requires Face ID / biometrics.
   * When `false`, the key is silent (no prompt).
   *
   * @defaultValue true
   */
  requireAuthentication?: boolean;
  /** Prompt shown when `requireAuthentication` is `true`. */
  promptMessage?: string;
  /**
   * Android negative-button label.
   *
   * @defaultValue `"Cancel"`
   */
  cancelButtonText?: string;
}

/** Result of `biometricKeysExist`. */
export interface BiometricKeysExistResult {
  /** `true` when a key exists for the requested alias. */
  keysExist: boolean;
}

/** Options that select a key by alias. */
export interface KeyAliasOptions {
  /**
   * Keychain / Keystore alias.
   *
   * @defaultValue `com.rnBanking.biometricKey` on iOS, `biometric_key` on Android
   */
  keyAlias?: string;
}

/** Result of `deleteKeys`. */
export interface DeleteKeysResult {
  /** `false` when no key existed for the alias. */
  keysDeleted: boolean;
}

/** Options for `createSignature`. */
export interface CreateSignatureOptions {
  /** Prompt shown when `requireAuthentication` is `true`. */
  promptMessage: string;
  /** UTF-8 payload to sign with SHA256withRSA / RSA PKCS1 v1.5 SHA-256. */
  payload: string;
  /**
   * Android negative-button label.
   *
   * @defaultValue `"Cancel"`
   */
  cancelButtonText?: string;
  /**
   * Keychain / Keystore alias.
   *
   * @defaultValue `com.rnBanking.biometricKey` on iOS, `biometric_key` on Android
   */
  keyAlias?: string;
  /**
   * When `true`, signing shows a biometric prompt. When `false`, signing is silent.
   *
   * @defaultValue true
   */
  requireAuthentication?: boolean;
}

/** Result of `createSignature`. */
export interface CreateSignatureResult {
  /** `true` when a signature was produced. */
  success: boolean;
  /** Standard Base64 signature. Convert to Base64URL for compact JWT. */
  signature?: string;
  /** Present when `success` is `false`. */
  error?: string;
}

/** Options for `simplePrompt`. */
export interface SimplePromptOptions {
  /** Title shown on the biometric prompt. */
  promptMessage: string;
  /**
   * iOS passcode fallback title when `allowDeviceCredentials` is `true`.
   *
   * @defaultValue `"Use Passcode"`
   */
  fallbackPromptMessage?: string;
  /**
   * Android negative-button label.
   *
   * @defaultValue `"Cancel"`
   */
  cancelButtonText?: string;
}

/** Result of `simplePrompt`. */
export interface SimplePromptResult {
  /** `true` when the user authenticated successfully. */
  success: boolean;
  /** Set when the user cancels (`"User cancellation"`). */
  error?: string;
}

/** iOS Touch ID (`LABiometryTypeTouchID`). */
export const TouchID: BiometryType = "TouchID";
/** iOS Face ID (`LABiometryTypeFaceID`). */
export const FaceID: BiometryType = "FaceID";
/** Android generic biometrics. */
export const Biometrics: BiometryType = "Biometrics";

/** Convenience map of `BiometryType` string constants. */
export const BiometryTypes = {
  TouchID,
  FaceID,
  Biometrics,
} as const;

/**
 * Native reject codes mapped onto `BiometricPromptError.code`.
 *
 * `BIOMETRIC_ERROR` is the fallback for unmapped native codes and missing
 * reject codes.
 */
export const BiometricPromptErrorCode = {
  /** Android: user tapped the negative button. */
  ERROR_NEGATIVE_BUTTON: "ERROR_NEGATIVE_BUTTON",
  /** User dismissed the prompt. */
  ERROR_USER_CANCELED: "ERROR_USER_CANCELED",
  /** System or app canceled the prompt. */
  ERROR_CANCELED: "ERROR_CANCELED",
  /** Too many failed attempts; retry later. */
  ERROR_LOCKOUT: "ERROR_LOCKOUT",
  /** Android: lockout until device credentials are used. */
  ERROR_LOCKOUT_PERMANENT: "ERROR_LOCKOUT_PERMANENT",
  /** iOS: Face ID / Touch ID did not match. */
  ERROR_AUTHENTICATION_FAILED: "ERROR_AUTHENTICATION_FAILED",
  /** Face ID Don't Allow, biometry not enrolled, or no biometrics enrolled. */
  ERROR_BIOMETRY_NOT_AVAILABLE: "ERROR_BIOMETRY_NOT_AVAILABLE",
  /** Android: Keystore key invalidated after biometrics enrollment changed. */
  ERROR_KEY_INVALIDATED: "ERROR_KEY_INVALIDATED",
  /** Unmapped native code or missing reject code. */
  BIOMETRIC_ERROR: "BIOMETRIC_ERROR",
} as const;

/** Union of known `BiometricPromptErrorCode` values. */
export type BiometricPromptErrorCode =
  (typeof BiometricPromptErrorCode)[keyof typeof BiometricPromptErrorCode];

/**
 * Thrown when a native biometric or keystore call fails.
 *
 * Cancel / lockout codes are typically handled silently in product UI.
 */
export class BiometricPromptError extends Error {
  /** Native reject code, or `BIOMETRIC_ERROR` when the code is missing. */
  code: string;

  /**
   * @param code - Native reject code, or `BIOMETRIC_ERROR`.
   * @param message - Optional native error message.
   */
  constructor(code: string, message?: string) {
    super(message);
    this.name = "BiometricPromptError";
    this.code = code;
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

const toBiometricPromptError = (error: unknown): BiometricPromptError => {
  if (error instanceof BiometricPromptError) {
    return error;
  }

  const nativeError = error as { code?: unknown; message?: unknown };
  return new BiometricPromptError(
    typeof nativeError?.code === "string"
      ? nativeError.code
      : BiometricPromptErrorCode.BIOMETRIC_ERROR,
    typeof nativeError?.message === "string" ? nativeError.message : undefined,
  );
};

/**
 * JS client for the DeviceBiometrics Turbo Module.
 *
 * Hold one instance per `allowDeviceCredentials` policy. Pass `keyAlias` on
 * each key operation so device keys and biometric keys stay independent.
 *
 * @example
 * ```ts
 * const biometrics = new DeviceBiometrics();
 *
 * const { publicKey } = await biometrics.createKeys({
 *   keyAlias: "BIOMETRIC",
 *   requireAuthentication: true,
 * });
 *
 * const { signature } = await biometrics.createSignature({
 *   keyAlias: "BIOMETRIC",
 *   payload: signingInput,
 *   promptMessage: "Confirm it is you",
 * });
 * ```
 */
export default class DeviceBiometrics {
  /**
   * When `true`, device passcode may satisfy prompts in addition to biometrics.
   * Set from `DeviceBiometricsOptions` at construction.
   */
  allowDeviceCredentials = false;

  /**
   * @param options - Instance policy. Omit to require biometrics only.
   */
  constructor(options?: DeviceBiometricsOptions) {
    this.allowDeviceCredentials = options?.allowDeviceCredentials ?? false;
  }

  /**
   * Reports whether biometrics (or passcode, if allowed) can be evaluated.
   *
   * Resolves with `available: false` instead of throwing when the sensor
   * cannot be used.
   *
   * @returns Sensor availability and, when present, the `BiometryType`.
   */
  isSensorAvailable(): Promise<IsSensorAvailableResult> {
    return NativeDeviceBiometrics.isSensorAvailable({
      allowDeviceCredentials: this.allowDeviceCredentials,
    }).catch((error: unknown) => {
      throw toBiometricPromptError(error);
    }) as Promise<IsSensorAvailableResult>;
  }

  /**
   * Creates an RSA 2048 key pair for `keyAlias`, replacing any existing key
   * for that alias only.
   *
   * @param options - Alias and whether creating/using the key requires biometrics.
   * @returns The SPKI public key as standard Base64.
   * @throws {BiometricPromptError} If the user cancels or biometrics cannot run.
   */
  createKeys(options?: CreateKeysOptions): Promise<CreateKeysResult> {
    return NativeDeviceBiometrics.createKeys({
      allowDeviceCredentials: this.allowDeviceCredentials,
      keyAlias: options?.keyAlias,
      requireAuthentication: options?.requireAuthentication ?? true,
      promptMessage: options?.promptMessage,
      cancelButtonText: options?.cancelButtonText ?? "Cancel",
    }).catch((error: unknown) => {
      throw toBiometricPromptError(error);
    }) as Promise<CreateKeysResult>;
  }

  /**
   * Returns whether a key exists for `keyAlias`.
   *
   * @param options - Alias to query. Omit to use the platform default alias.
   * @returns `{ keysExist: true }` when a key is present.
   * @throws {BiometricPromptError} If the keystore query fails.
   */
  biometricKeysExist(
    options?: KeyAliasOptions,
  ): Promise<BiometricKeysExistResult> {
    return NativeDeviceBiometrics.biometricKeysExist({
      keyAlias: options?.keyAlias,
    }).catch((error: unknown) => {
      throw toBiometricPromptError(error);
    }) as Promise<BiometricKeysExistResult>;
  }

  /**
   * Deletes the key for `keyAlias`.
   *
   * @param options - Alias to delete. Omit to use the platform default alias.
   * @returns `{ keysDeleted: false }` when no key existed.
   * @throws {BiometricPromptError} If deletion of an existing key fails.
   */
  deleteKeys(options?: KeyAliasOptions): Promise<DeleteKeysResult> {
    return NativeDeviceBiometrics.deleteKeys({
      keyAlias: options?.keyAlias,
    }).catch((error: unknown) => {
      throw toBiometricPromptError(error);
    }) as Promise<DeleteKeysResult>;
  }

  /**
   * Signs `payload` with the private key for `keyAlias`.
   *
   * @param options - Alias, UTF-8 payload, and prompt copy.
   * @returns `{ success, signature }` on success. `signature` is standard Base64.
   * @throws {BiometricPromptError} On cancel, lockout, or Android
   * `ERROR_KEY_INVALIDATED` after biometrics enrollment changed.
   */
  createSignature(
    options: CreateSignatureOptions,
  ): Promise<CreateSignatureResult> {
    return NativeDeviceBiometrics.createSignature({
      allowDeviceCredentials: this.allowDeviceCredentials,
      ...options,
      cancelButtonText: options.cancelButtonText ?? "Cancel",
      requireAuthentication: options.requireAuthentication ?? true,
    }).catch((error: unknown) => {
      throw toBiometricPromptError(error);
    }) as Promise<CreateSignatureResult>;
  }

  /**
   * Shows a biometric prompt without creating a signature.
   *
   * User cancel resolves `{ success: false, error: "User cancellation" }`.
   * Other failures throw `BiometricPromptError`.
   *
   * @param options - Prompt copy and optional Android cancel label.
   * @returns `{ success: true }` when the user authenticates.
   */
  simplePrompt(options: SimplePromptOptions): Promise<SimplePromptResult> {
    return NativeDeviceBiometrics.simplePrompt({
      allowDeviceCredentials: this.allowDeviceCredentials,
      ...options,
      cancelButtonText: options.cancelButtonText ?? "Cancel",
      fallbackPromptMessage: options.fallbackPromptMessage ?? "Use Passcode",
    }).catch((error: unknown) => {
      throw toBiometricPromptError(error);
    }) as Promise<SimplePromptResult>;
  }
}

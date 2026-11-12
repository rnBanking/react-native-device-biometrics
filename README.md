# react-native-device-biometrics

React Native Turbo Module for RSA keys in iOS Keychain and Android Keystore. It creates public/private key pairs that can be gated by biometrics (Face ID, Touch ID, fingerprint) or used silently. Those keys can later be used to create a cryptographic signature after proper authentication.

This package is designed to make server authentication using biometrics easier: enroll a public key, then sign a challenge on the device and verify it on the server.

## Features

- RSA 2048 key pairs in iOS Keychain / Android Keystore
- Multiple independent keys via `keyAlias` (for example a silent `DEVICE` key and a Face ID `BIOMETRIC` key)
- Optional biometric gate per key (`requireAuthentication`)
- Optional device passcode fallback (`allowDeviceCredentials`)
- SHA256withRSA / RSA PKCS1 v1.5 SHA-256 signatures
- Biometric prompt without signing (`simplePrompt`)
- Typed `BiometricPromptError` with stable reject codes
- New Architecture Turbo Module (Swift + Kotlin)

## Requirements

New Architecture only. This package is a codegen Turbo Module and does not include a legacy bridge.

Use React Native 0.76 or newer with the New Architecture enabled, or 0.82 or newer, where the New Architecture is the only architecture. Android `minSdkVersion` must be 24 or higher.

## Getting started

using either Yarn:

```sh
yarn add react-native-device-biometrics
```

or npm:

```sh
npm install react-native-device-biometrics --save
```

or pnpm (local checkout):

```sh
pnpm add file:../react-native-device-biometrics
```

Then install iOS pods:

```sh
cd ios && pod install
```

### Link / Autolinking

On React Native 0.60+ the CLI autolink feature links the module while building the app.

## Additional configuration

### iOS

Ensure that you have the `NSFaceIDUsageDescription` entry set in your React Native iOS project, or Face ID will not work properly. This description is presented to the user the first time a biometrics action is taken. If the user declines Face ID, `isSensorAvailable` reports biometrics as unavailable until the permission is allowed for the app.

```xml
<key>NSFaceIDUsageDescription</key>
<string>Authenticate with Face ID to sign in securely</string>
```

### Android

This package requires `minSdkVersion` 24 (Android 7.0) or higher.

The library manifest already includes `USE_BIOMETRIC` and `USE_FINGERPRINT`. Manifest merger adds them to the app; you do not need to copy them into the host `AndroidManifest.xml`. Both are normal permissions (no runtime prompt). `USE_FINGERPRINT` covers API 24–27; `USE_BIOMETRIC` covers API 28+.

## Usage

When a user enrolls in biometrics, a key pair is generated. The private key is stored securely on the device and the public key is sent to a server for registration. When the user wishes to authenticate, the user is prompted for biometrics, which unlocks the securely stored private key. Then a cryptographic signature is generated and sent to the server for verification. If verification succeeds, the server authorizes the user.

You can keep more than one key by passing `keyAlias` on each call. Creating a key replaces any existing key for **that alias only**.

```ts
import DeviceBiometrics, {
  BiometricPromptError,
  BiometricPromptErrorCode,
  BiometryTypes,
} from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();

const { available, biometryType } = await biometrics.isSensorAvailable();

if (available && biometryType === BiometryTypes.FaceID) {
  const { publicKey } = await biometrics.createKeys({
    keyAlias: "BIOMETRIC",
    requireAuthentication: true,
    promptMessage: "Confirm it is you",
  });

  sendPublicKeyToServer(publicKey);
}
```

Silent device-binding keys skip the prompt:

```ts
const { publicKey } = await biometrics.createKeys({
  keyAlias: "DEVICE",
  requireAuthentication: false,
});
```

## Constructor

```ts
const biometrics = new DeviceBiometrics({
  allowDeviceCredentials: false,
});
```

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `allowDeviceCredentials` | bool | `false` | When `true`, device passcode may satisfy prompts in addition to biometrics. Hold one instance per policy. |

## Constants

### TouchID (iOS only)

A constant for the Touch ID sensor type, evaluates to `'TouchID'`.

```ts
import DeviceBiometrics, { TouchID } from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();
const { biometryType } = await biometrics.isSensorAvailable();

if (biometryType === TouchID) {
  // fingerprint-specific UI
}
```

### FaceID (iOS only)

A constant for the Face ID sensor type, evaluates to `'FaceID'`.

```ts
import DeviceBiometrics, { FaceID } from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();
const { biometryType } = await biometrics.isSensorAvailable();

if (biometryType === FaceID) {
  // Face ID-specific UI
}
```

### Biometrics (Android only)

A constant for generic biometrics, evaluates to `'Biometrics'`.

```ts
import DeviceBiometrics, { Biometrics } from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();
const { biometryType } = await biometrics.isSensorAvailable();

if (biometryType === Biometrics) {
  // Android biometric UI
}
```

`BiometryTypes` is a convenience map of the same strings: `{ TouchID, FaceID, Biometrics }`.

## Methods

### isSensorAvailable()

Detects whether biometrics (or passcode, if `allowDeviceCredentials` is `true`) can be evaluated. Returns a `Promise` that resolves to an object with details about biometrics availability. This method resolves with `available: false` instead of throwing when the sensor cannot be used.

**Result Object**

| Property | Type | Description |
| --- | --- | --- |
| `available` | bool | Whether the requested policy can be evaluated on this device |
| `biometryType` | string | `TouchID`, `FaceID`, `Biometrics`, or `undefined` when unavailable. Android always reports `Biometrics`. |
| `error` | string | Why biometrics may not be available. `undefined` when there is no error. |

**Example**

```ts
import DeviceBiometrics, { BiometryTypes } from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();
const { available, biometryType } = await biometrics.isSensorAvailable();

if (available && biometryType === BiometryTypes.TouchID) {
  console.log("TouchID is supported");
} else if (available && biometryType === BiometryTypes.FaceID) {
  console.log("FaceID is supported");
} else if (available && biometryType === BiometryTypes.Biometrics) {
  console.log("Biometrics is supported");
} else {
  console.log("Biometrics not supported");
}
```

### createKeys(options?)

Generates a public/private RSA 2048 key pair and stores it in the device keystore under `keyAlias`. Replaces any existing key for that alias only. When `requireAuthentication` is `true` (default), creating the key shows a biometric prompt. Returns a `Promise` that resolves to the public key.

Throws `BiometricPromptError` if the user cancels or biometrics cannot run.

**Options Object**

| Parameter | Type | Description | iOS | Android |
| --- | --- | --- | --- | --- |
| `keyAlias` | string | Keychain / Keystore alias. Defaults to `com.rnBanking.biometricKey` on iOS and `biometric_key` on Android | ✔ | ✔ |
| `requireAuthentication` | bool | When `true`, creating and later using the key requires biometrics. When `false`, the key is silent. Defaults to `true` | ✔ | ✔ |
| `promptMessage` | string | Prompt shown when `requireAuthentication` is `true` | ✔ | ✔ |
| `cancelButtonText` | string | Negative-button label. Defaults to `Cancel` | ✖ | ✔ |

**Result Object**

| Property | Type | Description |
| --- | --- | --- |
| `publicKey` | string | SPKI public key as standard Base64 (`+` `/` `=`). Convert to Base64URL for compact JWT. |

**Example**

`createKeys` throws `BiometricPromptError` when the user cancels or biometrics cannot run. Cancel and "not available" are usually handled in the UI. Other codes should surface.

```ts
import DeviceBiometrics, {
  BiometricPromptError,
  BiometricPromptErrorCode,
} from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();

try {
  const { publicKey } = await biometrics.createKeys({
    keyAlias: "BIOMETRIC",
    requireAuthentication: true,
    promptMessage: "Confirm fingerprint",
  });

  sendPublicKeyToServer(publicKey);
} catch (error) {
  if (
    error instanceof BiometricPromptError &&
    (error.code === BiometricPromptErrorCode.ERROR_USER_CANCELED ||
      error.code === BiometricPromptErrorCode.ERROR_NEGATIVE_BUTTON)
  ) {
    return;
  }

  if (
    error instanceof BiometricPromptError &&
    error.code === BiometricPromptErrorCode.ERROR_BIOMETRY_NOT_AVAILABLE
  ) {
    // Face ID was denied, or no biometrics are enrolled.
    return;
  }

  throw error;
}
```

### biometricKeysExist(options?)

Detects whether a key already exists for `keyAlias`. Returns a `Promise` that resolves to an object indicating details about the keys.

**Options Object**

| Parameter | Type | Description | iOS | Android |
| --- | --- | --- | --- | --- |
| `keyAlias` | string | Alias to query. Omit to use the platform default alias | ✔ | ✔ |

**Result Object**

| Property | Type | Description |
| --- | --- | --- |
| `keysExist` | bool | `true` when a key exists for the requested alias |

**Example**

```ts
import DeviceBiometrics from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();
const { keysExist } = await biometrics.biometricKeysExist({
  keyAlias: "BIOMETRIC",
});

if (keysExist) {
  console.log("Keys exist");
} else {
  console.log("Keys do not exist or were deleted");
}
```

### deleteKeys(options?)

Deletes the key for `keyAlias`. Returns a `Promise` that resolves to an object indicating details about the deletion. Resolves `keysDeleted: false` when no key existed. Other aliases are left untouched.

**Options Object**

| Parameter | Type | Description | iOS | Android |
| --- | --- | --- | --- | --- |
| `keyAlias` | string | Alias to delete. Omit to use the platform default alias | ✔ | ✔ |

**Result Object**

| Property | Type | Description |
| --- | --- | --- |
| `keysDeleted` | bool | `false` when no key existed for the alias |

**Example**

```ts
import DeviceBiometrics from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();
const { keysDeleted } = await biometrics.deleteKeys({ keyAlias: "BIOMETRIC" });

if (keysDeleted) {
  console.log("Successful deletion");
} else {
  console.log("Unsuccessful deletion because there were no keys to delete");
}
```

### createSignature(options)

Prompts the user for biometrics (unless `requireAuthentication` is `false`) in order to use the private key, then generates an RSA PKCS#1 v1.5 SHA-256 signature over `payload`. Returns a `Promise` that resolves to an object with details about the signature.

Throws `BiometricPromptError` on cancel, lockout, or Android `ERROR_KEY_INVALIDATED` after biometrics enrollment changed (for example adding a new fingerprint).

**Options Object**

| Parameter | Type | Description | iOS | Android |
| --- | --- | --- | --- | --- |
| `promptMessage` | string | Message displayed in the biometric prompt when `requireAuthentication` is `true` | ✔ | ✔ |
| `payload` | string | UTF-8 data to sign | ✔ | ✔ |
| `keyAlias` | string | Alias of the key to sign with | ✔ | ✔ |
| `requireAuthentication` | bool | When `true`, signing shows a biometric prompt. When `false`, signing is silent. Defaults to `true` | ✔ | ✔ |
| `cancelButtonText` | string | Negative-button label. Defaults to `Cancel` | ✖ | ✔ |

**Result Object**

| Property | Type | Description |
| --- | --- | --- |
| `success` | bool | `true` when a signature was produced |
| `signature` | string | Standard Base64 signature. Convert to Base64URL for compact JWT. `undefined` if unsuccessful. |
| `error` | string | Why signature creation failed. `undefined` if there is no error. |

**Example**

```ts
import DeviceBiometrics from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();
const epochTimeSeconds = Math.round(new Date().getTime() / 1000).toString();
const payload = `${epochTimeSeconds}some message`;

const { success, signature } = await biometrics.createSignature({
  keyAlias: "BIOMETRIC",
  promptMessage: "Sign in",
  payload,
});

if (success && signature) {
  verifySignatureWithServer(signature, payload);
}
```

Silent signing with a device key:

```ts
const { signature } = await biometrics.createSignature({
  keyAlias: "DEVICE",
  requireAuthentication: false,
  promptMessage: "",
  payload,
});
```

### simplePrompt(options)

Prompts the user for biometrics without creating a signature. Returns a `Promise` that resolves if the user authenticates or cancels. Other failures throw `BiometricPromptError`.

**NOTE:** This only validates a user's biometrics. Do not use it to log a user in or authenticate with a server. Use `createSignature` for that. Use `simplePrompt` only to gate in-app actions.

User cancel resolves `{ success: false, error: "User cancellation" }`.

**Options Object**

| Parameter | Type | Description | iOS | Android |
| --- | --- | --- | --- | --- |
| `promptMessage` | string | Message displayed in the biometric prompt | ✔ | ✔ |
| `fallbackPromptMessage` | string | Passcode fallback title when `allowDeviceCredentials` is `true`. Defaults to `Use Passcode` | ✔ | ✖ |
| `cancelButtonText` | string | Negative-button label. Defaults to `Cancel` | ✖ | ✔ |

**Result Object**

| Property | Type | Description |
| --- | --- | --- |
| `success` | bool | `true` when the user authenticated. `false` when the user cancelled. |
| `error` | string | `"User cancellation"` when the user cancelled. `undefined` otherwise. |

**Example**

```ts
import DeviceBiometrics from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();

try {
  const { success } = await biometrics.simplePrompt({
    promptMessage: "Confirm fingerprint",
  });

  if (success) {
    console.log("successful biometrics provided");
  } else {
    console.log("user cancelled biometric prompt");
  }
} catch {
  console.log("biometrics failed");
}
```

## Errors

Native rejects are wrapped as `BiometricPromptError`. The `code` field is one of `BiometricPromptErrorCode`:

| Code | Platform | Description |
| --- | --- | --- |
| `ERROR_NEGATIVE_BUTTON` | Android | User tapped the negative button |
| `ERROR_USER_CANCELED` | both | User dismissed the prompt |
| `ERROR_CANCELED` | both | System or app canceled the prompt |
| `ERROR_LOCKOUT` | both | Too many failed attempts; retry later |
| `ERROR_LOCKOUT_PERMANENT` | Android | Lockout until device credentials are used |
| `ERROR_AUTHENTICATION_FAILED` | iOS | Face ID / Touch ID did not match |
| `ERROR_BIOMETRY_NOT_AVAILABLE` | both | Face ID Don't Allow, biometry not enrolled, or no biometrics enrolled |
| `ERROR_KEY_INVALIDATED` | Android | Keystore key invalidated after biometrics enrollment changed |
| `BIOMETRIC_ERROR` | both | Unmapped native code or missing reject code |

Cancel and lockout codes are typically handled silently in product UI.

`createKeys` rejects when enrollment cannot finish. `createSignature` rejects when an existing key cannot be used. On Android, `ERROR_KEY_INVALIDATED` only comes from `createSignature`, after the user changes enrolled biometrics.

```ts
import DeviceBiometrics, {
  BiometricPromptError,
  BiometricPromptErrorCode,
} from "react-native-device-biometrics";

const biometrics = new DeviceBiometrics();

try {
  const { publicKey } = await biometrics.createKeys({
    keyAlias: "BIOMETRIC",
    requireAuthentication: true,
    promptMessage: "Confirm it is you",
  });

  sendPublicKeyToServer(publicKey);
} catch (error) {
  if (
    error instanceof BiometricPromptError &&
    (error.code === BiometricPromptErrorCode.ERROR_USER_CANCELED ||
      error.code === BiometricPromptErrorCode.ERROR_NEGATIVE_BUTTON)
  ) {
    return;
  }

  if (
    error instanceof BiometricPromptError &&
    error.code === BiometricPromptErrorCode.ERROR_BIOMETRY_NOT_AVAILABLE
  ) {
    return;
  }

  throw error;
}

try {
  await biometrics.createSignature({
    keyAlias: "BIOMETRIC",
    payload: signingInput,
    promptMessage: "Confirm it is you",
  });
} catch (error) {
  if (
    error instanceof BiometricPromptError &&
    error.code === BiometricPromptErrorCode.ERROR_USER_CANCELED
  ) {
    return;
  }

  if (
    error instanceof BiometricPromptError &&
    error.code === BiometricPromptErrorCode.ERROR_KEY_INVALIDATED
  ) {
    await biometrics.deleteKeys({ keyAlias: "BIOMETRIC" });
    // re-enroll
  }

  throw error;
}
```

## Troubleshooting

- Android Keystore keys created with `requireAuthentication: true` are invalidated when the user adds or removes a biometric. `createSignature` then throws `ERROR_KEY_INVALIDATED`. Delete the alias and enroll again.
- iOS keys use `.biometryAny`, so Face ID / Touch ID re-enrollment does not invalidate the key.
- On iOS Simulator, Face ID is evaluated explicitly before signing so the prompt still appears. Enable Simulator **Features → Face ID**.
- Public keys and signatures are standard Base64. Compact JWT needs Base64URL (`+`/`/`/`=` → `-`/`_` and no padding).
- `createKeys` for an alias replaces only that alias. A `DEVICE` key and a `BIOMETRIC` key can coexist.

/**
 * Codegen spec for the DeviceBiometrics Turbo Module.
 *
 * Parameter and result shapes are `UnsafeObject` so native platforms keep
 * flexible dictionaries. App code should import the public client from
 * `./index` rather than this module.
 *
 * @internal
 */

import { TurboModuleRegistry, type TurboModule } from "react-native";

import type { UnsafeObject } from "react-native/Libraries/Types/CodegenTypes";

export interface Spec extends TurboModule {
  isSensorAvailable(params: UnsafeObject): Promise<UnsafeObject>;
  createKeys(params: UnsafeObject): Promise<UnsafeObject>;
  deleteKeys(params: UnsafeObject): Promise<UnsafeObject>;
  biometricKeysExist(params: UnsafeObject): Promise<UnsafeObject>;
  createSignature(params: UnsafeObject): Promise<UnsafeObject>;
  simplePrompt(params: UnsafeObject): Promise<UnsafeObject>;
}

export default TurboModuleRegistry.getEnforcing<Spec>("DeviceBiometrics");

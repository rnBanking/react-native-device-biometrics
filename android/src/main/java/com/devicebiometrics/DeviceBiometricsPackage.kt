package com.devicebiometrics

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import java.util.HashMap

/** Registers the DeviceBiometrics Turbo Module with React Native autolinking. */
class DeviceBiometricsPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == DeviceBiometricsModule.NAME) {
      DeviceBiometricsModule(reactContext)
    } else {
      null
    }
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    return ReactModuleInfoProvider {
      val moduleInfos: MutableMap<String, ReactModuleInfo> = HashMap()
      moduleInfos[DeviceBiometricsModule.NAME] = ReactModuleInfo(
        DeviceBiometricsModule.NAME,
        DeviceBiometricsModule.NAME,
        false,
        false,
        false,
        true,
      )
      moduleInfos
    }
  }
}

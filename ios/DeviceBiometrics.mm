#import "DeviceBiometrics.h"
#import "DeviceBiometrics-Swift.h"

// ObjC++ adapter required by React Native codegen. Each method forwards to
// the Swift implementation; `getTurboModule` registers the JSI spec.

@implementation DeviceBiometrics {
  DeviceBiometricsImpl *impl;
}

- (instancetype)init {
  if (self = [super init]) {
    impl = [DeviceBiometricsImpl new];
  }
  return self;
}

- (void)isSensorAvailable:(NSDictionary *)params
                  resolve:(RCTPromiseResolveBlock)resolve
                   reject:(RCTPromiseRejectBlock)reject {
  [impl isSensorAvailable:params resolve:resolve reject:reject];
}

- (void)createKeys:(NSDictionary *)params
           resolve:(RCTPromiseResolveBlock)resolve
            reject:(RCTPromiseRejectBlock)reject {
  [impl createKeys:params resolve:resolve reject:reject];
}

- (void)deleteKeys:(NSDictionary *)params
           resolve:(RCTPromiseResolveBlock)resolve
            reject:(RCTPromiseRejectBlock)reject {
  [impl deleteKeys:params resolve:resolve reject:reject];
}

- (void)biometricKeysExist:(NSDictionary *)params
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject {
  [impl biometricKeysExist:params resolve:resolve reject:reject];
}

- (void)createSignature:(NSDictionary *)params
                resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject {
  [impl createSignature:params resolve:resolve reject:reject];
}

- (void)simplePrompt:(NSDictionary *)params
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject {
  [impl simplePrompt:params resolve:resolve reject:reject];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params {
  return std::make_shared<facebook::react::NativeDeviceBiometricsSpecJSI>(
      params);
}

+ (NSString *)moduleName {
  return @"DeviceBiometrics";
}

@end

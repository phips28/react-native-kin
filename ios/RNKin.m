#import "React/RCTBridgeModule.h"

@interface RCT_EXTERN_MODULE(RNKin, NSObject)

// now expose all functions to JS
RCT_EXTERN_METHOD(increment)
RCT_EXTERN_METHOD(
                  decrement: (NSDictionary *)options
                  resolver: (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                  )
@end

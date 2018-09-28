#import "RNKin.h"

@interface RCT_EXTERN_MODULE(RNKinSwift, NSObject)
RCT_EXTERN_METHOD(logi)
@end

@implementation RNKin

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

RCT_EXPORT_MODULE()

- (NSDictionary *)constantsToExport
{
  return @{ @"firstDayOfTheWeek": @"Monday" };
}

/**
 * Initialize a new kin instance or ignore if currently exists.
 * @return
 */
RCT_EXPORT_METHOD(initializeApp:
    (NSString *) appName
            options:
            (NSDictionary *) options
            callback:
            (RCTResponseSenderBlock) callback) {

}

RCT_EXPORT_METHOD(log:(NSString *)any) {
    NSLog(@"Log: %@", any);
}

RCT_EXPORT_METHOD(isAvailable:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    resolve(@(YES));
}


@end

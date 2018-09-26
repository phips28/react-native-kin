
#import "RNKin.h"
#import <KinEcosystem/KinEcosystem>

@implementation RNKin

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

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

    dispatch_sync(dispatch_get_main_queue(), ^{
        FIRApp *existingApp = [FIRApp appNamed:appName];

        if (!existingApp) {
            FIROptions *firOptions = [[FIROptions alloc] initWithGoogleAppID:[options valueForKey:@"appId"] GCMSenderID:[options valueForKey:@"messagingSenderId"]];

            firOptions.APIKey = [options valueForKey:@"apiKey"];
            firOptions.projectID = [options valueForKey:@"projectId"];
            firOptions.clientID = [options valueForKey:@"clientId"];
            firOptions.trackingID = [options valueForKey:@"trackingId"];
            firOptions.databaseURL = [options valueForKey:@"databaseURL"];
            firOptions.storageBucket = [options valueForKey:@"storageBucket"];
            firOptions.androidClientID = [options valueForKey:@"androidClientId"];
            firOptions.deepLinkURLScheme = [options valueForKey:@"deepLinkURLScheme"];
            firOptions.bundleID = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleIdentifier"];

            [FIRApp configureWithName:appName options:firOptions];
        }

        callback(@[[NSNull null], @{@"result": @"success"}]);
    });
}


@end

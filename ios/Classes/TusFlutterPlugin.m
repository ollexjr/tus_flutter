#import "TusFlutterPlugin.h"
#if __has_include(<tus_flutter/tus_flutter-Swift.h>)
#import <tus_flutter/tus_flutter-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "tus_flutter-Swift.h"
#endif

@implementation TusFlutterPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftTusFlutterPlugin registerWithRegistrar:registrar];
}
@end

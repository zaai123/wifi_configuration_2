#import "WifiConfigurationPlugin.h"
#import <wifi_configuration_2/wifi_configuration_2-Swift.h>

@implementation WifiConfigurationPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftWifiConfigurationPlugin registerWithRegistrar:registrar];
}
@end

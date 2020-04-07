## wifi_configuration_2

Wifi plugin for flutter 

## Getting Started

This plugin is a updated version of wifi_configuration
This plugin allows Flutter apps to get available WifiNetwork list where you can get almost all the information about AP accessing to WifiNetwork object,
user can connect to wifi with ssid and password.
This plugin works only for Android.
iOS will be released later.


Sample usage to check current status:



Note :-   This plugin requires the location permission to auto enable the wifi if android version is above 9.0.


For Android : -
Add below Permissions to your manifist.xml file -
```dart
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-feature android:name="android.hardware.wifi" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Import package
```dart
import 'wifi_configuration_2.dart';
```

Get WIFI network list

````dart
 wifiNetworkList = await WifiConfiguration.getWifiList();
    setState(() {});
````

Check your connection is slow or fast
```dart
WifiConfiguration.isConnectionFast().then((value){
                          print('Connection type: ${value.toString()}');
                        });
```

Check your connection type
````dart
  WifiConfiguration.getConnectionType().then((value){
                          print('Connection type: ${value.toString()}');
                        });
````

Enable wifi
````dart
 WifiConfiguration.enableWifi();
````

Disable Wifi
````dart
WifiConfiguration.disableWifi();
````

## Check signal level of every network
# ListView example of wifiNetworks
# SignalLevel = 5 is maximum
# SignalLevel = 0 is minimum
```dart
ListView.builder(
                      itemBuilder: (context, index) {
                        WifiNetwork wifiNetwork = wifiNetworkList[index];
                        return ListTile(
                          leading: Text(wifiNetwork.signalLevel),
                          title: Text(wifiNetwork.ssid),
                          subtitle: Text(wifiNetwork.bssid),
                        );
                      },
                      itemCount: wifiNetworkList.length,
                    ),
```

```dart
    WifiConnectionStatus connectionStatus = await WifiConfiguration.connectToWifi("ssidName", "passName", "your android packagename");
    //This will return state of a connection
    //Package name is required to redirect user to application permission settings page to let user allow location permission
    //in case connecting with wifi
  
  
        switch (connectionStatus) {
              case WifiConnectionStatus.connected:
                print("connected");
                break;
        
              case WifiConnectionStatus.alreadyConnected:
                print("alreadyConnected");
                break;
        
              case WifiConnectionStatus.notConnected:
                print("notConnected");
                break;
        
              case WifiConnectionStatus.platformNotSupported:
                print("platformNotSupported");
                break;
        
              case WifiConnectionStatus.profileAlreadyInstalled:
                print("profileAlreadyInstalled");
                break;
        
              case WifiConnectionStatus.locationNotAllowed:
                print("locationNotAllowed");
                break;
            }
  
  
        var listAvailableWifi = await WifiConfiguration.getWifiList();
          //If wifi is available then device will get connected
          //In case of ios you will not get list of connected wifi an empty list will be available
          //As Apple does not allow to scan the available hotspot list
          //If you try to access with private api's then apple will reject the app
  
  
        bool isConnected = await WifiConfiguration.isConnectedToWifi("ssidName");
        //to get status if device connected to some wifi
        
        String isConnected = await WifiConfiguration.connectedToWifi();
                //to get current connected wifi name
        
```
If user has not aloowed the location permission for this app then it will ask everytime app try to connect to wifi for the location permission.


When you use connection on iOS (iOS 11 and above only)

1. 'Build Phases' -> 'Link Binay With Libraries' add 'NetworkExtension.framework'

2. in 'Capabilities' open 'Hotspot Configuration'

3. If you device is iOS12, in 'Capabilities' open 'Access WiFi Information'

If you want to use Wifi.list on iOS,



For help getting started with Flutter, view our 
[online documentation](https://flutter.dev/docs), which offers tutorials, 
samples, guidance on mobile development, and a full API reference.
### Sponsored by Jaaga Soft


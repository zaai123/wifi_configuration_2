import 'package:flutter/material.dart';
import 'package:wifi_configuration_2/wifi_configuration_2.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

//enum wifiStatus {
//  conected,
//alreadyConnected,.
//notConnected ,
//platformNotSupported,
//profileAlreadyInstalled,
//
//}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';

  @override
  void initState() {
    super.initState();
    getConnectionState();

  }

  void getConnectionState() async {
    var listAvailableWifi = await WifiConfiguration.getWifiList();

    print("get wifi list : " + listAvailableWifi.toString());
    WifiConnectionStatus connectionStatus =
        await WifiConfiguration.connectToWifi(
            "DarkBe@rs", "DarkBe@rs", "com.example.wifi_configuration_example");
    print("is Connected : ${connectionStatus}");
//
//
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
//
//    bool isConnected = await WifiConfiguration.isConnectedToWifi("DBWSN5");
    // String connectionState = await WifiConfiguration.connectedToWifi();
    //   print("coneection status ${connectionState}");
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: FlatButton(
            color: Colors.red,
            child: Text("connect"),
            onPressed: () async {
              WifiConnectionObject connectionStatus =
                  await WifiConfiguration.connectedToWifi();
              print("Ip address : ${connectionStatus.ip}");
              WifiConfiguration.getConnectionType().then((value){
                print('Connection type: ${value.toString()}');
              });
              WifiConfiguration.isConnectionFast().then((value){
                print('Is connection fast: ${value.toString()}');
              });

            },
          ),
        ),
      ),
    );
  }
}

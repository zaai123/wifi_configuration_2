import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

enum WifiConnectionStatus {
  connected,
  alreadyConnected,
  notConnected,
  platformNotSupported,
  profileAlreadyInstalled,
  locationNotAllowed,
}

class WifiConfiguration {
  static const MethodChannel _channel =
      const MethodChannel('wifi_configuration');

   Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');

    return version;
  }

   Future<WifiConnectionStatus> connectToWifi(
      String ssid, String password, String packageName) async {
    final String isConnected = await _channel.invokeMethod(
        'connectToWifi', <String, dynamic>{
      "ssid": ssid,
      "password": password,
      "packageName": packageName
    });
    switch (isConnected) {
      case "connected":
        return WifiConnectionStatus.connected;
        break;

      case "alreadyConnected":
        return WifiConnectionStatus.alreadyConnected;
        break;

      case "notConnected":
        return WifiConnectionStatus.notConnected;
        break;

      case "platformNotSupported":
        return WifiConnectionStatus.platformNotSupported;
        break;

      case "profileAlreadyInstalled":
        return WifiConnectionStatus.profileAlreadyInstalled;
        break;

      case "locationNotAllowed":
        return WifiConnectionStatus.locationNotAllowed;
        break;
    }
  }

   Future<List<dynamic>> getWifiList() async {
    final List<dynamic> wifiList = await _channel.invokeMethod('getWifiList');
    //return wifiList;
    print('Wifi list length: ${wifiList.length.toString()}');
    List<WifiNetwork> wifiNetworkList =
        wifiList.map((i) => WifiNetwork.fromMap(i)).toList();
    //print('ssid: ${wifiNetworkList[0].ssid.toString()}');
    return wifiNetworkList;
  }

   Future<bool> isConnectedToWifi(String ssid) async {
    final bool isConnected = await _channel
        .invokeMethod('isConnectedToWifi', <String, dynamic>{"ssid": ssid});
    return isConnected;
  }

   Future<WifiConnectionObject> connectedToWifi() async {
    final Map<dynamic, dynamic> connectedWifiMap =
        await _channel.invokeMethod('connectedToWifi');
    WifiConnectionObject wifiConnectionObject =
        WifiConnectionObject.fromMap(connectedWifiMap);
    print('ip: ${wifiConnectionObject.ip}');
    return wifiConnectionObject;
  }

   Future<ConnectionType> getConnectionType() async {
    final int connection = await _channel.invokeMethod('getConnectionType');
    //print('Connection: ${connection.toString()}');
    return connection == 0
        ? ConnectionType.WIFI
        : connection == 1
            ? ConnectionType.MOBILE
            : ConnectionType.NOT_CONNECTED;
  }

   Future<bool> isConnectionFast() async {
    final int checkConnection = await _channel.invokeMethod('isConnectionFast');
    return checkConnection == 1 ? true : false;
  }

   Future<void> enableWifi() async {
    _channel.invokeMethod('enableWifi');
  }

   Future<void> disableWifi() async {
    _channel.invokeMethod('disableWifi');
  }

   Future<bool> checkConnection() async {
    final bool isConnected = await _channel.invokeMethod('checkConnection');
    return isConnected;
  }

   Future<bool> isWifiEnabled() async{
    final bool isEnabled = await _channel.invokeMethod('isWifiEnabled');
    return isEnabled;
  }
}

enum ConnectionType { WIFI, MOBILE, NOT_CONNECTED }

class WifiNetwork {
  String ssid;
  String bssid;
  String frequency;
  String level;
  String security;
  String signalLevel;

  WifiNetwork(
      {this.ssid,
      this.bssid,
      this.frequency,
      this.level,
      this.security,
      this.signalLevel});

  factory WifiNetwork.fromMap(Map<dynamic, dynamic> map) {
    return WifiNetwork(
        bssid: map['bssid'],
        frequency: map['frequency'],
        level: map['level'],
        security: map['security'],
        ssid: map['ssid'],
        signalLevel: map['signal_level']);
  }
}

class WifiConnectionObject {
  String ip;
  String bssid;
  String frequency;
  String linkSpeed;
  String networkId;
  String ssid;
  InternetAddress internetAddress;

  WifiConnectionObject(
      {this.ip,
      this.bssid,
      this.frequency,
      this.linkSpeed,
      this.networkId,
      this.ssid});

  factory WifiConnectionObject.fromMap(Map<dynamic, dynamic> map) {
    return WifiConnectionObject(
        ssid: map['ssid'],
        frequency: map['frequency'],
        ip: map['ip'],
        linkSpeed: map['link_speed'],
        bssid: map['mac'],
        networkId: map['network_id']);
  }
}

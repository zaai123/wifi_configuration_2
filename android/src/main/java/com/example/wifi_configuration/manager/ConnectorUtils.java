package com.example.wifi_configuration.manager;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import android.text.TextUtils;

import com.example.wifi_configuration.ConnectionWpsListener;
import com.thanosfisherman.elvis.Objects;

import java.util.Collections;
import java.util.List;


public final class ConnectorUtils {
    private static final int MAX_PRIORITY = 99999;

    public static boolean isAlreadyConnected(WifiManager wifiManager,String bssid) {
        if (bssid != null && wifiManager != null) {
            if (wifiManager.getConnectionInfo() != null && wifiManager.getConnectionInfo().getBSSID() != null &&
                    wifiManager.getConnectionInfo().getIpAddress() != 0 &&
                    Objects.equals(bssid, wifiManager.getConnectionInfo().getBSSID())) {
                WifiUtils.wifiLog("Already connected to: " + wifiManager.getConnectionInfo().getSSID() + "  BSSID: " + wifiManager.getConnectionInfo().getBSSID());
                return true;
            }
        }
        return false;
    }


    @SuppressWarnings("UnusedReturnValue")
    private static boolean checkForExcessOpenNetworkAndSave( final ContentResolver resolver,  final WifiManager wifiMgr) {
        final List<WifiConfiguration> configurations = wifiMgr.getConfiguredNetworks();
        sortByPriority(configurations);

        boolean modified = false;
        int tempCount = 0;
        final int numOpenNetworksKept = Build.VERSION.SDK_INT >= 17
                ? Settings.Secure.getInt(resolver, Settings.Global.WIFI_NUM_OPEN_NETWORKS_KEPT, 10)
                : Settings.Secure.getInt(resolver, Settings.Secure.WIFI_NUM_OPEN_NETWORKS_KEPT, 10);

        for (int i = configurations.size() - 1; i >= 0; i--) {
            final WifiConfiguration config = configurations.get(i);
            if (Objects.equals(ConfigSecurities.SECURITY_NONE, ConfigSecurities.getSecurity(config))) {
                tempCount++;
                if (tempCount >= numOpenNetworksKept) {
                    modified = true;
                    wifiMgr.removeNetwork(config.networkId);
                }
            }
        }
        return !modified || wifiMgr.saveConfiguration();

    }

    private static int getMaxPriority( final WifiManager wifiManager) {
        final List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
        int pri = 0;
        for (final WifiConfiguration config : configurations) {
            if (config.priority > pri) {
                pri = config.priority;
            }
        }
        return pri;
    }

    private static int shiftPriorityAndSave( final WifiManager wifiMgr) {
        final List<WifiConfiguration> configurations = wifiMgr.getConfiguredNetworks();
        sortByPriority(configurations);
        final int size = configurations.size();
        for (int i = 0; i < size; i++) {
            final WifiConfiguration config = configurations.get(i);
            config.priority = i;
            wifiMgr.updateNetwork(config);
        }
        wifiMgr.saveConfiguration();
        return size;
    }

//        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {


    private static String trimQuotes( String str) {
        if (str != null && !str.isEmpty())
            return str.replaceAll("^\"*", "").replaceAll("\"*$", "");
        return str;
    }

    @SuppressWarnings("unused")
    public static int getPowerPercentage(int power) {
        int i;
        if (power <= -93)
            i = 0;
        else if (-25 <= power && power <= 0)
            i = 100;
        else
            i = 125 + power;
        return i;
    }

    
    static String convertToQuotedString( String string) {
        if (TextUtils.isEmpty(string))
            return "";

        final int lastPos = string.length() - 1;
        if (lastPos < 0 || (string.charAt(0) == '"' && string.charAt(lastPos) == '"'))
            return string;

        return "\"" + string + "\"";
    }

    static boolean isHexWepKey( String wepKey) {
        final int passwordLen = wepKey == null ? 0 : wepKey.length();
        return passwordLen != 0 && (passwordLen == 10 || passwordLen == 26 || passwordLen == 58) && wepKey.matches("[0-9A-Fa-f]*");
    }


    private static void sortByPriority( final List<WifiConfiguration> configurations) {
        Collections.sort(configurations, (o1, o2) -> o1.priority - o2.priority);
    }

    @SuppressWarnings("unused")
    public static int frequencyToChannel(int freq) {
        if (2412 <= freq && freq <= 2484)
            return (freq - 2412) / 5 + 1;
        else if (5170 <= freq && freq <= 5825)
            return (freq - 5170) / 5 + 34;
        else
            return -1;
    }

    static void registerReceiver( Context context,  BroadcastReceiver receiver,  IntentFilter filter) {
        if (receiver != null) {
            try {
                context.registerReceiver(receiver, filter);
            } catch (Exception e) {
            }
        }
    }

    static void unregisterReceiver( Context context,  BroadcastReceiver receiver) {
        if (receiver != null) {
            try {
                context.unregisterReceiver(receiver);
            } catch (IllegalArgumentException e) {
            }
        }
    }

    static boolean connectToWifi( Context context,  WifiManager wifiManager,  ScanResult scanResult,  String password) {
        Log.e("wifi_configuration_WifiConnection", "with ssid " + " and password " + password);
        WifiConfiguration config = ConfigSecurities.getWifiConfiguration(wifiManager, scanResult);
        if (config != null && password.isEmpty()) {
            WifiUtils.wifiLog("PASSWORD WAS EMPTY. TRYING TO CONNECT TO EXISTING NETWORK CONFIGURATION");
            return connectToConfiguredNetwork(wifiManager, config, true);
        }

        if (!cleanPreviousConfiguration(wifiManager, config)) {
            WifiUtils.wifiLog("COULDN'T REMOVE PREVIOUS CONFIG, CONNECTING TO EXISTING ONE");
            return connectToConfiguredNetwork(wifiManager, config, true);
        }

        final String security = ConfigSecurities.getSecurity(scanResult);

        if (Objects.equals(ConfigSecurities.SECURITY_NONE, security))
            checkForExcessOpenNetworkAndSave(context.getContentResolver(), wifiManager);

        config = new WifiConfiguration();
        config.SSID = convertToQuotedString(scanResult.SSID);
        config.BSSID = scanResult.BSSID;
        ConfigSecurities.setupSecurity(config, security, password);

        int id = wifiManager.addNetwork(config);
        WifiUtils.wifiLog("Network ID: " + id);
        if (id == -1)
            return false;

        // We have to retrieve the WifiConfiguration after save
        config = ConfigSecurities.getWifiConfiguration(wifiManager, config);
        if (config == null) {
            WifiUtils.wifiLog("Error getting wifi config after save. (config == null)");
            return false;
        }
        return connectToConfiguredNetwork(wifiManager, config, true);
    }

    private static boolean connectToConfiguredNetwork( WifiManager wifiManager,  WifiConfiguration config, boolean reassociate) {
        if (config == null)
            return false;

        if (Build.VERSION.SDK_INT >= 23)
            return disableAllButOne(wifiManager, config) && (reassociate ? wifiManager.reassociate() : wifiManager.reconnect());

        int oldPri = config.priority;
        // Make it the highest priority.
        int newPri = getMaxPriority(wifiManager) + 1;
        if (newPri > MAX_PRIORITY) {
            newPri = shiftPriorityAndSave(wifiManager);
            config = ConfigSecurities.getWifiConfiguration(wifiManager, config);
            if (config == null)
                return false;
        }

        // Set highest priority to this configured network
        config.priority = newPri;
        int networkId = wifiManager.updateNetwork(config);
        if (networkId == -1)
            return false;

        // Do not disable others
        if (!wifiManager.enableNetwork(networkId, false)) {
            config.priority = oldPri;
            return false;
        }

        if (!wifiManager.saveConfiguration()) {
            config.priority = oldPri;
            return false;
        }

        // We have to retrieve the WifiConfiguration after save.
        config = ConfigSecurities.getWifiConfiguration(wifiManager, config);
        return config != null && disableAllButOne(wifiManager, config) && (reassociate ? wifiManager.reassociate() : wifiManager.reconnect());
//


    }

    private static boolean disableAllButOne( final WifiManager wifiManager,  final WifiConfiguration config) {
         final List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
        if (configurations == null || config == null || configurations.isEmpty())
            return false;
        boolean result = false;

        for (WifiConfiguration wifiConfig : configurations)
            if (wifiConfig.networkId == config.networkId)
                result = wifiManager.enableNetwork(wifiConfig.networkId, true);
            else {
                continue;
//                wifiManager.disableNetwork(wifiConfig.networkId);
            }
        WifiUtils.wifiLog("disableAllButOne " + result);
        return result;
    }


    @SuppressWarnings("UnusedReturnValue")
    private static boolean disableAllButOne( final WifiManager wifiManager,  final ScanResult scanResult) {
         final List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
        if (configurations == null || scanResult == null || configurations.isEmpty())
            return false;
        boolean result = false;
        for (WifiConfiguration wifiConfig : configurations)
            if (Objects.equals(scanResult.BSSID, wifiConfig.BSSID) && Objects.equals(scanResult.SSID, trimQuotes(wifiConfig.SSID)))
                result = wifiManager.enableNetwork(wifiConfig.networkId, true);
            else
                wifiManager.disableNetwork(wifiConfig.networkId);
        return result;
    }

    public static boolean reEnableNetworkIfPossible( final WifiManager wifiManager,  final ScanResult scanResult) {
         final List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
        if (configurations == null || scanResult == null || configurations.isEmpty())
            return false;
        boolean result = false;
        for (WifiConfiguration wifiConfig : configurations)
            if (Objects.equals(scanResult.BSSID, wifiConfig.BSSID) && Objects.equals(scanResult.SSID, trimQuotes(wifiConfig.SSID))) {
                result = wifiManager.enableNetwork(wifiConfig.networkId, true);
                break;
            }
        WifiUtils.wifiLog("reEnableNetworkIfPossible " + result);
        return result;
    }

//    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static void connectWps( final WifiManager wifiManager,  final ScanResult scanResult,  String pin, long timeOutMillis,
                            final ConnectionWpsListener connectionWpsListener) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

        final WeakHandler handler = new WeakHandler();
        final WpsInfo wpsInfo = new WpsInfo();
        final Runnable handlerTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                wifiManager.cancelWps(null);
                WifiUtils.wifiLog("Connection with WPS has timed out");
                cleanPreviousConfiguration(wifiManager, scanResult);
                connectionWpsListener.isSuccessful(false);
                handler.removeCallbacks(this);
            }
        };

        final WifiManager.WpsCallback wpsCallback = new WifiManager.WpsCallback() {
            @Override
            public void onStarted(String pin) {
            }

            @Override
            public void onSucceeded() {
                handler.removeCallbacks(handlerTimeoutRunnable);
                WifiUtils.wifiLog("CONNECTED With WPS successfully");
                connectionWpsListener.isSuccessful(true);
            }

            @Override
            public void onFailed(int reason) {
                handler.removeCallbacks(handlerTimeoutRunnable);
                final String reasonStr;
                switch (reason) {
                    case 3:
                        reasonStr = "WPS_OVERLAP_ERROR";
                        break;
                    case 4:
                        reasonStr = "WPS_WEP_PROHIBITED";
                        break;
                    case 5:
                        reasonStr = "WPS_TKIP_ONLY_PROHIBITED";
                        break;
                    case 6:
                        reasonStr = "WPS_AUTH_FAILURE";
                        break;
                    case 7:
                        reasonStr = "WPS_TIMED_OUT";
                        break;
                    default:
                        reasonStr = String.valueOf(reason);
                }
                WifiUtils.wifiLog("FAILED to connect with WPS. Reason: " + reasonStr);
                cleanPreviousConfiguration(wifiManager, scanResult);
                reenableAllHotspots(wifiManager);
                connectionWpsListener.isSuccessful(false);
            }
        };

        WifiUtils.wifiLog("Connecting with WPS...");
        wpsInfo.setup = WpsInfo.KEYPAD;
        wpsInfo.BSSID = scanResult.BSSID;
        wpsInfo.pin = pin;
        wifiManager.cancelWps(null);

        if (!cleanPreviousConfiguration(wifiManager, scanResult))
            disableAllButOne(wifiManager, scanResult);

        handler.postDelayed(handlerTimeoutRunnable, timeOutMillis);
        wifiManager.startWps(wpsInfo, wpsCallback);

    }
    }

    static boolean cleanPreviousConfiguration( final WifiManager wifiManager,  final ScanResult scanResult) {
        //On Android 6.0 (API level 23) and above if my app did not create the configuration in the first place, it can not remove it either.
        final WifiConfiguration config = ConfigSecurities.getWifiConfiguration(wifiManager, scanResult);
        WifiUtils.wifiLog("Attempting to remove previous network config...");
        if (config == null)
            return true;

        if (wifiManager.removeNetwork(config.networkId)) {
            wifiManager.saveConfiguration();
            return true;
        }
        return false;
    }

    static boolean cleanPreviousConfiguration( final WifiManager wifiManager,  final WifiConfiguration config) {
        //On Android 6.0 (API level 23) and above if my app did not create the configuration in the first place, it can not remove it either.

        WifiUtils.wifiLog("Attempting to remove previous network config...");
        if (config == null)
            return true;

        if (wifiManager.removeNetwork(config.networkId)) {
            wifiManager.saveConfiguration();
            return true;
        }
        return false;
    }

    static void reenableAllHotspots( WifiManager wifi) {
        final List<WifiConfiguration> configurations = wifi.getConfiguredNetworks();
        if (configurations != null && !configurations.isEmpty())
            for (final WifiConfiguration config : configurations)
                wifi.enableNetwork(config.networkId, false);
    }

    
    static ScanResult matchScanResultSsid( String ssid,  Iterable<ScanResult> results) {
        for (ScanResult result : results)
            if (Objects.equals(result.SSID, ssid))
                return result;
        return null;
    }

    
    static ScanResult matchScanResult( String ssid,  String bssid,  Iterable<ScanResult> results) {
        for (ScanResult result : results)
            if (Objects.equals(result.SSID, ssid) && Objects.equals(result.BSSID, bssid))
                return result;
        return null;
    }

    
    static ScanResult matchScanResultBssid( String bssid,  Iterable<ScanResult> results) {
        for (ScanResult result : results)
            if (Objects.equals(result.BSSID, bssid))
                return result;
        return null;
    }
}

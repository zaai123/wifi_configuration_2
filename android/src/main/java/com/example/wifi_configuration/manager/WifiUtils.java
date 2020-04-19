package com.example.wifi_configuration.manager;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.example.wifi_configuration.ConnectionWpsListener;
import com.example.wifi_configuration.connect.ConnectionScanResultsListener;
import com.example.wifi_configuration.connect.ConnectionSuccessListener;
import com.example.wifi_configuration.connect.WifiConnectionCallback;
import com.example.wifi_configuration.connect.WifiConnectionReceiver;
import com.example.wifi_configuration.scan.ScanResultsListener;
import com.example.wifi_configuration.scan.WifiScanCallback;
import com.example.wifi_configuration.scan.WifiScanReceiver;
import com.example.wifi_configuration.state.WifiStateCallback;
import com.example.wifi_configuration.state.WifiStateListener;
import com.example.wifi_configuration.state.WifiStateReceiver;
import com.example.wifi_configuration.util.Connectivity;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.example.wifi_configuration.manager.ConnectorUtils.cleanPreviousConfiguration;
import static com.example.wifi_configuration.manager.ConnectorUtils.connectToWifi;
import static com.example.wifi_configuration.manager.ConnectorUtils.connectWps;
import static com.example.wifi_configuration.manager.ConnectorUtils.matchScanResult;
import static com.example.wifi_configuration.manager.ConnectorUtils.matchScanResultBssid;
import static com.example.wifi_configuration.manager.ConnectorUtils.matchScanResultSsid;
import static com.example.wifi_configuration.manager.ConnectorUtils.reenableAllHotspots;
import static com.example.wifi_configuration.manager.ConnectorUtils.registerReceiver;
import static com.example.wifi_configuration.manager.ConnectorUtils.unregisterReceiver;
import static com.thanosfisherman.elvis.Elvis.of;

public final class WifiUtils implements WifiConnectorBuilder,
        WifiConnectorBuilder.WifiUtilsBuilder,
        WifiConnectorBuilder.WifiSuccessListener,
        WifiConnectorBuilder.WifiWpsSuccessListener {
    
    private final WifiManager mWifiManager;
    
    private final Context mContext;
    private static boolean mEnableLog;
    private long mWpsTimeoutMillis = 30000;
    private long mTimeoutMillis = 30000;
    
    private static final String TAG = WifiUtils.class.getSimpleName();
    // private static final WifiUtils INSTANCE = new WifiUtils();
    
    private final WifiStateReceiver mWifiStateReceiver;
    
    private final WifiConnectionReceiver mWifiConnectionReceiver;
    
    private final WifiScanReceiver mWifiScanReceiver;
    
    private String mSsid;
    
    private String mBssid;
    
    private String mPassword;
    
    private ScanResult mSingleScanResult;
    
    private ScanResultsListener mScanResultsListener;
    
    private ConnectionScanResultsListener mConnectionScanResultsListener;
    
    private ConnectionSuccessListener mConnectionSuccessListener;
    
    private WifiStateListener mWifiStateListener;
    
    private ConnectionWpsListener mConnectionWpsListener;

    
    private final WifiStateCallback mWifiStateCallback = new WifiStateCallback() {
        @Override
        public void onWifiEnabled() {
            wifiLog("WIFI ENABLED...");
            unregisterReceiver(mContext, mWifiStateReceiver);
            of(mWifiStateListener).ifPresent(stateListener -> stateListener.isSuccess(true));

            if (mScanResultsListener != null || mPassword != null) {
                wifiLog("START SCANNING....");
                if (mWifiManager.startScan())
                    registerReceiver(mContext, mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                else {
                    of(mScanResultsListener).ifPresent(resultsListener -> resultsListener.onScanResults(new ArrayList<>()));
                    of(mConnectionWpsListener).ifPresent(wpsListener -> wpsListener.isSuccessful(false));
                    mWifiConnectionCallback.errorConnect();
                    wifiLog("ERROR COULDN'T SCAN");
                }
            }
        }
    };

    
    private final WifiScanCallback mWifiScanResultsCallback = new WifiScanCallback() {
        @Override
        public void onScanResultsReady() {
            wifiLog("GOT SCAN RESULTS");
            unregisterReceiver(mContext, mWifiScanReceiver);

            final List<ScanResult> scanResultList = mWifiManager.getScanResults();
            of(mScanResultsListener).ifPresent(resultsListener -> resultsListener.onScanResults(scanResultList));
            of(mConnectionScanResultsListener).ifPresent(connectionResultsListener -> mSingleScanResult = connectionResultsListener.onConnectWithScanResult(scanResultList));

            if (mConnectionWpsListener != null && mBssid != null && mPassword != null) {
                mSingleScanResult = matchScanResultBssid(mBssid, scanResultList);
                if (mSingleScanResult != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    connectWps(mWifiManager, mSingleScanResult, mPassword, mWpsTimeoutMillis, mConnectionWpsListener);
                else {
                    if (mSingleScanResult == null)
                        wifiLog("Couldn't find network. Possibly out of range");
                    mConnectionWpsListener.isSuccessful(false);
                }
                return;
            }

            if (mSsid != null) {
                if (mBssid != null)
                    mSingleScanResult = matchScanResult(mSsid, mBssid, scanResultList);
                else
                    mSingleScanResult = matchScanResultSsid(mSsid, scanResultList);
            }
            if (mSingleScanResult != null && mPassword != null) {
                if (connectToWifi(mContext, mWifiManager, mSingleScanResult, mPassword)) {
                    registerReceiver(mContext, mWifiConnectionReceiver.activateTimeoutHandler(mSingleScanResult),
                            new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
                    registerReceiver(mContext, mWifiConnectionReceiver,
                            new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
                } else
                    mWifiConnectionCallback.errorConnect();
            } else
                mWifiConnectionCallback.errorConnect();
        }
    };

    public List<ScanResult> getScanWifiResult(){

        if (mWifiManager != null && mWifiManager.getScanResults() != null && mWifiManager.getScanResults().size() > 0) {
            return mWifiManager.getScanResults();
        }
        return null;
    }

    
    private final WifiConnectionCallback mWifiConnectionCallback = new WifiConnectionCallback() {
        @Override
        public void successfulConnect() {
            wifiLog("CONNECTED SUCCESSFULLY");
            Log.e("wifi_configuration", "connected successfully");
            unregisterReceiver(mContext, mWifiConnectionReceiver);
            //reenableAllHotspots(mWifiManager);
            of(mConnectionSuccessListener).ifPresent(successListener -> successListener.isSuccessful(true));
        }

        @Override
        public void errorConnect() {
            unregisterReceiver(mContext, mWifiConnectionReceiver);
            reenableAllHotspots(mWifiManager);
            //if (mSingleScanResult != null)
            //cleanPreviousConfiguration(mWifiManager, mSingleScanResult);
            of(mConnectionSuccessListener).ifPresent(successListener -> {
                successListener.isSuccessful(false);
                Log.e("wifi_configuration", "not connected successfully");
                wifiLog("DIDN'T CONNECT TO WIFI");
            });
        }
    };

    public WifiUtils( Context context) {
        mContext = context;
        mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (mWifiManager == null)
            throw new RuntimeException("WifiManager is not supposed to be null");
        mWifiStateReceiver = new WifiStateReceiver(mWifiStateCallback);
        mWifiScanReceiver = new WifiScanReceiver(mWifiScanResultsCallback);
        mWifiConnectionReceiver = new WifiConnectionReceiver(mWifiConnectionCallback, mWifiManager, mTimeoutMillis);
    }

    public static WifiUtilsBuilder withContext( final Context context) {
        return new WifiUtils(context);
    }

    public static void wifiLog(final String text) {
//        if (mEnableLog)
            Log.d(TAG, "WifiUtils: " + text);
    }

    public static void enableLog(final boolean enabled) {
        mEnableLog = enabled;
    }

    @Override
    public void enableWifi( final WifiStateListener wifiStateListener) {
        mWifiStateListener = wifiStateListener;
        if (mWifiManager.isWifiEnabled()) {
            Log.e("wifiUtils", "wifi already enabled");
            mWifiStateCallback.onWifiEnabled();
        }
        else {
            Log.e("wifiUtils", "wifi enabling");
            if (mWifiManager.setWifiEnabled(true))
                registerReceiver(mContext, mWifiStateReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
            else {
                of(wifiStateListener).ifPresent(stateListener -> stateListener.isSuccess(false));
                of(mScanResultsListener).ifPresent(resultsListener -> resultsListener.onScanResults(new ArrayList<>()));
                of(mConnectionWpsListener).ifPresent(wpsListener -> wpsListener.isSuccessful(false));
                mWifiConnectionCallback.errorConnect();
                wifiLog("COULDN'T ENABLE WIFI");
            }
        }
    }

    @Override
    public void enableWifi() {
        enableWifi(null);
    }

    
    @Override
    public WifiConnectorBuilder scanWifi(final ScanResultsListener scanResultsListener) {
        mScanResultsListener = scanResultsListener;
        return this;
    }

    public boolean checkIsWifiEnabled(){
        return mWifiManager.isWifiEnabled();
    }

    
    @Override
    public WifiSuccessListener connectWith( final String ssid,  final String password) {
        mSsid = ssid;
        mPassword = password;
        return this;
    }

    
    @Override
    public WifiSuccessListener connectWith( final String ssid,  final String bssid,  final String password) {
        mSsid = ssid;
        mBssid = bssid;
        mPassword = password;
        return this;
    }

    
    @Override
    public WifiSuccessListener connectWithScanResult( final String password,
                                                      final ConnectionScanResultsListener connectionScanResultsListener) {
        mConnectionScanResultsListener = connectionScanResultsListener;
        mPassword = password;
        return this;
    }

    
    @Override
    public WifiWpsSuccessListener connectWithWps( final String bssid,  final String password) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // only for gingerbread and newer versions
            mBssid = bssid;
            mPassword = password;
        }

        return this;
    }

    @Override
    public void cancelAutoConnect() {
        unregisterReceiver(mContext, mWifiStateReceiver);
        unregisterReceiver(mContext, mWifiScanReceiver);
        unregisterReceiver(mContext, mWifiConnectionReceiver);
        of(mSingleScanResult).ifPresent(scanResult -> cleanPreviousConfiguration(mWifiManager, scanResult));
        reenableAllHotspots(mWifiManager);
    }

    
    @Override
    public WifiSuccessListener setTimeout(final long timeOutMillis) {
        mTimeoutMillis = timeOutMillis;
        mWifiConnectionReceiver.setTimeout(timeOutMillis);
        return this;
    }

    
    @Override
    public WifiWpsSuccessListener setWpsTimeout(final long timeOutMillis) {
        mWpsTimeoutMillis = timeOutMillis;
        return this;
    }

    
    @Override
    public WifiConnectorBuilder onConnectionWpsResult( final ConnectionWpsListener successListener) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // only for gingerbread and newer versions
            mConnectionWpsListener = successListener;
        }
        return this;
    }


    
    @Override
    public WifiConnectorBuilder onConnectionResult( final ConnectionSuccessListener successListener) {
        mConnectionSuccessListener = successListener;
        return this;
    }

    @Override
    public void start() {
        unregisterReceiver(mContext, mWifiStateReceiver);
        unregisterReceiver(mContext, mWifiScanReceiver);
        unregisterReceiver(mContext, mWifiConnectionReceiver);
        enableWifi(null);
    }

    @Override
    public void disableWifi() {
        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
            unregisterReceiver(mContext, mWifiStateReceiver);
            unregisterReceiver(mContext, mWifiScanReceiver);
            unregisterReceiver(mContext, mWifiConnectionReceiver);
        }
        wifiLog("WiFi Disabled");
    }

    /**
     * Get the network info
     * @param
     * @return
     */
    public  NetworkInfo getNetworkInfo(){
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo();
    }

    /**
     * Check if there is any connectivity
     * @param context
     * @return
     */
    public boolean isConnected(){
        NetworkInfo info = Connectivity.getNetworkInfo(mContext);
        return (info != null && info.isConnected());
    }

    /**
     * Check if there is any connectivity to a Wifi network
     * @param context
     * @return
     */
    public  boolean isConnectedWifi(){
        NetworkInfo info = Connectivity.getNetworkInfo(mContext);
        return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI);
    }

    /**
     * Check if there is any connectivity to a mobile network
     * @param
     * @return
     */
    public  boolean isConnectedMobile(){
        NetworkInfo info = Connectivity.getNetworkInfo(mContext);
        return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_MOBILE);
    }

    /**
     * Check if there is fast connectivity
     * @param context
     * @return
     */
    public  boolean isConnectedFast(){
        NetworkInfo info = Connectivity.getNetworkInfo(mContext);
        return (info != null && info.isConnected() && Connectivity.isConnectionFast(info.getType(),info.getSubtype()));
    }

    /**
     * Check if the connection is fast
     * @param type
     * @param subType
     * @return
     */
    public  boolean isConnectionFast(int type, int subType){
        if(type==ConnectivityManager.TYPE_WIFI){
            return true;
        }else if(type==ConnectivityManager.TYPE_MOBILE){
            switch(subType){
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    return false; // ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_CDMA:
                    return false; // ~ 14-64 kbps
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    return false; // ~ 50-100 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    return true; // ~ 400-1000 kbps
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    return true; // ~ 600-1400 kbps
                case TelephonyManager.NETWORK_TYPE_GPRS:
                    return false; // ~ 100 kbps
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                    return true; // ~ 2-14 Mbps
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    return true; // ~ 700-1700 kbps
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                    return true; // ~ 1-23 Mbps
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    return true; // ~ 400-7000 kbps
                /*
                 * Above API level 7, make sure to set android:targetSdkVersion
                 * to appropriate level to use these
                 */
                case TelephonyManager.NETWORK_TYPE_EHRPD: // API level 11
                    return true; // ~ 1-2 Mbps
                case TelephonyManager.NETWORK_TYPE_EVDO_B: // API level 9
                    return true; // ~ 5 Mbps
                case TelephonyManager.NETWORK_TYPE_HSPAP: // API level 13
                    return true; // ~ 10-20 Mbps
                case TelephonyManager.NETWORK_TYPE_IDEN: // API level 8
                    return false; // ~25 kbps
                case TelephonyManager.NETWORK_TYPE_LTE: // API level 11
                    return true; // ~ 10+ Mbps
                // Unknown
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                default:
                    return false;
            }
        }else{
            return false;
        }
    }

    /**
     * Get IP address from first non-localhost interface
     *
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return address or empty string
     */
    public String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
        } // for now eat exceptions
        return "";
    }

    public int calculateSignalLevel(int rssi, int numLevels) {
        if (rssi <= -100) {
            return 0;
        } else if (rssi >= -55) {
            return numLevels - 1;
        } else {
            float inputRange = (-55 - -100);
            float outputRange = (numLevels - 1);
            if (inputRange != 0)
                return (int) ((float) (rssi - -100) * outputRange / inputRange);
        }
        return 0;
    }
}

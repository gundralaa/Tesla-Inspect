package com.vegetarianbaconite.teslainspect;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,
        DeviceNameReceiver.OnDeviceNameReceivedListener {

    TextView widiName, widiConnected, wifiEnabled, batteryLevel, osVersion, airplaneMode, bluetooth,
            wifiConnected, appsStatus;
    TextView txtManufacturer, txtModel;
    TextView txtIsRCInstalled, txtIsDSInstalled, txtIsCCInstalled;
    ActionBar ab;
    final int dsid = 9277, ccid = 10650;
    String rcApp = "com.qualcomm.ftcrobotcontroller", dsApp = "com.qualcomm.ftcdriverstation",
            ccApp = "com.zte.wifichanneleditor", widiNameString = "";
    DeviceNameReceiver mDeviceNameReceiver;
    Pattern teamNoRegex;
    Handler handler;
    Runnable refreshRunnable;
    IntentFilter filter;
    Integer darkGreen = Color.rgb(47, 151, 47);
    Integer yellow = Color.rgb(178, 178, 0);
    Integer orange = Color.rgb(255, 128, 0);

    static final String STR_ZTE="zte";
    static final String STR_ZTESPEED="N9130";

    static final int RC_MIN_VERSIONCODE=9;
    static final int DS_MIN_VERSIONCODE=9;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ab = getSupportActionBar();
        ab.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#FF0000")));
        ab.setTitle("FTC Inspect: " + BuildConfig.VERSION_NAME);

        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                refresh();
                handler.postDelayed(getRefreshRunnable(), 1000);
                Log.d("Handler", "Boop.");
            }
        };

        txtIsRCInstalled = (TextView)findViewById(R.id.txtIsRCInstalled);
        txtIsDSInstalled = (TextView)findViewById(R.id.txtIsDSInstalled);
        txtIsCCInstalled = (TextView)findViewById(R.id.txtIsCCInstalled);


        widiName = (TextView) findViewById(R.id.widiName);
        widiConnected = (TextView) findViewById(R.id.widiConnected);
        wifiEnabled = (TextView) findViewById(R.id.wifiEnabled);
        batteryLevel = (TextView) findViewById(R.id.batteryLevel);
        osVersion = (TextView) findViewById(R.id.osVersion);
        airplaneMode = (TextView) findViewById(R.id.airplaneMode);
        bluetooth = (TextView) findViewById(R.id.bluetoothEnabled);
        wifiConnected = (TextView) findViewById(R.id.wifiConnected);
        appsStatus = (TextView) findViewById(R.id.appsStatus);

        txtManufacturer = (TextView)findViewById(R.id.txtManufacturer);
        txtModel = (TextView)findViewById(R.id.txtModel);

        teamNoRegex = Pattern.compile("^\\d{1,5}(-\\w)?-(RC|DS)\\z", Pattern.CASE_INSENSITIVE);

        initReceiver();
        startReceivingWidiInfo();


        handler = new Handler();
        handler.postDelayed(getRefreshRunnable(), 1000);

        refresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.clear_wifi) {
            return deleteAllWifi();
        }

        if (id == R.id.clear_widi) {
            deleteRememberedWiDi();
            Toast.makeText(getApplicationContext(), "Deleted remembered WifiDirect Connections!",
                    Toast.LENGTH_SHORT).show();

            return true;
        }

        if (id == R.id.disc_widi) {
            disconnectWiDi();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mDeviceNameReceiver);
        super.onPause();
    }

    @Override
    protected void onResume() {
        startReceivingWidiInfo();
        super.onResume();
    }

    private Boolean validateInputs() {
        if (!validateVersion()) return false;
        if (!getAirplaneMode()) return false;
        if (getBluetooth()) return false;
        if (!getWiFiEnabled()) return false;
        if (getWifiConnected()) return false;
        if (!validateDeviceName()) return false;

        //TODO: Name mismatch validation
        return validateAppsInstalled();
    }

    private void refresh() {
        widiConnected.setText(getWiDiConnected() ? "\u2713" : "X");
        wifiEnabled.setText(getWiFiEnabled() ? "\u2713" : "X");
        osVersion.setText(Build.VERSION.RELEASE);
        airplaneMode.setText(getAirplaneMode() ? "\u2713" : "X");
        bluetooth.setText(getBluetooth() ? "On" : "Off");
        wifiConnected.setText(getWifiConnected() ? "Yes" : "No");
        widiName.setText(widiNameString);

        txtManufacturer.setText(Build.MANUFACTURER);
        txtModel.setText(Build.MODEL);

        widiConnected.setTextColor(getWiDiConnected() ? darkGreen : Color.RED);
        wifiEnabled.setTextColor(getWiFiEnabled() ? darkGreen : Color.RED);
        airplaneMode.setTextColor(getAirplaneMode() ? darkGreen : Color.RED);
        bluetooth.setTextColor(!getBluetooth() ? darkGreen : Color.RED);
        osVersion.setTextColor(validateVersion() ? darkGreen : Color.RED);

        widiName.setTextColor(validateDeviceName() ? darkGreen : Color.RED);

        wifiConnected.setTextColor(!getWifiConnected() ? darkGreen : Color.RED);

        // check the installed apps.
        Boolean appsOkay = true;

        // check if channel change app should be installed.
        // is this a ZTE speed?
        if(STR_ZTE.equalsIgnoreCase(Build.MANUFACTURER) && STR_ZTESPEED.equalsIgnoreCase(Build.MODEL))  {
            // ZTE Speed should have channel change app.
            // Note that only the RC really needs the channel change app.
            // For now, however, check if ccApp is installed on all ZTE Speed phones.
            if(packageExists(ccApp)) {
                txtIsCCInstalled.setText("\u2713");
                txtIsCCInstalled.setTextColor(darkGreen);
            } else {
                txtIsCCInstalled.setText("X");
                txtIsCCInstalled.setTextColor(Color.RED);
                appsOkay = false;
            }
        } else {
            txtIsCCInstalled.setText("N/A");
            txtIsCCInstalled.setTextColor(darkGreen);
        }

        // is the robot controller installed?
        if (packageExists(rcApp)) {
            // display version number.
            txtIsRCInstalled.setText(getPackageInfo(rcApp).versionName);
            if (getPackageInfo(rcApp).versionCode < RC_MIN_VERSIONCODE) {
                txtIsRCInstalled.setTextColor(orange);
                appsOkay = false;
            } else {
                txtIsRCInstalled.setTextColor(darkGreen);
            }
        } else {
            txtIsRCInstalled.setText("X");
            txtIsRCInstalled.setTextColor(darkGreen);
        }

        // is the driver station installed?
        if (packageExists(dsApp)) {
            // check version number.
            txtIsDSInstalled.setText(getPackageInfo(dsApp).versionName);
            if (getPackageInfo(dsApp).versionCode < DS_MIN_VERSIONCODE) {
                txtIsDSInstalled.setTextColor(orange);
                appsOkay = false;
            } else {
                txtIsDSInstalled.setTextColor(darkGreen);
            }
        } else {
            txtIsDSInstalled.setText("X");
            txtIsDSInstalled.setTextColor(darkGreen);
        }

        if(packageExists(rcApp) == false && packageExists(dsApp) == false)  {
            // you should have at least one or the other installed.
            appsOkay = false;
            txtIsDSInstalled.setTextColor(Color.RED);
            txtIsRCInstalled.setTextColor(Color.RED);
        }

        if (packageExists(rcApp) && packageExists(dsApp)) {
            // you should not have both installed.
            appsOkay = false;
            txtIsDSInstalled.setTextColor(Color.RED);
            txtIsRCInstalled.setTextColor(Color.RED);
        }

        // is there installation of apps OK?
        appsStatus.setTextColor(appsOkay ? darkGreen : Color.RED);
        appsStatus.setText(appsOkay ? "\u2713" : "X");

        getBatteryInfo();
    }

    public void explainErrors() {
        Dialogs d = new Dialogs(this);

        if (!validateVersion()) d.addError(R.string.verisonError);
        if (!getAirplaneMode()) d.addError(R.string.airplaneError);
        if (getBluetooth()) d.addError(R.string.bluetoothError);
        if (!getWiFiEnabled()) d.addError(R.string.wifiEnabledError);
        if (getWifiConnected()) d.addError(R.string.wifiConnectedError);
        if (!validateDeviceName()) d.addError(R.string.widiNameError);

        if (!packageExists(ccApp)) d.addError(R.string.missingChannelChanger);
        if (packageExists(dsApp) && (packageExists(rcApp) || appInventorExists()))
            d.addError(R.string.tooManyApps);
        else if ((!packageExists(dsApp) || !(packageExists(rcApp) || appInventorExists())) && !validateAppsInstalled())
            d.addError(R.string.notEnoughApps);

        Dialog dlg = d.build();
        dlg.show();
    }

    @Override
    public void onDeviceNameReceived(String deviceName) {
        widiNameString = deviceName;
        refresh();
    }

    public Boolean getAirplaneMode() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    public Boolean getWifiDeviceLockdown() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, 0) != 0;
    }


    public Boolean getWifiConnected() {
        WifiManager m = (WifiManager) getSystemService(WIFI_SERVICE);
        SupplicantState s = m.getConnectionInfo().getSupplicantState();
        NetworkInfo.DetailedState state = WifiInfo.getDetailedStateOf(s);
        Log.v("getWifiConnected", state.toString());

        return (state == NetworkInfo.DetailedState.CONNECTED ||
                state == NetworkInfo.DetailedState.OBTAINING_IPADDR);
    }

    public Boolean getBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return false;
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                return false;
            }
        }

        return true;
    }

    public Boolean validateVersion() {
        boolean bVal;
        // is this a ZTE speed?
        if(STR_ZTE.equalsIgnoreCase(Build.MANUFACTURER) && STR_ZTESPEED.equalsIgnoreCase(Build.MODEL))  {
            // ZTE Speed should be Kit Kat. ZTE did not upgrade Speed beyond Kit Kat.
            if(Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                return true;
            } else {
                return false;
            }
        } else {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // for 2016-2017 season we recommend Marshmallow or higher.
                return true;
            } else {
                return false;
            }
        }
    }

    public Boolean validateDeviceName() {
        if (widiNameString.contains("\n") || widiNameString.contains("\r")) return false;
        return (teamNoRegex.matcher(widiNameString)).find();
    }

    public Boolean getWiFiEnabled() {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        return wifi.isWifiEnabled();
    }

    public Boolean getWiDiConnected() {
        return new WifiP2pDevice().status == WifiP2pDevice.CONNECTED;
    }

    private void initReceiver() {
        mDeviceNameReceiver = new DeviceNameReceiver();
        mDeviceNameReceiver.setOnDeviceNameReceivedListener(this);
        filter = new IntentFilter(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void startReceivingWidiInfo() {
        registerReceiver(mDeviceNameReceiver, filter);
    }

    public Boolean validateAppsInstalled() {
        if (!packageExists(ccApp)) return false;

        return (packageExists(dsApp) ^ (packageExists(rcApp) || appInventorExists()));
    }

    private void getBatteryInfo() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level / (float) scale;

        batteryLevel.setText(Math.round(batteryPct * 100f) + "%");
        batteryLevel.setTextColor(batteryPct > 0.6 ? darkGreen : orange);
    }

    private Runnable getRefreshRunnable() {
        return refreshRunnable;
    }

    public Boolean packageExists(String targetPackage) {
        return !packageCode(targetPackage).equals("na");
    }

    public String packageCode(String targetPackage) {
        PackageManager pm = getPackageManager();
        try {
            return pm.getPackageInfo(targetPackage, PackageManager.GET_META_DATA).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "na";
        }
    }

    private Boolean appInventorExists() {
        return !(findAppStudioApps().equals("na") || findAppStudioApps().equals("duplicate"));
    }

    // Returns
    //   na - if none found
    //   Duplicate if more than one appstudio app found
    //   package name if only one app found
    private String findAppStudioApps() {
        String strval = "na";

        final PackageManager pm = getPackageManager();
        final List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        if (installedApps != null) {
            for (ApplicationInfo app : installedApps) {
                if (app.packageName.startsWith("appinventor.ai_")) {

                    if (strval.equals("na")) {
                        strval = getPackageInfo(app.packageName).versionName;
                    } else {
                        strval = "Duplicate";
                    }
                }
            }
        }

        return strval;
    }

    public PackageInfo getPackageInfo(String targetPackage) {
        PackageManager pm = getPackageManager();
        try {
            return pm.getPackageInfo(targetPackage, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void startStore(String appPackageName) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == dsid) {
            startStore(dsApp);
        }

        if (id == ccid) {
            startStore(ccApp);
        }
    }

    private boolean mayAccessWifiState()  {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // you only need to request permisision at run time for Android M and greater.
            return true;
        }

        // check wifi device owner configs lockdown.
        // if non zero, then app will not be able to modify networks that it did not create.

        if (getWifiDeviceLockdown()) {
            Log.d("TIENG", "TIENG - WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN is enabled");
        }     else {
            Log.d("TIENG", "TIENG - WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN is NOT enabled");
        }


        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            Log.d("TIENG", "ACCESS_WIFI_STATE PERMISSION_GRANTED");
            return true;
        } else {
            Log.d("TIENG", "ACCESS_WIFI_STATE PERMISSION_DENIED");
            return false;
        }

    }

    private boolean deleteAllWifi() {
        // check Android version.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)  {
            Toast.makeText(getApplicationContext(),
                    "Cannot delete networks! This feature is not available with Android M or higher.",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        boolean bError = false;
        WifiManager mainWifiObj = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> list = mainWifiObj.getConfiguredNetworks();
        for (WifiConfiguration i : list) {
            if (mainWifiObj.removeNetwork(i.networkId)) {
                Log.d("TIENG", String.format("removeNetwork successful for %s", i.SSID));
            } else {
                Log.d("TIENG", String.format("removeNetwork failed for %s", i.SSID));
            }
            if (mainWifiObj.saveConfiguration()) {
                Log.d("TIENG", String.format("saveConfiguration successful for %s", i.SSID));
            } else {
                Log.d("TIENG", String.format("saveConfiguration FAILED for %s", i.SSID));
                bError = true;
            }
        }
        if (bError) {
            // had an issue deleting one or more networks.
            Toast.makeText(getApplicationContext(), "An error occurred while deleting one or more Wifi Networks",
                    Toast.LENGTH_LONG).show();
            return false;
        } else {
            Toast.makeText(getApplicationContext(), "Deleted remembered Wifi Networks!",
                    Toast.LENGTH_LONG).show();
            return true;
        }
    }

    private void deleteRememberedWiDi() {
        final WifiP2pManager wifiP2pManagerObj = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        final Context context = getApplicationContext();
        final Channel channel = wifiP2pManagerObj.initialize(context, context.getMainLooper(), new ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.d("WIFIDIRECT", "Channel disconnected!");
            }
        });

        try {
            Method[] methods = WifiP2pManager.class.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals("deletePersistentGroup")) {
                    // Delete any persistent group
                    for (int netid = 0; netid < 32; netid++) {
                        methods[i].invoke(wifiP2pManagerObj, channel, netid, null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disconnectWiDi() {
        final WifiP2pManager wifiP2pManagerObj = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        final Context context = getApplicationContext();
        final Channel mChannel = wifiP2pManagerObj.initialize(context, context.getMainLooper(), new ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.d("WIFIDIRECT", "Channel disconnected!");
            }
        });

        if (mChannel != null) {
            wifiP2pManagerObj.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null && group.isGroupOwner()) {
                        wifiP2pManagerObj.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d("WIFIDIRECT", "Current WifiDirect Connection Removed");
                                Toast.makeText(MainActivity.this, "Successfully disconnected from WiFi Direct",
                                        Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.d("WIFIDIRECT", "Current WifiDirect Connection Removal Failed - " + reason);
                                Toast.makeText(MainActivity.this, "There was an error disconnecting " +
                                        "from WiFi Direct!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });
        }
    }
}
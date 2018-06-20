package com.aograph.mocklocation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvSystemMockPositionStatus = null;
    private Button btnStartMock = null;
    private Button btnStopMock = null;
    private TextView tvProvider = null;
    private TextView tvTime = null;
    private TextView tvLatitude = null;
    private TextView tvLongitude = null;
    private TextView tvAltitude = null;
    private TextView tvBearing = null;
    private TextView tvSpeed = null;
    private TextView tvAccuracy = null;

    /**
     * 位置管理器
     */
    private LocationManager locationManager = null;

    public LocationManager getLocationManager() {
        return locationManager;
    }

    /**
     * 模拟位置的提供者
     */
    private List<String> mockProviders = null;

    public List<String> getMockProviders() {
        return mockProviders;
    }

    /**
     * 是否成功addTestProvider，默认为true，软件启动时为防止意外退出导致未重置，重置一次
     * Android 6.0系统以下，可以通过Setting.Secure.ALLOW_MOCK_LOCATION获取是否【允许模拟位置】，
     * 当【允许模拟位置】开启时，可addTestProvider；
     * Android 6.0系统及以上，弃用Setting.Secure.ALLOW_MOCK_LOCATION变量，没有【允许模拟位置】选项，
     * 增加【选择模拟位置信息应用】，此时需要选择当前应用，才可以addTestProvider，
     * 但未找到获取当前选择应用的方法，因此通过addTestProvider是否成功来判断是否可用模拟位置。
     */
    private boolean hasAddTestProvider = true;

    /**
     * 启动和停止模拟位置的标识
     */
    private boolean bRun = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSystemMockPositionStatus = (TextView) findViewById(R.id.tv_system_mock_position_status);
        btnStartMock = (Button) findViewById(R.id.btn_start_mock);
        btnStopMock = (Button) findViewById(R.id.btn_stop_mock);
        tvProvider = (TextView) findViewById(R.id.tv_provider);
        tvTime = (TextView) findViewById(R.id.tv_time);
        tvLatitude = (TextView) findViewById(R.id.tv_latitude);
        tvLongitude = (TextView) findViewById(R.id.tv_longitude);
        tvAltitude = (TextView) findViewById(R.id.tv_altitude);
        tvBearing = (TextView) findViewById(R.id.tv_bearing);
        tvSpeed = (TextView) findViewById(R.id.tv_speed);
        tvAccuracy = (TextView) findViewById(R.id.tv_accuracy);

        btnStartMock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getUseMockPosition()) {
                    bRun = true;
                    btnStartMock.setEnabled(false);
                    btnStopMock.setEnabled(true);
                }
            }
        });
        btnStopMock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bRun = false;
                stopMockLocation();
                btnStartMock.setEnabled(true);
                btnStopMock.setEnabled(false);
            }
        });

        initService(this);

        new Thread(new RunnableMockLocation()).start();

        Log.i("8888", "root:"+isRootSystem());
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        // 判断系统是否允许模拟位置，并addTestProvider
        if (getUseMockPosition() == false) {
            bRun = false;
            btnStartMock.setEnabled(false);

            btnStopMock.setEnabled(false);
            tvSystemMockPositionStatus.setText("未开启");
        } else {
            if (bRun) {
                btnStartMock.setEnabled(false);
                btnStopMock.setEnabled(true);
            } else {
                btnStartMock.setEnabled(true);
                btnStopMock.setEnabled(false);
            }
            tvSystemMockPositionStatus.setText("已开启");
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Log.i("KKKK", "ACCESS_FINE_LOCATION no");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }else{
            Log.i("KKKK", "ACCESS_FINE_LOCATION yes");
            //
            // 注册位置服务，获取系统位置
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }

    @Override
    protected void onPause() {
        locationManager.removeUpdates(locationListener);

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        bRun = false;
        stopMockLocation();

        super.onDestroy();
    }

    /**
     * 初始化服务
     * @param context
     */
    private void initService(Context context) {
        /**
         * 模拟位置服务
         */
        mockProviders = new ArrayList<>();
        mockProviders.add(LocationManager.GPS_PROVIDER);
//        mockProviders.add(LocationManager.NETWORK_PROVIDER);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // 防止程序意外终止，没有停止模拟GPS
        stopMockLocation();
    }

    /**
     * 模拟位置是否启用
     * 若启用，则addTestProvider
     */
    public boolean getUseMockPosition() {
        // Android 6.0以下，通过Setting.Secure.ALLOW_MOCK_LOCATION判断
        // Android 6.0及以上，需要【选择模拟位置信息应用】，未找到方法，因此通过addTestProvider是否可用判断
        boolean canMockPosition = (Settings.Secure.getInt(getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION, 0) != 0)
                || Build.VERSION.SDK_INT > 22;
        if (canMockPosition && hasAddTestProvider == false) {
            try {
                for (String providerStr : mockProviders) {
                    LocationProvider provider = locationManager.getProvider(providerStr);
                    if (provider != null) {
                        locationManager.addTestProvider(
                                provider.getName()
                                , provider.requiresNetwork()
                                , provider.requiresSatellite()
                                , provider.requiresCell()
                                , provider.hasMonetaryCost()
                                , provider.supportsAltitude()
                                , provider.supportsSpeed()
                                , provider.supportsBearing()
                                , provider.getPowerRequirement()
                                , provider.getAccuracy());
                    } else {
                        if (providerStr.equals(LocationManager.GPS_PROVIDER)) {
                            locationManager.addTestProvider(
                                    providerStr
                                    , true, true, false, false, true, true, true
                                    , Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
                        } else if (providerStr.equals(LocationManager.NETWORK_PROVIDER)) {
                            locationManager.addTestProvider(
                                    providerStr
                                    , true, false, true, false, false, false, false
                                    , Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
                        } else {
                            locationManager.addTestProvider(
                                    providerStr
                                    , false, false, false, false, true, true, true
                                    , Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
                        }
                    }
                    locationManager.setTestProviderEnabled(providerStr, true);
                    locationManager.setTestProviderStatus(providerStr, LocationProvider.AVAILABLE, null, System.currentTimeMillis());
                }
                hasAddTestProvider = true;  // 模拟位置可用
                canMockPosition = true;
            } catch (SecurityException e) {
                e.printStackTrace();
                canMockPosition = false;
            }
        }
        if (canMockPosition == false) {
            stopMockLocation();
        }
        return canMockPosition;
    }

    /**
     * 取消位置模拟，以免启用模拟数据后无法还原使用系统位置
     * 若模拟位置未开启，则removeTestProvider将会抛出异常；
     * 若已addTestProvider后，关闭模拟位置，未removeTestProvider将导致系统GPS无数据更新；
     */
    public void stopMockLocation() {
        if (hasAddTestProvider) {
            for (String provider : mockProviders) {
                try {
                    locationManager.removeTestProvider(provider);
                } catch (Exception ex) {
                    // 此处不需要输出日志，若未成功addTestProvider，则必然会出错
                    // 这里是对于非正常情况的预防措施
                }
            }
            hasAddTestProvider = false;
        }
    }

    /**
     * 模拟位置线程
     */
    private class RunnableMockLocation implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);

                    if (hasAddTestProvider == false) {
                        continue;
                    }

                    if (bRun == false) {
                        stopMockLocation();
                        continue;
                    }

                    try {
                        // 模拟位置（addTestProvider成功的前提下）
                        for (String providerStr : mockProviders) {
                            Location mockLocation = new Location(providerStr);
                            mockLocation.setLatitude(22);   // 维度（度）
                            mockLocation.setLongitude(113);  // 经度（度）
                            mockLocation.setAltitude(30);    // 高程（米）
                            mockLocation.setBearing(180);    // 方向（度）
                            mockLocation.setSpeed(10);    //速度（米/秒）
                            mockLocation.setAccuracy(0.1f);   // 精度（米）
                            mockLocation.setTime(new Date().getTime());   // 本地时间
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                                mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                            }
                            locationManager.setTestProviderLocation(providerStr, mockLocation);
                        }
                    } catch (Exception e) {
                        // 防止用户在软件运行过程中关闭模拟位置或选择其他应用
                        stopMockLocation();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvProvider.setText(location.getProvider());
                        tvTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(location.getTime())));
                        tvLatitude.setText(location.getLatitude() + " °");
                        tvLongitude.setText(location.getLongitude() + " °");
                        tvAltitude.setText(location.getAltitude() + " m");
                        tvBearing.setText(location.getBearing() + " °");
                        tvSpeed.setText(location.getSpeed() + " m/s");
                        tvAccuracy.setText(location.getAccuracy() + " m");
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 100: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i("KKKK", "ACCESS_FINE_LOCATION yes  1");
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    // 注册位置服务，获取系统位置
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

                } else {
                    Log.i("KKKK", "ACCESS_FINE_LOCATION no  1");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
                }
                return;
            }
        }
    }

    private final static int kSystemRootStateUnknow = -1;
    private final static int kSystemRootStateDisable = 0;
    private final static int kSystemRootStateEnable = 1;
    private static int systemRootState = kSystemRootStateUnknow;

    public int isRootSystem() {
        if (systemRootState == kSystemRootStateEnable) {
            return 1;
        } else if (systemRootState == kSystemRootStateDisable) {

            return 0;
        }
        File f = null;
        final String kSuSearchPaths[] = {"/system/bin/", "/system/xbin/", "/system/sbin/", "/sbin/", "/vendor/bin/"};
        try {
            for (int i = 0; i < kSuSearchPaths.length; i++) {
                f = new File(kSuSearchPaths[i] + "su");
                if (f != null && f.exists()) {
                    systemRootState = kSystemRootStateEnable;
                    return 1;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        systemRootState = kSystemRootStateDisable;
        return 0;
    }
}

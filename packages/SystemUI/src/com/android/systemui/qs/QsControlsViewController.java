/*
 * Copyright (C) 2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.qs;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settingslib.net.DataUsageController;
import com.android.settingslib.Utils;

import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.animation.Expandable;
import com.android.systemui.animation.view.LaunchableLinearLayout;
import com.android.systemui.lockscreen.ActivityLauncherUtils;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.bluetooth.qsdialog.BluetoothTileDialogViewModel;
import com.android.systemui.qs.tiles.dialog.InternetDialogManager;
import com.android.systemui.qs.VerticalSlider;
import com.android.systemui.statusbar.connectivity.AccessPointController;
import com.android.systemui.statusbar.connectivity.IconState;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.statusbar.connectivity.MobileDataIndicators;
import com.android.systemui.statusbar.connectivity.WifiIndicators;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BluetoothController.Callback;
import com.android.systemui.statusbar.policy.ConfigurationController;

import com.android.internal.util.android.VibrationUtils;

import java.util.Arrays;
import java.util.List;

public class QsControlsViewController {

    public static final int BT_ACTIVE = R.drawable.qs_bluetooth_icon_on;
    public static final int BT_INACTIVE = R.drawable.qs_bluetooth_icon_off;
    public static final int DATA_ACTIVE = R.drawable.ic_signal_cellular_alt_24;
    public static final int DATA_INACTIVE = R.drawable.ic_mobiledata_off_24;
    public static final int WIFI_ACTIVE = R.drawable.ic_wifi_24;
    public static final int WIFI_INACTIVE = R.drawable.ic_wifi_off_24;

    public static final int BT_LABEL_INACTIVE = R.string.quick_settings_bluetooth_label;
    public static final int DATA_LABEL_INACTIVE = R.string.quick_settings_data_label;
    public static final int INTERNET_LABEL_INACTIVE = R.string.quick_settings_internet_label;
    public static final int WIFI_LABEL_INACTIVE = R.string.quick_settings_wifi_label;

    private View mView, mTilesLayout, mSlidersLayout;

    private LaunchableLinearLayout mInternetButton, mBtButton;

    private VerticalSlider mBrightnessSlider, mVolumeSlider;

    private final AccessPointController mAccessPointController;
    private final ActivityStarter mActivityStarter;
    private final ActivityLauncherUtils mActivityLauncherUtils;
    private final NetworkController mNetworkController;
    private final BluetoothController mBluetoothController;
    private final BluetoothTileDialogViewModel mBluetoothTileDialogViewModel;
    private final DataUsageController mDataController;
    private final InternetDialogManager mInternetDialogManager;
    private final ConfigurationController mConfigurationController;

    protected final CellSignalCallback mCellSignalCallback = new CellSignalCallback();
    protected final WifiSignalCallback mWifiSignalCallback = new WifiSignalCallback();

    private int mAccentColor, mBgColor, mTintColor;
    
    private final Context mContext;
    private boolean mInflated = false;

    private ConfigurationController.ConfigurationListener mConfigurationListener =
            new ConfigurationController.ConfigurationListener() {
                @Override
                public void onThemeChanged() {
                    updateResources();
                }
                @Override
                public void onUiModeChanged() {
                    updateResources();
                }
                @Override
                public void onConfigChanged(Configuration newConfig) {
                    updateResources();
                }
            };

    public QsControlsViewController(View view) {
        mView = view;
        mContext = mView.getContext();
        mActivityLauncherUtils = new ActivityLauncherUtils(mContext);
        mActivityStarter = Dependency.get(ActivityStarter.class);
        mAccessPointController = Dependency.get(AccessPointController.class);
        mBluetoothController = Dependency.get(BluetoothController.class);
        mBluetoothTileDialogViewModel = Dependency.get(BluetoothTileDialogViewModel.class);
        mInternetDialogManager = Dependency.get(InternetDialogManager.class);
        mNetworkController = Dependency.get(NetworkController.class);
        mDataController = mNetworkController.getMobileDataController();
        mConfigurationController = Dependency.get(ConfigurationController.class);
    }

    public void onFinishInflate() {
        mInflated = true;
        mTilesLayout = mView.findViewById(R.id.qs_controls_tiles);
        mSlidersLayout = mView.findViewById(R.id.qs_controls_sliders);
        mBrightnessSlider = mSlidersLayout.findViewById(R.id.qs_controls_brightness_slider);
        mVolumeSlider = mSlidersLayout.findViewById(R.id.qs_controls_volume_slider);
        mInternetButton = mTilesLayout.findViewById(R.id.internet_btn);
        mBtButton = mTilesLayout.findViewById(R.id.bt_btn);
    }

    public void onAttachedToWindow() {
        if (!mInflated) {
            return;
        }
        setClickListeners();
        updateResources();
        mBluetoothController.addCallback(mBtCallback);
        mNetworkController.addCallback(mWifiSignalCallback);
        mNetworkController.addCallback(mCellSignalCallback);
        mConfigurationController.addCallback(mConfigurationListener);
    }
    
    public void onDetachedFromWindow() {
        mBluetoothController.removeCallback(mBtCallback);
        mNetworkController.removeCallback(mWifiSignalCallback);
        mNetworkController.removeCallback(mCellSignalCallback);
        mConfigurationController.removeCallback(mConfigurationListener);
    }

    private void setClickListeners() {
        mBtButton.setOnClickListener(view -> toggleBluetoothState());
        mBtButton.setOnLongClickListener(v -> { showBluetoothDialog(v); return true; });
        mInternetButton.setOnClickListener(view -> showInternetDialog(view));
        mInternetButton.setOnLongClickListener(v -> { mActivityLauncherUtils.startIntent(new Intent(Settings.ACTION_WIFI_SETTINGS)); return true; });
    }

    public void updateColors() {
        mAccentColor = mContext.getResources().getColor(isNightMode() ? 
            R.color.qs_controls_active_color_dark : R.color.lockscreen_widget_active_color_light);
        mBgColor = mContext.getResources().getColor(isNightMode() ? 
            R.color.qs_controls_bg_color_dark : R.color.qs_controls_bg_color_light);
        mTintColor = mContext.getResources().getColor(isNightMode() ? 
            R.color.qs_controls_bg_color_light : R.color.qs_controls_bg_color_dark);
    }
    
    private boolean isNightMode() {
        // on default qs we always use dark mode
        return true;
    }
    
    private int qsPanelStyle() {
        return Settings.System.getIntForUser(mContext.getContentResolver(), 
            Settings.System.QS_PANEL_STYLE, 0, UserHandle.USER_CURRENT);
    }
    
    private boolean translucentQsStyle() {
        int[] translucentStyles = {1, 2, 3};
        return Arrays.stream(translucentStyles).anyMatch(style -> style == qsPanelStyle());
    }

    public void updateResources() {
        if (mBrightnessSlider != null && mVolumeSlider != null) {
            mBrightnessSlider.updateSliderPaint();
            mVolumeSlider.updateSliderPaint();
        }
        updateColors();
    }
    
    private void updateTileButtonState(LaunchableLinearLayout tile, boolean active,
                    int activeResource, int inactiveResource, 
                    String activeString, String inactiveString) {
        mView.post(new Runnable() {
            @Override
            public void run() {
                if (tile != null) {
                    ImageView tileIcon = null;
                    TextView tileLabel = null;
                    if (tile.getId() == R.id.internet_btn) {
                        tileIcon = tile.findViewById(R.id.internet_btn_icon);
                        tileLabel = tile.findViewById(R.id.internet_btn_text);
                    } else if (tile.getId() == R.id.bt_btn) {
                        tileIcon = tile.findViewById(R.id.bt_btn_icon);
                        tileLabel = tile.findViewById(R.id.bt_btn_text);
                    }
                    tileIcon.setImageDrawable(mContext.getDrawable(active ? activeResource : inactiveResource));
                    tileLabel.setText(active ? activeString : inactiveString);
                    setButtonActiveState(tile, active);
                }
            }
        });
    }
    
    private void setButtonActiveState(LaunchableLinearLayout tile, boolean active) {
        int accentColor = translucentQsStyle() ? Utils.applyAlpha(0.2f, mAccentColor) : mAccentColor;
        int bgColor = translucentQsStyle() ? Utils.applyAlpha(0.8f, mBgColor) : mBgColor;
        int activeTintColor = translucentQsStyle() ? mAccentColor : mBgColor;
        int bgTint = active ? accentColor : bgColor;
        int inactiveTintColor = translucentQsStyle() ? mAccentColor : mTintColor;
        int tintColor = active ? activeTintColor : inactiveTintColor;
        ImageView tileIcon = null;
        ImageView chevron = null;
        TextView tileLabel = null;
        if (tile != null) {
            if (tile.getId() == R.id.internet_btn) {
                tileIcon = tile.findViewById(R.id.internet_btn_icon);
                tileLabel = tile.findViewById(R.id.internet_btn_text);
                chevron = tile.findViewById(R.id.internet_btn_arrow);
            } else if (tile.getId() == R.id.bt_btn) {
                tileIcon = tile.findViewById(R.id.bt_btn_icon);
                tileLabel = tile.findViewById(R.id.bt_btn_text);
                chevron = tile.findViewById(R.id.bt_btn_arrow);
            }
            chevron.setImageTintList(ColorStateList.valueOf(tintColor));
            tileIcon.setImageTintList(ColorStateList.valueOf(tintColor));
            tileLabel.setTextColor(ColorStateList.valueOf(tintColor));
            tile.setBackgroundTintList(ColorStateList.valueOf(bgTint));
        }
    }

    private void showInternetDialog(View view) {
        mView.post(() -> mInternetDialogManager.create(true,
                mAccessPointController.canConfigMobileData(),
                mAccessPointController.canConfigWifi(), Expandable.fromView(view)));
        VibrationUtils.triggerVibration(mContext, 2);
    }

    private final BluetoothController.Callback mBtCallback = new BluetoothController.Callback() {
        @Override
        public void onBluetoothStateChange(boolean enabled) {
            updateBtState();
        }

        @Override
        public void onBluetoothDevicesChanged() {
            updateBtState();
        }
    };
    
    private void toggleBluetoothState() {
        mBluetoothController.setBluetoothEnabled(!isBluetoothEnabled());
        updateBtState();
        mView.postDelayed(() -> {
            updateBtState();
        }, 250);
    }
    
    private void showBluetoothDialog(View view) {
        mView.post(() -> 
            mBluetoothTileDialogViewModel.showDialog(Expandable.fromView(view)));
        VibrationUtils.triggerVibration(mContext, 2);
    }
    
    private void updateInternetButtonState() {
        boolean wifiEnabled = mWifiSignalCallback.mInfo.enabled;
        boolean dataEnabled = mCellSignalCallback.mInfo.enabled;
        if (wifiEnabled) {
            updateWiFiButtonState();
        } else if (dataEnabled) {
            updateMobileDataState();
        } else {
            String inactiveString = mContext.getResources().getString(INTERNET_LABEL_INACTIVE);
            updateTileButtonState(mInternetButton, false, DATA_INACTIVE, DATA_INACTIVE, inactiveString, inactiveString);
        }
    }

    private void updateWiFiButtonState() {;
        final WifiCallbackInfo cbi = mWifiSignalCallback.mInfo;
        String inactiveString = mContext.getResources().getString(WIFI_LABEL_INACTIVE);
        updateTileButtonState(mInternetButton, true, 
            WIFI_ACTIVE, WIFI_INACTIVE, cbi.ssid != null ? removeDoubleQuotes(cbi.ssid) : inactiveString, inactiveString);
    }

    private void updateMobileDataState() {
        String networkName = mNetworkController == null ? "" : mNetworkController.getMobileDataNetworkName();
        boolean hasNetwork = !TextUtils.isEmpty(networkName) && mNetworkController != null 
            && mNetworkController.hasMobileDataFeature();
        String inactiveString = mContext.getResources().getString(DATA_LABEL_INACTIVE);
        updateTileButtonState(mInternetButton, true, 
            DATA_ACTIVE, DATA_INACTIVE, hasNetwork ? networkName : inactiveString, inactiveString);
    }
    
    private void updateBtState() {
        String deviceName = isBluetoothEnabled() ? mBluetoothController.getConnectedDeviceName() : "";
        boolean isConnected = !TextUtils.isEmpty(deviceName);
        String inactiveString = mContext.getResources().getString(BT_LABEL_INACTIVE);
        updateTileButtonState(mBtButton, isBluetoothEnabled(), 
            BT_ACTIVE, BT_INACTIVE, isConnected ? deviceName : inactiveString, inactiveString);
    }
    
    private boolean isBluetoothEnabled() {
        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    private boolean isMobileDataEnabled() {
        return mDataController.isMobileDataEnabled();
    }

    @Nullable
    private static String removeDoubleQuotes(String string) {
        if (string == null) return null;
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    protected static final class CellCallbackInfo {
        boolean enabled;
    }

    protected static final class WifiCallbackInfo {
        boolean enabled;
        @Nullable
        String ssid;
    }

    protected final class WifiSignalCallback implements SignalCallback {
        final WifiCallbackInfo mInfo = new WifiCallbackInfo();
        @Override
        public void setWifiIndicators(@NonNull WifiIndicators indicators) {
            if (indicators.qsIcon == null) {
                mInfo.enabled = false;
                return;
            }
            mInfo.enabled = indicators.enabled;
            mInfo.ssid = indicators.description;
            updateInternetButtonState();
        }
    }
    
    private final class CellSignalCallback implements SignalCallback {
        final CellCallbackInfo mInfo = new CellCallbackInfo();
        @Override
        public void setMobileDataIndicators(@NonNull MobileDataIndicators indicators) {
            if (indicators.qsIcon == null) {
                mInfo.enabled = false;
                return;
            }
            mInfo.enabled = isMobileDataEnabled();
            updateInternetButtonState();
        }
        @Override
        public void setNoSims(boolean show, boolean simDetected) {
            mInfo.enabled = simDetected && isMobileDataEnabled();
            updateInternetButtonState();
        }
        @Override
        public void setIsAirplaneMode(@NonNull IconState icon) {
            mInfo.enabled = !icon.visible && isMobileDataEnabled();
            updateInternetButtonState();
        }
    }
}

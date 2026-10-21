package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Collections;
import java.util.Map;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * The same service also covers builds that will not hand Home over at all (Fire TV: the stock
 * launcher is a protected package, set-home is accepted but ignored, and the remote's plain Home
 * key never reaches key filtering). There onAccessibilityEvent watches for the stock Home window
 * coming to the front and covers it with this launcher — the Launcher Manager trick. The stock
 * screen flashes briefly first; that much is inherent to the technique. Matching is by
 * package, not activity: some builds report window class names that differ from the Home
 * activity's own, and an activity match then silently never fires. Other screens the stock
 * package hosts are covered too — on Fire TV that package is the stock home and little else.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same map InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    /** The stock launcher's Home activity, resolved at connect time for the cover watcher below.
     *  Null when this launcher already owns Home, or when no other Home activity resolves. */
    private ComponentName stockHome;
    private long lastCoverMs;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        stockHome = resolveStockHome();
    }

    /** The stock Home activity: the registered default when there is one, else the system
     *  Home activity that is not this launcher's (Fire TV registers no default at all). */
    private ComponentName resolveStockHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (info != null && info.activityInfo != null
                && !info.activityInfo.packageName.equals(getPackageName())
                && !"android".equals(info.activityInfo.packageName)) {
            return new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        }
        for (ResolveInfo candidate : getPackageManager().queryIntentActivities(home, 0)) {
            if (candidate.activityInfo == null) continue;
            if (candidate.activityInfo.packageName.equals(getPackageName())) continue;
            if ((candidate.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) continue;
            return new ComponentName(candidate.activityInfo.packageName, candidate.activityInfo.name);
        }
        return null;
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : new Intent(Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        String app = appKeys.get(event.getKeyCode());
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, app);
            return true;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (stockHome == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !stockHome.getPackageName().contentEquals(pkg)) return;
        long now = System.currentTimeMillis();
        if (now - lastCoverMs < 1500) return;   // one cover per arrival, not per event in the burst
        lastCoverMs = now;
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
    }

    @Override
    public void onInterrupt() {
    }
}

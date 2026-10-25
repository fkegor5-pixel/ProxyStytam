package com.proxystytam.launcher;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private TextView clockView, dateView, greetingView, dockAppName;
    private TextView pillInputs, pillWifi, pillSettings;
    private RecyclerView dockRecycler, appsGrid;
    private FrameLayout dockContainer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> dockApps = new ArrayList<>();
    private int columns;

    // Navigation state
    private enum Section { TOP_BAR, DOCK, GRID }
    private Section currentSection = Section.DOCK;
    private boolean gridVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        clockView = findViewById(R.id.clock);
        dateView = findViewById(R.id.date_text);
        greetingView = findViewById(R.id.greeting);
        dockAppName = findViewById(R.id.dock_app_name);
        pillInputs = findViewById(R.id.pill_inputs);
        pillWifi = findViewById(R.id.pill_wifi);
        pillSettings = findViewById(R.id.pill_settings);
        dockRecycler = findViewById(R.id.dock_recycler);
        appsGrid = findViewById(R.id.apps_grid);
        dockContainer = findViewById(R.id.dock_container);

        columns = getResources().getInteger(R.integer.columns);

        setupClock();
        setupWifiName();
        setupPills();
        loadApps();
        setupDock();
        setupGrid();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        updateWifiName();
        loadApps();
        setupDock();
        setupGrid();
    }

    // ── Clock ──

    private void setupClock() {
        updateClock();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateClock();
                handler.postDelayed(this, 30_000);
            }
        }, 30_000);
    }

    private void updateClock() {
        Date now = new Date();
        clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));

        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM", new Locale("ru"));
        String dateStr = dateFmt.format(now);
        dateView.setText(dateStr.substring(0, 1).toUpperCase() + dateStr.substring(1));

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) greetingView.setText("Доброе утро");
        else if (hour >= 12 && hour < 17) greetingView.setText("Добрый день");
        else if (hour >= 17 && hour < 22) greetingView.setText("Добрый вечер");
        else greetingView.setText("Доброй ночи");
    }

    // ── Wi-Fi ──

    private void setupWifiName() {
        updateWifiName();
    }

    private void updateWifiName() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                WifiInfo info = wm.getConnectionInfo();
                String ssid = info.getSSID();
                if (ssid != null && !ssid.equals("<unknown ssid>")) {
                    ssid = ssid.replace("\"", "");
                    pillWifi.setText(ssid);
                    return;
                }
            }
        } catch (Exception ignored) {}
        pillWifi.setText("Wi-Fi");
    }

    // ── Pills (top right buttons) ──

    private void setupPills() {
        pillInputs.setOnClickListener(v -> openInputs());
        pillWifi.setOnClickListener(v -> openWifiSettings());
        pillSettings.setOnClickListener(v -> openSettings());

        View.OnFocusChangeListener pillFocus = (v, hasFocus) -> {
            if (hasFocus) currentSection = Section.TOP_BAR;
            animateScale(v, hasFocus ? 1.1f : 1.0f);
        };
        pillInputs.setOnFocusChangeListener(pillFocus);
        pillWifi.setOnFocusChangeListener(pillFocus);
        pillSettings.setOnFocusChangeListener(pillFocus);
    }

    private void openInputs() {
        try {
            // Try TvInputManager settings
            Intent i = new Intent("com.android.tv.action.VIEW_INPUTS");
            if (i.resolveActivity(getPackageManager()) != null) {
                startActivity(i);
                return;
            }
            // Fallback: TV settings
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void openWifiSettings() {
        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    private void openSettings() {
        // Try Android TV settings first
        try {
            Intent i = new Intent();
            i.setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings");
            startActivity(i);
            return;
        } catch (Exception ignored) {}
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    // ── App loading ──

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackApps = pm.queryIntentActivities(leanback, 0);

        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launcherApps = pm.queryIntentActivities(launcher, 0);

        Set<String> seen = new HashSet<>();
        allApps.clear();
        String myPkg = getPackageName();

        // Prefer leanback activities
        for (ResolveInfo ri : leanbackApps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(myPkg) || seen.contains(pkg)) continue;
            seen.add(pkg);
            allApps.add(new AppInfo(
                ri.loadLabel(pm).toString(),
                pkg,
                ri.activityInfo.name,
                ri.activityInfo.loadBanner(pm) != null ? ri.activityInfo.loadBanner(pm) : ri.loadIcon(pm)
            ));
        }
        for (ResolveInfo ri : launcherApps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(myPkg) || seen.contains(pkg)) continue;
            seen.add(pkg);
            allApps.add(new AppInfo(
                ri.loadLabel(pm).toString(),
                pkg,
                ri.activityInfo.name,
                ri.loadIcon(pm)
            ));
        }

        // Build dock list from config
        String dockConfig = getString(R.string.dock_apps);
        String[] dockPkgs = dockConfig.split(",");
        dockApps.clear();
        for (String dp : dockPkgs) {
            dp = dp.trim();
            if (dp.isEmpty()) continue;
            for (AppInfo app : allApps) {
                if (app.packageName.equals(dp)) {
                    dockApps.add(app);
                    break;
                }
            }
        }
    }

    // ── Dock ──

    private void setupDock() {
        LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        dockRecycler.setLayoutManager(lm);
        dockRecycler.setAdapter(new DockAdapter());
        dockRecycler.requestFocus();
    }

    // ── Grid ──

    private void setupGrid() {
        GridLayoutManager glm = new GridLayoutManager(this, columns);
        appsGrid.setLayoutManager(glm);
        // Grid shows all apps except dock apps
        List<AppInfo> gridApps = new ArrayList<>();
        Set<String> dockPkgs = new HashSet<>();
        for (AppInfo d : dockApps) dockPkgs.add(d.packageName);
        for (AppInfo a : allApps) {
            if (!dockPkgs.contains(a.packageName)) gridApps.add(a);
        }
        appsGrid.setAdapter(new GridAdapter(gridApps));
    }

    private void showGrid() {
        if (!gridVisible) {
            gridVisible = true;
            appsGrid.setVisibility(View.VISIBLE);
            dockContainer.animate().translationY(dockContainer.getHeight() + 40).setDuration(250);
            dockAppName.setVisibility(View.GONE);
        }
    }

    private void hideGrid() {
        if (gridVisible) {
            gridVisible = false;
            appsGrid.setVisibility(View.GONE);
            dockContainer.animate().translationY(0).setDuration(250);
        }
    }

    // ── Key navigation ──

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && currentSection == Section.DOCK && !gridVisible) {
            showGrid();
            appsGrid.requestFocus();
            currentSection = Section.GRID;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && currentSection == Section.GRID) {
            // If at top of grid, go back to dock
            GridLayoutManager glm = (GridLayoutManager) appsGrid.getLayoutManager();
            if (glm != null && glm.findFirstVisibleItemPosition() < columns) {
                hideGrid();
                dockRecycler.requestFocus();
                currentSection = Section.DOCK;
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && currentSection == Section.DOCK) {
            pillInputs.requestFocus();
            currentSection = Section.TOP_BAR;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && currentSection == Section.TOP_BAR) {
            dockRecycler.requestFocus();
            currentSection = Section.DOCK;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (gridVisible) {
                hideGrid();
                dockRecycler.requestFocus();
                currentSection = Section.DOCK;
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ── Animation ──

    private void animateScale(View v, float scale) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
            ObjectAnimator.ofFloat(v, "scaleX", scale),
            ObjectAnimator.ofFloat(v, "scaleY", scale)
        );
        set.setDuration(150);
        set.start();
    }

    // ── Launch app ──

    private void launchApp(AppInfo app) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(app.packageName, app.activityName);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            // Fallback: launch by package
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(app.packageName);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (i != null) startActivity(i);
        }
    }

    // ── Data class ──

    static class AppInfo {
        String label, packageName, activityName;
        Drawable icon;
        AppInfo(String label, String packageName, String activityName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.activityName = activityName;
            this.icon = icon;
        }
    }

    // ── Dock adapter ──

    class DockAdapter extends RecyclerView.Adapter<DockAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.dock_icon);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_dock, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AppInfo app = dockApps.get(pos);
            h.icon.setImageDrawable(app.icon);
            h.itemView.setOnClickListener(v -> launchApp(app));

            h.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                currentSection = Section.DOCK;
                animateScale(v, hasFocus ? 1.15f : 1.0f);
                if (hasFocus) {
                    dockAppName.setText(app.label);
                    dockAppName.setVisibility(View.VISIBLE);
                    // Ambient glow color from icon — use a subtle white for now
                    dockAppName.setShadowLayer(32, 0, 0, 0x66FFFFFF);
                } else if (!hasFocusInDock()) {
                    dockAppName.setVisibility(View.GONE);
                }
            });
        }

        @Override public int getItemCount() { return dockApps.size(); }

        private boolean hasFocusInDock() {
            for (int i = 0; i < dockRecycler.getChildCount(); i++) {
                if (dockRecycler.getChildAt(i).isFocused()) return true;
            }
            return false;
        }
    }

    // ── Grid adapter ──

    class GridAdapter extends RecyclerView.Adapter<GridAdapter.VH> {
        private final List<AppInfo> apps;
        GridAdapter(List<AppInfo> apps) { this.apps = apps; }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.app_icon);
                name = v.findViewById(R.id.app_name);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_app, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AppInfo app = apps.get(pos);
            h.icon.setImageDrawable(app.icon);
            h.name.setText(app.label);
            h.itemView.setOnClickListener(v -> launchApp(app));

            h.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                currentSection = Section.GRID;
                animateScale(v, hasFocus ? 1.12f : 1.0f);
                // Show label when focused
                h.name.setTextColor(hasFocus ? 0xDDFFFFFF : 0x00FFFFFF);
            });
        }

        @Override public int getItemCount() { return apps.size(); }
    }
}

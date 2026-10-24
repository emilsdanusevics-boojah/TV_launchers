package com.minimal.tvlauncher;

import android.view.KeyEvent;
import android.view.View;

import java.util.List;

/** One look for the home screen. MainActivity shows the one the user picked. */
interface LauncherTheme {

    View createView();

    void setApps(List<Apps.Entry> apps);

    /** The minute, date or time format changed. */
    void onTimeChanged();

    void onResume();

    void onPause();

    /** Home was pressed while already home. */
    void onHome();

    boolean onKeyDown(int keyCode, KeyEvent event);

    /** Returns true if Back was used (e.g. to close a menu). */
    boolean onBack();

    void onDestroy();
}

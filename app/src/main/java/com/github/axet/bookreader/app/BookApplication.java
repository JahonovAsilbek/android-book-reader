package com.github.axet.bookreader.app;

import android.content.Context;

import com.github.axet.androidlibrary.app.MainApplication;
import com.github.axet.androidlibrary.net.HttpClient;
import com.github.axet.bookreader.R;

import org.geometerplus.zlibrary.ui.android.library.ZLAndroidApplication;

public class BookApplication extends MainApplication {
    public static String PREFERENCE_THEME = "theme";
    public static String PREFERENCE_FONTFAMILY_FBREADER = "fontfamily_fb";
    public static String PREFERENCE_FONTSIZE_FBREADER = "fontsize_fb";
    public static String PREFERENCE_FONTSIZE_REFLOW = "fontsize_reflow";
    public static float PREFERENCE_FONTSIZE_REFLOW_DEFAULT = 0.8f;
    public static String PREFERENCE_LIBRARY_LAYOUT = "layout_";
    public static String PREFERENCE_SCREENLOCK = "screen_lock";
    public static String PREFERENCE_VOLUME_KEYS = "volume_keys";
    public static String PREFERENCE_LAST_PATH = "last_path";
    public static String PREFERENCE_ROTATE = "rotate";
    public static String PREFERENCE_VIEW_MODE = "view_mode";
    public static String PREFERENCE_STORAGE = "storage_path";
    public static String PREFERENCE_SORT = "sort";
    public static String PREFERENCE_LANGUAGE = "tts_pref";

    public ZLAndroidApplication zlib;

    public static int getTheme(Context context, int light, int dark) {
        return MainApplication.getTheme(context, PREFERENCE_THEME, light, dark);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        zlib = new ZLAndroidApplication() {
            {
                attachBaseContext(BookApplication.this);
                onCreate();
            }
        };
    }
}

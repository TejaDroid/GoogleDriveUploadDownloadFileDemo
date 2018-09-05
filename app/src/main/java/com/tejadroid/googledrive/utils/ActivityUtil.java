package com.tejadroid.googledrive.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.Toast;

import com.tejadroid.googledrive.R;

/**
 * Created by Droid on 11-04-2017.
 */
public class ActivityUtil {

    public static void showToast(Context mContext, String message) {
        if (mContext != null && isNotEmpty(message)) {
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void showLongToast(Context mContext, String message) {
        if (isNotEmpty(message)) {
            Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
        }
    }

    public static boolean isNotEmpty(String string) {
        return string != null && !string.isEmpty();
    }


    public static float dp2px(Resources resources, float dp) {
        final float scale = resources.getDisplayMetrics().density;
        return dp * scale + 0.5f;
    }

    public static float sp2px(Resources resources, float sp) {
        final float scale = resources.getDisplayMetrics().scaledDensity;
        return sp * scale;
    }
}

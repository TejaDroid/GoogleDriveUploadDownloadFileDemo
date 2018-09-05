package com.tejadroid.googledrive.utils;

import android.os.Environment;

/**
 * Created by Tejas on 1/11/18.
 */

public class Constant {

    public static final int REQUEST_CODE_SIGN_IN = 0;
    public static final int REQUEST_CODE_OPEN_ITEM = 1;
    public static final int REQUEST_CODE_PICKFILE = 2;

    public static final Boolean PrintLog = true;

    public static final String TAG = "Google drive";

    public static final String DOWNLOAD_PATH = Environment.getExternalStorageDirectory() + "/DroidDrive/Download";
    public static final String UPLOAD_PATH = Environment.getExternalStorageDirectory() + "/DroidDrive/Upload";

}

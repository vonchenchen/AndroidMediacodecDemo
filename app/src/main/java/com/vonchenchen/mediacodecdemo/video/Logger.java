package com.vonchenchen.mediacodecdemo.video;

import android.util.Log;

/**
 * Created by vonchenchen on 2018/4/15.
 */

public class Logger {

    public static void d(String TAG, String msg){
        Log.d(TAG, msg);
    }

    public static void i(String TAG, String msg){
        Log.i(TAG, msg);
    }

    public static void v(String TAG, String msg){
        Log.v(TAG, msg);
    }

    public static void w(String TAG, String msg){
        Log.w(TAG, msg);
    }

    public static void e(String TAG, String msg){
        Log.e(TAG, msg);
    }

    public static void printErrStackTrace(String TAG, Exception e, String msg){
        Log.e(TAG, msg+e.toString());
    }

    private void writeMsg(){

    }
}

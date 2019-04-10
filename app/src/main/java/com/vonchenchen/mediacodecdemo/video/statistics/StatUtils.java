package com.vonchenchen.mediacodecdemo.video.statistics;

import android.content.pm.PackageManager;

import com.vonchenchen.mediacodecdemo.video.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;

public class StatUtils {

    private static final String TAG = "StatUtils";

    public static void getCpuRate(){

        try {
            Process p = Runtime.getRuntime().exec("top |grep vonchenchen");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String str = br.readLine();

            Logger.i(TAG, "lidechen_test str="+str);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package com.vonchenchen.demo;

public class YuvUtils {

    static {
        System.loadLibrary("demo");
    }

    public static native void testARGBtoI420(byte[] argbData, int width, int heigh);
}

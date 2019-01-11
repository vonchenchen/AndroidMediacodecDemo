package com.vonchenchen.mediacodecdemo.video;

public class Utils {

    public static String byteArrayToHexString(byte[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }

        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ; i++) {
            b.append(Integer.toHexString(0x000000ff & a[i]));
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static int getLittleEndianUint32(byte[] data, int offset){
        int ret = 0;
        ret += (((short)data[offset]) & 0x00ff);
        ret += ((short)data[offset+1] & 0x00ff) << 8;
        ret += ((short)data[offset+2] & 0x00ff) << 16;
        ret += ((short)data[offset+3] & 0x00ff) << 24;
        return ret;
    }
}

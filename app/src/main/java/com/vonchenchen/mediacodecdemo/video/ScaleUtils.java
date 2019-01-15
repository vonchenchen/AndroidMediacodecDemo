package com.vonchenchen.mediacodecdemo.video;

import android.widget.RelativeLayout;

public class ScaleUtils {

    private static final String TAG = "ScaleUtils";

    public static Param getScale(int outWidth, int outHeight, int imageWidth, int imageHeight) {

        int width, height;
        Param params = new Param();

        if (imageWidth <= 0 || imageHeight <= 0) {
            Logger.e(TAG, "setScale fail imageHeight=" + imageHeight + " imageWidth " + imageWidth);
            return null;
        }

        if (outWidth <= 1 || outHeight <= 1) {
            params.width = 1;
            params.height = 1;
        } else {
            //视频帧比例
            float k = ((float) imageHeight) / imageWidth;
            //外轮廓比例
            float ok = ((float) outHeight) / outWidth;

            if (outWidth > outHeight) {
                //宽大于高
                if (k > ok) {
                    //按比例 外框比帧宽
                    height = outHeight;
                    width = (int) (outHeight / k);
                } else {
                    //按比例 外框比帧窄
                    width = outWidth;
                    height = (int) (outWidth * k);
                }
            } else {
                if (k < ok) {
                    width = outWidth;
                    height = (int) (outWidth * k);
                } else {
                    height = outHeight;
                    width = (int) (outHeight / k);
                }
            }

            params.width = width;
            params.height = height;
        }
        return params;
    }

    public static class Param{
        public int width;
        public int height;
    }
}

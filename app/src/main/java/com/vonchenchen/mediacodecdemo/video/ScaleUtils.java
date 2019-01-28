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

    /**
     * 根据屏幕宽高与帧宽高重新计算VertexMat 将图片等比例画入控件框中
     * @param displayViewWidth
     * @param displayViewHeight
     * @param frameWidth
     * @param frameHeight
     * @return
     */
    public static float[] getScaleVertexMat(int displayViewWidth, int displayViewHeight, int frameWidth, int frameHeight){

        //通过修改顶点坐标 将采集到的视频按比例缩放到窗口中
        ScaleUtils.Param param = ScaleUtils.getScale(displayViewWidth, displayViewHeight, frameWidth, frameHeight);
        float scaleWidth = ((float) param.width)/displayViewWidth;
        float scaleHeight = ((float) param.height)/displayViewHeight;
        float[] drawMatrix = new float[8];
        if(scaleWidth == 1){
            float halfHeight = scaleHeight;
            drawMatrix[0] = -1;
            drawMatrix[1] = -halfHeight;
            drawMatrix[2] = 1;
            drawMatrix[3] = -halfHeight;
            drawMatrix[4] = -1;
            drawMatrix[5] = halfHeight;
            drawMatrix[6] = 1;
            drawMatrix[7] = halfHeight;
        }else{
            float halfWidth = scaleWidth;
            drawMatrix[0] = -halfWidth;
            drawMatrix[1] = -1;
            drawMatrix[2] = halfWidth;
            drawMatrix[3] = -1;
            drawMatrix[4] = -halfWidth;
            drawMatrix[5] = 1;
            drawMatrix[6] = halfWidth;
            drawMatrix[7] = 1;
        }
        return drawMatrix;
    }
}

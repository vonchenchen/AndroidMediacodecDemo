package com.vonchenchen.mediacodecdemo.video;

import android.graphics.SurfaceTexture;
import android.view.SurfaceView;

import com.vonchenchen.mediacodecdemo.video.statistics.StatisticsData;

/**
 * Created by vonchenchen on 2018/4/15.
 */

public class VideoEncoderWrapper {

    private EncodeTask mEncodeTask;

    private String mPath;

    private OnVideoEncodeEventListener mOnVideoEncodeEventListener = null;

    /**
     * 开始采集并渲染和编码
     * @param surfaceView
     * @param width    图像宽度
     * @param height   图像高度
     * @param frameRate
     */
    public void startEncode(SurfaceView surfaceView, int width, int height, int frameRate, EncodeInfo encodeInfo){

        mEncodeTask = new EncodeTask(surfaceView, width, height, frameRate, new SimpleEncoder.OnCricularEncoderEventListener() {
            @Override
            public void onConfigFrameReceive(byte[] data, int length, int videoWidth, int videoHeight) {
                if(mOnVideoEncodeEventListener != null){
                    mOnVideoEncodeEventListener.onConfigFrameRecv(data, length, videoWidth, videoHeight);
                }
            }

            @Override
            public void onKeyFrameReceive(byte[] data, int length, int videoWidth, int videoHeight) {
                if(mOnVideoEncodeEventListener != null){
                    mOnVideoEncodeEventListener.onKeyFrameRecv(data, length, videoWidth, videoHeight);
                }
            }

            @Override
            public void onOtherFrameReceive(byte[] data, int length, int videoWidth, int videoHeight) {
                if(mOnVideoEncodeEventListener != null){
                    mOnVideoEncodeEventListener.onOtherFrameRecv(data, length, videoWidth, videoHeight);
                }
            }

            @Override
            public void onStatisticsUpdate(StatisticsData statisticsData) {
                if(mOnVideoEncodeEventListener != null){
                    mOnVideoEncodeEventListener.onStatisticsUpdate(statisticsData);
                }
            }
        });

        mEncodeTask.setOnEncodeTaskEventListener(new EncodeTask.OnEncodeTaskEventListener() {
            @Override
            public void onCameraTextureReady(SurfaceTexture camSurfaceTexture) {
                if(mOnVideoEncodeEventListener != null){
                    mOnVideoEncodeEventListener.onCameraTextureReady(camSurfaceTexture);
                }
            }
        });

        mEncodeTask.setEncodeInfo(encodeInfo);

        mEncodeTask.startEncodeTask();
    }

    public void changeBitrate(int bitrate){
        mEncodeTask.changeBitrate(bitrate);
    }

    public void changeFramerate(int framerate){
        mEncodeTask.changeFramerate(framerate);
    }

    public void requestKeyFrame(){
        mEncodeTask.requestKeyFrame();
    }

    public SurfaceTexture getCameraTexture(){
        return mEncodeTask.getCameraTexture();
    }

    public void stopEncode(){
        if(mEncodeTask != null) {
            mEncodeTask.stopEncodeTask();
        }
    }

    public void setOnVideoEncodeEventListener(OnVideoEncodeEventListener onVideoEncodeEventListener){
        mOnVideoEncodeEventListener = onVideoEncodeEventListener;
    }

    public interface OnVideoEncodeEventListener{

        /**
         * 编码帧率
         * @param frameRate
         */
        void onStatisticsUpdate(StatisticsData frameRate);

        /**
         * 编码器surfacetexture就绪
         * @param camSurfaceTexture
         */
        void onCameraTextureReady(SurfaceTexture camSurfaceTexture);

        /**
         * 收到编码配置信息
         * @param data
         * @param length
         */
        void onConfigFrameRecv(byte[] data, int length, int videoWidth, int videoHeight);

        /**
         * 收到关键帧
         * @param data
         * @param length
         */
        void onKeyFrameRecv(byte[] data, int length, int videoWidth, int videoHeight);

        /**
         * 收到非关键帧
         * @param data
         * @param length
         */
        void onOtherFrameRecv(byte[] data, int length, int videoWidth, int videoHeight);
    }
}

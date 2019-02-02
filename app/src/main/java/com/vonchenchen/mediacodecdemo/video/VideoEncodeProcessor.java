package com.vonchenchen.mediacodecdemo.video;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.vonchenchen.mediacodecdemo.io.MediaDataWriter;

import java.io.FileNotFoundException;

/**
 * Created by vonchenchen on 2018/4/15.
 */

public class VideoEncodeProcessor {

    private EncodeTask mEncodeTask;
    private MediaDataWriter mMediaDataWriter;
    private String mPath;

    private OnVideoEncodeEventListener mOnVideoEncodeEventListener = null;

    public VideoEncodeProcessor(String outPath){
        mPath = outPath;
    }

    /**
     * 开始采集并渲染和编码
     * @param surfaceView
     * @param width    图像宽度
     * @param height   图像高度
     * @param frameRate
     */
    public void startEncode(SurfaceView surfaceView, int width, int height, int frameRate){

        try {
            mMediaDataWriter = new MediaDataWriter(mPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        mEncodeTask = new EncodeTask(surfaceView, width, height, frameRate, new CircularEncoder.OnCricularEncoderEventListener() {
            @Override
            public void onConfigFrameReceive(byte[] data, int length) {
                mMediaDataWriter.write(data, length);
            }

            @Override
            public void onKeyFrameReceive(byte[] data, int length) {
                mMediaDataWriter.write(data, length);
            }

            @Override
            public void onOtherFrameReceive(byte[] data, int length) {
                mMediaDataWriter.write(data, length);
            }

            @Override
            public void onFrameRateReceive(int frameRate) {

                if(mOnVideoEncodeEventListener != null){
                    mOnVideoEncodeEventListener.onFrameRate(frameRate);
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

        mEncodeTask.startEncodeTask();
    }

    public SurfaceTexture getCameraTexture(){
        return mEncodeTask.getCameraTexture();
    }

    public void stopCapture(){
        if(mEncodeTask != null) {
            mEncodeTask.stopEncodeTask();
        }
        if(mMediaDataWriter != null) {
            mMediaDataWriter.close();
        }
    }

    public void setOnVideoEncodeEventListener(OnVideoEncodeEventListener onVideoEncodeEventListener){
        mOnVideoEncodeEventListener = onVideoEncodeEventListener;
    }

    public interface OnVideoEncodeEventListener{
        void onFrameRate(int frameRate);
        void onCameraTextureReady(SurfaceTexture camSurfaceTexture);
    }
}

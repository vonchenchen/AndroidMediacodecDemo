package com.vonchenchen.mediacodecdemo.video;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.SurfaceHolder;

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
     * @param surfaceHolder
     * @param width    图像宽度
     * @param height   图像高度
     * @param displayViewWidth    图像view宽度
     * @param displayViewHeight   图像view高度
     * @param frameRate
     */
    public void startEncode(SurfaceHolder surfaceHolder, int width, int height, int displayViewWidth, int displayViewHeight, int frameRate){

        try {
            mMediaDataWriter = new MediaDataWriter(mPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        initGLThread(surfaceHolder, width, height, displayViewWidth, displayViewHeight,frameRate);
    }

    private void initGLThread(SurfaceHolder surfaceHolder, int width, int height, int displayViewWidth, int displayViewHeight, int frameRate){
        if(surfaceHolder.getSurface().isValid()){
            mEncodeTask = new EncodeTask(surfaceHolder, width, height, displayViewWidth, displayViewHeight,frameRate, new CircularEncoder.OnCricularEncoderEventListener() {
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
            mEncodeTask.start();
        }
    }


    public SurfaceTexture getCameraTexture(){
        return mEncodeTask.getCameraTexture();
    }

    public void stopCapture(){
        if(mEncodeTask != null) {
            mEncodeTask.release();
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
    }
}

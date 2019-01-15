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

    private GLThread mGLThread;
    private MediaDataWriter mMediaDataWriter;
    private String mPath;

    private OnVideoEncodeEventListener mOnVideoEncodeEventListener = null;

    public VideoEncodeProcessor(String outPath){
        mPath = outPath;
    }

    /**
     * 开始采集并渲染和编码
     * @param context
     * @param surfaceHolder
     * @param width    图像宽度
     * @param height   图像高度
     * @param displayViewWidth    图像view宽度
     * @param displayViewHeight   图像view高度
     * @param frameRate
     */
    public void startCapture(Context context, SurfaceHolder surfaceHolder, int width, int height, int displayViewWidth, int displayViewHeight, int frameRate){

        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            }
        });

        try {
            mMediaDataWriter = new MediaDataWriter(mPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        initGLThread(context, surfaceHolder, width, height, displayViewWidth, displayViewHeight,frameRate);
    }

    private void initGLThread(Context context, SurfaceHolder surfaceHolder, int width, int height, int displayViewWidth, int displayViewHeight, int frameRate){
        if(surfaceHolder.getSurface().isValid()){
            mGLThread = new GLThread(context, surfaceHolder, width, height, displayViewWidth, displayViewHeight,frameRate, new CircularEncoder.OnCricularEncoderEventListener() {
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
            mGLThread.start();
        }
    }


    public SurfaceTexture getCameraTexture(){
        return mGLThread.getCameraTexture();
    }

    public void stopCapture(){
        if(mGLThread != null) {
            mGLThread.release();
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

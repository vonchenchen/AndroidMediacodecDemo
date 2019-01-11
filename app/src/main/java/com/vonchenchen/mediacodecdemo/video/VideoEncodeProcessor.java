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

    public VideoEncodeProcessor(String outPath){
        mPath = outPath;
    }

    public void startCapture(Context context, SurfaceHolder surfaceHolder, int width, int height, int frameRate){

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

        initGLThread(context, surfaceHolder, width, height, frameRate);
    }

    private void initGLThread(Context context, SurfaceHolder surfaceHolder, int width, int height, int frameRate){
        if(surfaceHolder.getSurface().isValid()){
            mGLThread = new GLThread(context, surfaceHolder, width, height, frameRate, new CircularEncoder.OnCricularEncoderEventListener() {
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
}

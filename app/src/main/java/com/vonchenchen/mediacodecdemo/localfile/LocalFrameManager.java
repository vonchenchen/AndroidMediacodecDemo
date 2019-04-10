package com.vonchenchen.mediacodecdemo.localfile;

import android.graphics.SurfaceTexture;
import android.view.SurfaceView;

import com.vonchenchen.mediacodecdemo.io.IStreamDataReader;
import com.vonchenchen.mediacodecdemo.io.RawFrameReader;

import java.io.File;

public class LocalFrameManager {

    private SurfaceTexture mImageSurfaceTexture;
    //private SurfaceView

    private IStreamDataReader mRawFrameReader;

    private LocalFrameManager sInstance = null;

    private LocalFrameManager(){}

    public LocalFrameManager getInstance(){
        if(sInstance == null) {
            synchronized (LocalFrameManager.class) {
                if(sInstance == null) {
                    sInstance = new LocalFrameManager();
                }
            }
        }
        return sInstance;
    }

    public void setRawFrameReader(IStreamDataReader rawFrameReader){
        mRawFrameReader = rawFrameReader;
    }

    public void openFile(File file, Codec codec){


    }

    public void startCapture(SurfaceTexture surfaceTexture){

        mImageSurfaceTexture = surfaceTexture;

        //mRawFrameReader.
    }

    enum Codec{
        I420,
        H264
    }
}

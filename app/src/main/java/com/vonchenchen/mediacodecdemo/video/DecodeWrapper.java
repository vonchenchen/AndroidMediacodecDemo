package com.vonchenchen.mediacodecdemo.video;

import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * 用来封装decode所使用的数据
 */
public class DecodeWrapper {

    private static final String TAG = "DecodeWrapper";

    private int mWidth;
    private int mHeight;
    /** 用于解码的surface */
    private Surface mDecodeSurface;
    private SimpleDecoder mSimpleDecoder = null;

    public void init(int width, int height, Surface surface, final String mediaFormatType){

        mWidth = width;
        mHeight = height;
        mDecodeSurface = surface;

        if(mSimpleDecoder != null){
            mSimpleDecoder.close();
        }
        mSimpleDecoder = new SimpleDecoder(mWidth, mHeight, mDecodeSurface, mediaFormatType);
    }

    public int decode(byte[] input, int offset, int count , long pts){
        return mSimpleDecoder.decode(input, offset, count, pts);
    }

    public void setOnDecoderEnventLisetener(SimpleDecoder.OnDecoderEnventLisetener lisetener){
        mSimpleDecoder.setOnDecoderEnventLisetener(lisetener);
    }

    public void release(){

//        if(mDecodeSurface != null) {
//            mDecodeSurface.release();
//            mDecodeSurface = null;
//        }

        if(mSimpleDecoder != null) {
            mSimpleDecoder.close();
            mSimpleDecoder = null;
        }
    }
}

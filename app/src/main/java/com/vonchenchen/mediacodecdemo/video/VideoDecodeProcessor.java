package com.vonchenchen.mediacodecdemo.video;

import android.media.MediaFormat;
import android.view.SurfaceView;

import com.vonchenchen.mediacodecdemo.io.H264DataReader;
import com.vonchenchen.mediacodecdemo.io.IStreamDataReader;
import com.vonchenchen.mediacodecdemo.io.IVFDataReader;

import java.io.FileNotFoundException;

/**
 * Created by vonchenchen on 2018/5/17.
 */

public class VideoDecodeProcessor {

    private static final String TAG = "VideoDecodeProcessor";

    private volatile boolean mStopPlay;

    private SurfaceView mSurfaceView;
    private CircularDecoder mCircularDecoder;

    private IStreamDataReader mStreamDataReader;
    private String mMediaFormatType;
    private String mInPath;

    private int mPts = 0;

    public VideoDecodeProcessor(SurfaceView surfaceView, String path, String mediaFormatType){
        mSurfaceView = surfaceView;
        mInPath = path;
        mMediaFormatType = mediaFormatType;
    }
    public void startPlay(int width, int height){
        mPts = 0;
        //创建解码器
        mCircularDecoder = new CircularDecoder(mSurfaceView, mMediaFormatType);

        try {
            if(mMediaFormatType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {

                mStreamDataReader = new H264DataReader(mInPath, width * height * 3);
            }else if(mMediaFormatType.equals(MediaFormat.MIMETYPE_VIDEO_VP8)){

                mStreamDataReader = new IVFDataReader(mInPath, width * height * 3);
            }

            mStreamDataReader.setOnDataParsedListener(new IStreamDataReader.OnDataParsedListener() {
                @Override
                public void onParsed(byte[] data, int index, int length) {

                    //Logger.i(TAG, "lidechen_test length="+length+" onParsed "+Utils.byteArrayToHexString(data));

                    long start = System.currentTimeMillis();
                    //解码
                    mCircularDecoder.decode(data, index, length, mPts+=1);
                    long end = System.currentTimeMillis();
                    Logger.i(TAG,"lidechen_test spend="+(end-start));

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        mStopPlay = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int ret = 0;
                while (mStopPlay) {
                    ret = mStreamDataReader.readNextFrame();
                    if(ret <=0){
                        break;
                    }
                }

                if(mOnVideoDecodeEventListener != null){
                    mOnVideoDecodeEventListener.onDecodeFinish();
                }
            }
        }).start();
    }

    public void stopPlay(){
        mStopPlay = false;
        //关闭解码器
        mCircularDecoder.close();

        mStreamDataReader.close();
    }

    public void setOnVideoDecodeEventListener(OnVideoDecodeEventListener listener){
        mOnVideoDecodeEventListener = listener;
    }

    private OnVideoDecodeEventListener mOnVideoDecodeEventListener;

    public interface OnVideoDecodeEventListener{
        void onDecodeFinish();
    }
}

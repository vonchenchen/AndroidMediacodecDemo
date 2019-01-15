package com.vonchenchen.mediacodecdemo.video;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class CircularDecoder implements SurfaceHolder.Callback{

    private static final String TAG = "CircularDecoderToSurface";

    private static final long DEFAULT_TIMEOUT_US = 1000000;

    private boolean mSurfaceReady ;
    private SurfaceHolder mSurfaceHolder;

    private MediaCodec mMediaCodec;

    private String mMediaFormatType = MediaFormat.MIMETYPE_VIDEO_AVC;

    /** 窗口宽 */
    private int mWidth;
    /** 窗口高 */
    private int mHeight;

    private SurfaceView mMainSurfaceView;
    /** 视频帧实际宽度 */
    private int mFrameWidth = -1;
    /** 视频帧实际高度 */
    private int mFrameHeight = -1;

    /**
     * 初始化解码器
     * @param width 解码器分辨率
     * @param height 解码器分辨率
     */
    CircularDecoder(SurfaceView surfaceView, int width, int height, String mediaFormatType) {
        mMainSurfaceView = surfaceView;
        mWidth = width;
        mHeight = height;
        init();
    }

    CircularDecoder(SurfaceView surfaceView, String mediaFormatType) {
        mMainSurfaceView = surfaceView;
        mWidth = -1;
        mHeight = -1;
        mMediaFormatType = mediaFormatType;
        init();
    }

    private SurfaceView getSurfaceView() {
        return mMainSurfaceView;
    }

    public void setSurfaceView(SurfaceView surfaceView){
        mMainSurfaceView = surfaceView;
    }

    /**
     * 初始化视频解码器
     */
    private void init() {
        try {
            if(mMainSurfaceView != null) {
                mSurfaceHolder = mMainSurfaceView.getHolder();
                mSurfaceReady = mMainSurfaceView.getHolder().getSurface().isValid();
                mSurfaceHolder.addCallback(this);
            }
            if(!mSurfaceReady) {
                return;
            }
            if(mWidth == -1) {
                mWidth = mMainSurfaceView.getWidth();
                mHeight = mMainSurfaceView.getHeight();
            }
            MediaFormat format = MediaFormat.createVideoFormat(mMediaFormatType, mWidth, mHeight);
            format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
            mMediaCodec = MediaCodec.createDecoderByType(mMediaFormatType);
            mMediaCodec.configure(format, mMainSurfaceView.getHolder().getSurface(), null, 0);
            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if(mSurfaceHolder != null) {
                mSurfaceHolder.removeCallback(this);
            }
            mSurfaceReady = false;
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        } catch (Exception e) {
            Logger.printErrStackTrace(TAG , e , "get Exception");
        }
    }

    public void decode(byte[] input, int offset, int count , long pts) {
        if(!mSurfaceReady) {
            Logger.e(TAG , "[decode] mSurfaceReady="+mSurfaceReady);
            return ;
        }
        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(input, offset, count);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, count, pts, 0);
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        // 1000000 us timeout , one second
        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, DEFAULT_TIMEOUT_US);
        Logger.d(TAG , "decode outputBufferIndex " + outputBufferIndex);

        if (outputBufferIndex >= 0) {

            if(mFrameWidth<= 0||mFrameHeight<= 0){
                MediaFormat format = mMediaCodec.getOutputFormat();
                mFrameWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                mFrameHeight = format.getInteger(MediaFormat.KEY_HEIGHT);

                if(mOnDecoderEnventLisetener != null){
                    mOnDecoderEnventLisetener.onFrameSizeInit(mFrameWidth, mFrameHeight);
                }
            }

            mMediaCodec.releaseOutputBuffer(outputBufferIndex, true);
        }
    }


    public void setSurfaceView() {
        mSurfaceReady = false;

        if(mMainSurfaceView != null) {
            mSurfaceHolder = mMainSurfaceView.getHolder();
            mSurfaceReady = mMainSurfaceView.getHolder().getSurface().isValid();
            mSurfaceHolder.addCallback(this);
        }
        if(mMainSurfaceView == null || !mMainSurfaceView.getHolder().getSurface().isValid()) {
            return;
        }
        try {
            if(mMediaCodec == null) {
                mMediaCodec = MediaCodec.createDecoderByType(mMediaFormatType);
            } else {
                mMediaCodec.stop();
            }
        } catch (Exception e) {
            Logger.printErrStackTrace(TAG ,e , "getException");
        }
        MediaFormat format = MediaFormat.createVideoFormat(mMediaFormatType, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
        mMediaCodec.configure(format, mMainSurfaceView.getHolder().getSurface(), null, 0);
        mMediaCodec.start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        mSurfaceHolder.addCallback(this);
        if(mMediaCodec == null) {
            try {
                mMediaCodec = MediaCodec.createDecoderByType(mMediaFormatType);
            } catch (Exception e) {
                Logger.printErrStackTrace(TAG ,e ,  "");
            }

        } else {
            mMediaCodec.stop();
        }

        mWidth = mMainSurfaceView.getWidth();
        mHeight = mMainSurfaceView.getHeight();

        MediaFormat mFormat = MediaFormat.createVideoFormat(mMediaFormatType, mWidth, mHeight);
        mFormat.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
        mMediaCodec.configure(mFormat, mSurfaceHolder.getSurface(), null, 0);
        mMediaCodec.start();
        mSurfaceReady = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceReady = false;
        try {
            if(mSurfaceHolder != null) {
                mSurfaceHolder.removeCallback(this);
            }
            mSurfaceHolder = holder;
            mSurfaceHolder.addCallback(this);
            mSurfaceReady = holder.getSurface().isValid();
        } catch (Exception e) {
            Logger.printErrStackTrace(TAG ,e, "get Exception on surfaceChanged");
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceReady = false;
    }

    private OnDecoderEnventLisetener mOnDecoderEnventLisetener = null;

    public void setOnDecoderEnventLisetener(OnDecoderEnventLisetener lisetener){
        mOnDecoderEnventLisetener = lisetener;
    }

    public interface OnDecoderEnventLisetener{
        /** 拿到帧大小 */
        void onFrameSizeInit(int frameWidth, int frameHeight);
    }
}

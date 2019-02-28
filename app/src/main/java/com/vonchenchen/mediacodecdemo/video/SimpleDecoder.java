package com.vonchenchen.mediacodecdemo.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;

public class SimpleDecoder {

    private static final String TAG = "SimpleDecoder";

    /** 解码器超时时间 如果解码异常会等待的时间 不要太长 否则一旦数据不完整会引起较长的黑屏时间 */
    private static final long DEFAULT_TIMEOUT_US = 250000;

    private Surface mSurface;

    private MediaCodec mMediaCodec;

    private String mMediaFormatType = MediaFormat.MIMETYPE_VIDEO_AVC;

    /** 设置视频宽度 */
    private int mWidth;
    /** 设置视频高度 */
    private int mHeight;

    /** 视频帧实际宽度 */
    private int mFrameWidth = -1;
    /** 视频帧实际高度 */
    private int mFrameHeight = -1;

    private int mRecWidth = -1;
    private int mRecHeight = -1;

    /**
     * 初始化解码器
     * @param width 解码器分辨率
     * @param height 解码器分辨率
     */
    SimpleDecoder(int width, int height, Surface surface,String mediaFormatType) {
        mWidth = width;
        mHeight = height;
        mSurface = surface;
        mMediaFormatType = mediaFormatType;
        init();
    }

    /**
     * 初始化视频解码器
     */
    private void init() {
        try {

            MediaFormat format = MediaFormat.createVideoFormat(mMediaFormatType, mWidth, mHeight);
            format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
            //分辨率可以避免一些情况下长宽比渲染异常的情况
            format.setInteger(MediaFormat.KEY_MAX_HEIGHT, 1920);
            format.setInteger(MediaFormat.KEY_MAX_WIDTH, 1080);
            mMediaCodec = MediaCodec.createDecoderByType(mMediaFormatType);
            mMediaCodec.configure(format, mSurface, null, 0);
            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        } catch (Exception e) {
            Logger.printErrStackTrace(TAG , e , "get Exception");
        }
    }

    public int decode(byte[] input, int offset, int count , long pts) {

        ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(input, offset, count);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, count, pts, 0);
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, DEFAULT_TIMEOUT_US);
        Logger.d(TAG , "decode outputBufferIndex " + outputBufferIndex);

        MediaFormat format = mMediaCodec.getOutputFormat();
        mFrameWidth = format.getInteger(MediaFormat.KEY_WIDTH);
        mFrameHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        Logger.d(TAG , "mFrameWidth=" + mFrameWidth+ " mFrameHeight="+mFrameHeight);

        if (outputBufferIndex >= 0) {

            if(mFrameWidth<= 0||mFrameHeight<= 0){
                //首次解码
                mRecWidth = mFrameWidth;
                mRecHeight = mFrameHeight;

                Logger.d(TAG , "mFrameWidth=" + mFrameWidth+ " mFrameHeight="+mFrameHeight);

                if(mOnDecoderEnventLisetener != null){
                    mOnDecoderEnventLisetener.onFrameSizeInit(mFrameWidth, mFrameHeight);
                }
            }else{

                if(mFrameWidth != mRecWidth || mFrameHeight != mRecHeight){
                    //码流分辨率改变
                    mRecWidth = mFrameWidth;
                    mRecHeight = mFrameHeight;

                    mOnDecoderEnventLisetener.onFrameSizeChange(mFrameWidth, mFrameHeight);
                }
            }

            mMediaCodec.releaseOutputBuffer(outputBufferIndex, true);
        }else if(outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){
            Logger.i(TAG, "decode info MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
        }else if(outputBufferIndex == INFO_OUTPUT_FORMAT_CHANGED){
            Logger.i(TAG, "decode info MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
        }else if(outputBufferIndex == INFO_TRY_AGAIN_LATER){
            Logger.i(TAG, "decode info MediaCodec.INFO_TRY_AGAIN_LATER");
        }else {
            Logger.i(TAG, "decode info outputBufferIndex="+outputBufferIndex);
        }

        return outputBufferIndex;
    }

    private OnDecoderEnventLisetener mOnDecoderEnventLisetener = null;

    public void setOnDecoderEnventLisetener(OnDecoderEnventLisetener lisetener){
        mOnDecoderEnventLisetener = lisetener;
    }

    public interface OnDecoderEnventLisetener{
        /** 拿到帧大小 */
        void onFrameSizeInit(int frameWidth, int frameHeight);
        /** 帧大小变化 此处考虑媒体流的分辨率改变的情况 */
        void onFrameSizeChange(int frameWidth, int frameHeight);
    }
}

package com.vonchenchen.mediacodecdemo.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

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

    /**
     * 初始化解码器
     * @param width 解码器分辨率
     * @param height 解码器分辨率
     */
    SimpleDecoder(int width, int height, Surface surface,String mediaFormatType) {
        mWidth = width;
        mHeight = height;
        mSurface = surface;
        init();
    }

    /**
     * 初始化视频解码器
     */
    private void init() {
        try {

            MediaFormat format = MediaFormat.createVideoFormat(mMediaFormatType, mWidth, mHeight);
            format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
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
//                MediaFormat format = mMediaCodec.getOutputFormat();
//                mFrameWidth = format.getInteger(MediaFormat.KEY_WIDTH);
//                mFrameHeight = format.getInteger(MediaFormat.KEY_HEIGHT);

                Logger.d(TAG , "mFrameWidth=" + mFrameWidth+ " mFrameHeight="+mFrameHeight);

                if(mOnDecoderEnventLisetener != null){
                    mOnDecoderEnventLisetener.onFrameSizeInit(mFrameWidth, mFrameHeight);
                }
            }

            mMediaCodec.releaseOutputBuffer(outputBufferIndex, true);
        }

        return outputBufferIndex;
    }

    private DirectDecoder.OnDecoderEnventLisetener mOnDecoderEnventLisetener = null;

    public void setOnDecoderEnventLisetener(DirectDecoder.OnDecoderEnventLisetener lisetener){
        mOnDecoderEnventLisetener = lisetener;
    }

    public interface OnDecoderEnventLisetener{
        /** 拿到帧大小 */
        void onFrameSizeInit(int frameWidth, int frameHeight);
    }
}

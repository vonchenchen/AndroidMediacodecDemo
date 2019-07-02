package com.vonchenchen.mediacodecdemo.video;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.Surface;
import android.widget.TextView;

import java.io.IOException;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;

public class DirectEncoder {

    private static String VIDEO_MIME_TYPE = "video/avc";
    private static final int I_FRAME_INTERVAL = 5;

    private MediaCodec mVideoEncoder;

    private Surface mInputSurface;
    private int mVideoWidth, mVideoHeight;

    /** 给定编码器编码帧率 */
    private int mFrameRate;

    private EncodeInfo mEncodeInfo;

    public DirectEncoder(int width, int height, int frameRate , @Nullable String mimeType, @Nullable EncodeInfo encodeInfo){

        mVideoWidth = width;
        mVideoHeight = height;
        mFrameRate = frameRate;
        mEncodeInfo = encodeInfo;

        if(!TextUtils.isEmpty(mimeType)){
            VIDEO_MIME_TYPE = mimeType;
        }

        createVideoEncoder();

        mInputSurface = mVideoEncoder.createInputSurface();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private MediaCodec createVideoEncoder() {

        /**
         * 关于硬编码相关设置
         *
         * 目前看Android avc硬编码在Android接口只支持baseline
         * 编码器设置帧率最好要接近真实帧率 否则可能导致码率控制不准
         */

        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mVideoWidth, mVideoHeight);

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

        if(mEncodeInfo != null && mEncodeInfo.bitrate != 0){
            format.setInteger(MediaFormat.KEY_BIT_RATE, mEncodeInfo.bitrate);
        }else {
            format.setInteger(MediaFormat.KEY_BIT_RATE, getBitrate());
        }

        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mVideoWidth * mVideoHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);

        if(mEncodeInfo != null && mEncodeInfo.bitrateMode != 0){
            format.setInteger("bitrate-mode", mEncodeInfo.bitrateMode);
        }else {
            format.setInteger("bitrate-mode", BITRATE_MODE_CBR);
        }

        if(mEncodeInfo != null && mEncodeInfo.profile != 0) {
            format.setInteger(MediaFormat.KEY_PROFILE, mEncodeInfo.profile);
        }else {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        }
        format.setInteger("level", MediaCodecInfo.CodecProfileLevel.AVCLevel31);

        if(mEncodeInfo != null && mEncodeInfo.keyFrameInterval != 0){
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mEncodeInfo.keyFrameInterval);
        }else {
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        }

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        MediaCodec videoEncoder = null;
        try {
            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            if(android.os.Build.VERSION.SDK_INT >= 20) {
                videoEncoder.reset();
            }
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return videoEncoder;
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    private int getBitrate() {
        if(mVideoWidth * mVideoHeight == 1280 * 720) {
            return 1000 * 1024;
        }

        if(mVideoWidth * mVideoHeight == 1920 * 1080) {
            return 2000 * 1024;
        }

        if(mVideoWidth * mVideoHeight == 3840 * 2160) {
            return 4000 * 1024;
        }

        return 400 * 1024;
    }

    public void start(){

        mVideoEncoder.start();
    }

    public void stop(){

        mVideoEncoder.stop();
    }

    public void release(){

        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }

        if(mVideoEncoder != null){
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
    }
}

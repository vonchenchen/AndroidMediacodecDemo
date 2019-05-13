package com.vonchenchen.mediacodecdemo.video;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.widget.Toast;

import com.vonchenchen.mediacodecdemo.utils.NumUtils;
import com.vonchenchen.mediacodecdemo.video.statistics.StatisticsData;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenRecorder {

    private static final String TAG = "ScreenRecorder";

    private static String VIDEO_MIME_TYPE = "video/avc";

    private Activity mBaseActivity;

    private SimpleEncoder mSimpleEncoder;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;

    private VirtualDisplay mVirtualDisplay;
    /** 屏幕显示信息 */
    private DisplayMetrics mMetrics;

    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDpi;

    private MediaRecorder mediaRecorder;
    private String videoPath = "";
    private MediaCodec mVideoEncoder;
    private ImageReader mImageReader;

    private byte[] mRGBAImageData = null;

    public ScreenRecorder(Activity activity){

        mBaseActivity = activity;
    }

    /**
     * 获取录制权限后调用
     * @param requestCode
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void initCapture(int requestCode){

        mMediaProjectionManager = (MediaProjectionManager) mBaseActivity.getSystemService(MEDIA_PROJECTION_SERVICE);
        //开启录屏请求intent
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        mBaseActivity.startActivityForResult(captureIntent, requestCode);
    }

    /**
     * onActivityResult中获取
     * @param resultCode
     * @param data
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setRecordResult(int resultCode, Intent data){

        mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
    }

    public void startCapture(){

        mMetrics = new DisplayMetrics();
        mBaseActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        mScreenWidth = mMetrics.widthPixels;
        mScreenHeight = mMetrics.heightPixels;
        mScreenDpi = mMetrics.densityDpi;

        mRGBAImageData = new byte[mScreenWidth * mScreenHeight * 4];

        mSimpleEncoder = new SimpleEncoder(mScreenWidth, mScreenHeight, 30, MediaFormat.MIMETYPE_VIDEO_AVC, true, null);
        mSimpleEncoder.setOnCricularEncoderEventListener(new SimpleEncoder.OnCricularEncoderEventListener() {
            @Override
            public void onConfigFrameReceive(byte[] data, int length, int frameWidth, int frameHeight) {

            }

            @Override
            public void onKeyFrameReceive(byte[] data, int length, int frameWidth, int frameHeight) {

                Logger.i(TAG, "onKeyFrameReceive key length="+length);
            }

            @Override
            public void onOtherFrameReceive(byte[] data, int length, int frameWidth, int frameHeight) {

                Logger.i(TAG, "onKeyFrameReceive length="+length);
            }

            @Override
            public void onStatisticsUpdate(StatisticsData statisticsData) {

            }
        });
        //Surface surface = mSimpleEncoder.getInputSurface();

// recorder
//        initRecorder();
//        Surface surface = mediaRecorder.getSurface();

//        mVideoEncoder = createVideoEncoder();
//        Surface surface = mVideoEncoder.createInputSurface();

        mImageReader = ImageReader.newInstance(mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 1);
        Surface surface = mImageReader.getSurface();
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {

                Image image = imageReader.acquireNextImage() ;

                int width = image.getWidth();
                int height = image.getHeight();

                final Image.Plane[] planes = image.getPlanes();
                final ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;
                //Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                //bitmap.copyPixelsFromBuffer(buffer);
                //bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                //int len = buffer.limit() - buffer.position();

                Logger.i(TAG, "onImageAvailable pixelStride="+pixelStride+" rowStride="+rowStride);

                image.getPlanes()[0].getBuffer().get(mRGBAImageData);
                image.close();

                if(mOnImageRecvListener != null){
                    mOnImageRecvListener.onImageRecv(mRGBAImageData, width, height);
                }
            }
        }, null);

        mVirtualDisplay = mMediaProjection.createVirtualDisplay("MainScreen", mScreenWidth, mScreenHeight, mScreenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null);

//        mediaRecorder.start();

//        mVideoEncoder.start();
//        mBufferInfo = new MediaCodec.BufferInfo();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//
//                while (true) {
//                    drainVideoEncoder();
//                    try {
//                        Thread.sleep(30);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }).start();
    }

    public void stopCapture(){

//        mSimpleEncoder.shutdown();
//        mSimpleEncoder = null;

//        mediaRecorder.stop();
//        mediaRecorder.reset();

        if(mVideoEncoder != null){
            mVideoEncoder.release();
        }
        mVideoEncoder = null;

        mVirtualDisplay.release();
    }

    private void initRecorder() {
        mediaRecorder = new MediaRecorder();
        //设置声音来源
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        //设置视频来源
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        //设置视频格式
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //设置视频储存地址
        videoPath ="/sdcard/" + System.currentTimeMillis() + ".mp4";
        mediaRecorder.setOutputFile(videoPath);
        //设置视频大小
        mediaRecorder.setVideoSize(mScreenWidth, mScreenHeight);
        //设置视频编码
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //设置声音编码
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        //视频码率
        mediaRecorder.setVideoEncodingBitRate(2 * 1920 * 1080);
        mediaRecorder.setVideoFrameRate(18);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(mBaseActivity,"prepare出错，录屏失败！",Toast.LENGTH_SHORT).show();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private MediaCodec createVideoEncoder() {

        /**
         * 关于硬编码相关设置
         *
         * 目前看Android avc硬编码在Android接口只支持baseline
         * 编码器设置帧率最好要接近真实帧率 否则可能导致码率控制不准
         */

        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mScreenWidth, mScreenWidth);

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2000 * 1000);


        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mScreenWidth * mScreenWidth);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        format.setInteger("level", MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

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

    private MediaCodec.BufferInfo mBufferInfo;
    private byte[] configbyte ;
    private int mCount = 0;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    void drainVideoEncoder() {
        final int TIMEOUT_USEC = 50;     // no timeout -- check for buffers, bail if none
        //mVideoEncoder.flush();
        ByteBuffer[] encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
        byte[] outData;

        Logger.d(TAG, "drainVideoEncoder");

        while (true) {
            int encoderStatus = mVideoEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                Logger.d(TAG, "drainVideoEncoder INFO_TRY_AGAIN_LATER");
                break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                Logger.d(TAG, "drainVideoEncoder INFO_OUTPUT_BUFFERS_CHANGED");
                encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                MediaFormat encodedFormat = mVideoEncoder.getOutputFormat();
                Logger.d(TAG, "drainVideoEncoder INFO_OUTPUT_FORMAT_CHANGED "+encodedFormat);
                //Logger.d(TAG, "encoder output format changed: " + mEncodedFormat);
            } else if (encoderStatus < 0) {
                Logger.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                // let's ignore it
            } else {

                Logger.d(TAG, "drainVideoEncoder mBufferInfo size: "+mBufferInfo.size+" offset: "+mBufferInfo.offset+" pts: "+mBufferInfo.presentationTimeUs);

                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                long pts = computePresentationTime(mCount);
                mCount += 1;
                // adjust the ByteBuffer values to match BufferInfo (not needed?)
                outData = new byte[mBufferInfo.size];
                encodedData.get(outData);

                if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {

                    //SPS PPS
                    configbyte = new byte[outData.length];
                    System.arraycopy(outData, 0, configbyte, 0, configbyte.length);

//                    if(mOnCricularEncoderEventListener != null){
//                        mOnCricularEncoderEventListener.onConfigFrameReceive(outData, mBufferInfo.size, mVideoWidth, mVideoHeight);
//                    }

                    Logger.d(TAG,"configbyte size="+outData.length);

                } else if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_SYNC_FRAME) {

                    byte[] keyframe;
                    if(((short)outData[4] & 0x001f) == 0x05){
                        //IDR帧前加入sps pps
                        keyframe = new byte[mBufferInfo.size + configbyte.length];
                        System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                        System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                    }else {
                        keyframe = outData;
                    }

//                    if(mOnCricularEncoderEventListener != null){
//                        mOnCricularEncoderEventListener.onKeyFrameReceive(keyframe, keyframe.length, mVideoWidth, mVideoHeight);
//                    }

                    Logger.d(TAG,"frame size="+outData.length);

                } else {

                    Logger.d(TAG,"frame size="+outData.length);

//                    if(mOnCricularEncoderEventListener != null){
//                        mOnCricularEncoderEventListener.onOtherFrameReceive(outData, outData.length, mVideoWidth, mVideoHeight);
//                    }
                }

                mVideoEncoder.releaseOutputBuffer(encoderStatus, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / 30;
    }

    private OnImageRecvListener mOnImageRecvListener;

    public void setOnImageRecvListener(OnImageRecvListener listener){
        mOnImageRecvListener =listener;
    }

    public interface OnImageRecvListener{
        void onImageRecv(byte[] data, int width, int height);
    }
}

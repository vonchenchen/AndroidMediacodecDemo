package com.vonchenchen.mediacodecdemo.video.screen;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
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

import com.vonchenchen.mediacodecdemo.video.DirectEncoder;
import com.vonchenchen.mediacodecdemo.video.Logger;
import com.vonchenchen.mediacodecdemo.video.SimpleEncoder;
import com.vonchenchen.mediacodecdemo.video.statistics.StatisticsData;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenCaptor {

    private static final String TAG = "ScreenRecorder";

    private Activity mBaseActivity;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;

    private VirtualDisplay mVirtualDisplay;
    /** 屏幕显示信息 */
    private DisplayMetrics mMetrics;

    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDpi;

    private ImageReader mImageReader;
    private DirectEncoder mDirectEncoder;

    private byte[] mRGBAImageData = null;

    public ScreenCaptor(Activity activity){

        mBaseActivity = activity;
    }

    private SurfaceTexture mSurfaceTexture;

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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void startCaptureImage(){

        mMetrics = new DisplayMetrics();
        mBaseActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        mScreenWidth = mMetrics.widthPixels;
        mScreenHeight = mMetrics.heightPixels;
        mScreenDpi = mMetrics.densityDpi;

        mRGBAImageData = new byte[mScreenWidth * mScreenHeight * 4];

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
    }

    public void stopCapture(){

        mVirtualDisplay.release();
    }

    public void startCaptureStream(){

        mMetrics = new DisplayMetrics();
        mBaseActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        mScreenWidth = mMetrics.widthPixels;
        mScreenHeight = mMetrics.heightPixels;
        mScreenDpi = mMetrics.densityDpi;

        mDirectEncoder = new DirectEncoder(mScreenWidth, mScreenHeight, 20, null, null);
        Surface encodeSurface = mDirectEncoder.getInputSurface();
        mDirectEncoder.start();

        mVirtualDisplay = mMediaProjection.createVirtualDisplay("MainScreen", mScreenWidth, mScreenHeight, mScreenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, encodeSurface, null, null);
    }

    public void stopCaptureStream(){

        mVirtualDisplay.release();
        if(mDirectEncoder != null){
            mDirectEncoder.stop();
            mDirectEncoder.release();
            mDirectEncoder = null;
        }
    }

    private OnImageRecvListener mOnImageRecvListener;

    public void setOnImageRecvListener(OnImageRecvListener listener){
        mOnImageRecvListener =listener;
    }

    private SimpleEncoder.OnCricularEncoderEventListener mOnCricularEncoderEventListener = null;

    public void setOnCricularEncoderEventListener(SimpleEncoder.OnCricularEncoderEventListener listener){
        mOnCricularEncoderEventListener = listener;
    }

    public interface OnImageRecvListener{
        void onImageRecv(byte[] data, int width, int height);
    }


}

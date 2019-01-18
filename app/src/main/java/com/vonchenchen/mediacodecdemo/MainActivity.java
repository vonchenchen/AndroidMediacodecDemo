package com.vonchenchen.mediacodecdemo;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.vonchenchen.mediacodecdemo.camera.CameraManager;
import com.vonchenchen.mediacodecdemo.camera.interfaces.OnCameraPreviewListener;
import com.vonchenchen.mediacodecdemo.video.Logger;
import com.vonchenchen.mediacodecdemo.video.VideoDecodeProcessor;
import com.vonchenchen.mediacodecdemo.video.VideoEncodeProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private SurfaceView mCamSurfaceView;
    private Button mCamBtn;
    private Button mEncBtn;
    private Button mPlayBtn;

    private VideoEncodeProcessor mVideoEncodeProcessor;
    private VideoDecodeProcessor mH264VideoDecodeProcessor;
    private VideoDecodeProcessor mVp8VideoDecodeProcessor;

    private CameraManager.CameraSize mCurrentSize = CameraManager.CameraSize.SIZE_720P;

    private int mCaptureWidth;
    private int mCaptureHeight;
    private int mFps = 15;
    private int mCameraIndex = 0;

    private boolean mIsStartPlay = false;
    private SurfaceView mVp8PlaySurfaceView;
    private Button mPlayVp8Btn;
    private TextView mEncodeFrameRateText;
    private ImageView mTestImage;
    private Button mDisplayRgbBtn;

    int mDisplayViewWidth;
    int mDisplayViewHeight;
    private SurfaceView mPlaySurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCamSurfaceView = findViewById(R.id.sv_cam);
        mCamBtn = findViewById(R.id.btn_cam);
        mEncBtn = findViewById(R.id.btn_enc);
        mPlayBtn = findViewById(R.id.btn_play);
        mVp8PlaySurfaceView = findViewById(R.id.sv_playvp8);
        mPlayVp8Btn = findViewById(R.id.btn_playvp8);
        mPlaySurfaceView = findViewById(R.id.sv_play);
        mEncodeFrameRateText = findViewById(R.id.tv_encode_framerate);

        mCameraIndex = 0;

        //编码设置
        mVideoEncodeProcessor = new VideoEncodeProcessor("/sdcard/test.h264");
        mVideoEncodeProcessor.setOnVideoEncodeEventListener(new VideoEncodeProcessor.OnVideoEncodeEventListener() {
            @Override
            public void onFrameRate(final int frameRate) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mEncodeFrameRateText.setText("framerate="+frameRate);
                    }
                });

            }
        });

        //设置解码文件avc
        String inputPathAvc = "/sdcard/test.h264";
        mH264VideoDecodeProcessor = new VideoDecodeProcessor(mPlaySurfaceView, inputPathAvc, MediaFormat.MIMETYPE_VIDEO_AVC);

        //设置解码文件vp8
        String inputPathVp8 = "/sdcard/out.vp8";
        mVp8VideoDecodeProcessor = new VideoDecodeProcessor(mVp8PlaySurfaceView, inputPathVp8, MediaFormat.MIMETYPE_VIDEO_VP8);

        mH264VideoDecodeProcessor.setOnVideoDecodeEventListener(new VideoDecodeProcessor.OnVideoDecodeEventListener() {
            @Override
            public void onDecodeFinish() {
                mIsStartPlay = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mH264VideoDecodeProcessor.stopPlay();
                        mPlayBtn.setText("PLAY");
                    }
                });
            }
        });

        mCamBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                SurfaceHolder surfaceholder = mCamSurfaceView.getHolder();

                if(!CameraManager.getInstance().isCamStart()) {
                    //camera直接渲染
                    CameraManager.getInstance().startCapture(surfaceholder);
                    mCamBtn.setText("cam stop");
                }else{
                    CameraManager.getInstance().stopPreview();

                    //Surface surface = surfaceholder.getSurface();
                    //surface.release();


                    mCamBtn.setText("cam start");
                }
            }
        });

        mEncBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!CameraManager.getInstance().isCamStart()) {

                    CameraManager.CamSizeDetailInfo info = CameraManager.getInstance().getCamSize(mCurrentSize);
                    mCaptureWidth = info.width;
                    mCaptureHeight = info.height;
                    mDisplayViewWidth = mCamSurfaceView.getWidth();
                    mDisplayViewHeight = mCamSurfaceView.getHeight();

                    SurfaceHolder surfaceholder = mCamSurfaceView.getHolder();
                    mVideoEncodeProcessor.startCapture(MainActivity.this, surfaceholder, mCaptureWidth, mCaptureHeight, mDisplayViewWidth, mDisplayViewHeight, mFps);
                    SurfaceTexture surfaceTexture = mVideoEncodeProcessor.getCameraTexture();
                    CameraManager.getInstance().startCapture(surfaceTexture);

                    mEncBtn.setText("enc stop");
                }else{
                    CameraManager.getInstance().stopPreview();
                    mVideoEncodeProcessor.stopCapture();

                    mEncBtn.setText("enc start");
                }
            }
        });

        mPlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mIsStartPlay){
                    mH264VideoDecodeProcessor.stopPlay();
                    mPlayBtn.setText("PLAY");
                }else {

                    CameraManager.CamSizeDetailInfo info = CameraManager.getInstance().getCamSize(mCurrentSize);
                    mCaptureWidth = info.width;
                    mCaptureHeight = info.height;

                    mH264VideoDecodeProcessor.startPlay(mCaptureWidth, mCaptureHeight);
                    mPlayBtn.setText("STOP");
                }
                mIsStartPlay = !mIsStartPlay;
            }
        });

        mVp8VideoDecodeProcessor.setOnVideoDecodeEventListener(new VideoDecodeProcessor.OnVideoDecodeEventListener() {
            @Override
            public void onDecodeFinish() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mVp8VideoDecodeProcessor.stopPlay();
                        mPlayVp8Btn.setText("play vp8");
                    }
                });
            }
        });

        mPlayVp8Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                mPlayVp8Btn.setText("stop vp8");
                mVp8VideoDecodeProcessor.startPlay(352, 288);
            }
        });

        CameraManager.getInstance().setOnCameraPreviewListener(new OnCameraPreviewListener() {
            @Override
            public void onPreview(int width, int height, byte[] data) {
                Logger.i(TAG, "onPreview w "+width+" h "+height);
            }
        });

        mTestImage = findViewById(R.id.iv_test);
        mDisplayRgbBtn = findViewById(R.id.btn_diplay_rgb);

        mDisplayRgbBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


            }
        });

        openCamera();
    }

    private void openCamera(){
        CameraManager.getInstance().closeCamera();
        CameraManager.getInstance().openCamera(mCameraIndex, mCurrentSize, mFps);
    }

    private void closeCamera(){
        CameraManager.getInstance().closeCamera();
    }
}

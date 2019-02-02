package com.vonchenchen.mediacodecdemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.vonchenchen.mediacodecdemo.camera.CameraManager;
import com.vonchenchen.mediacodecdemo.video.DecodeTask;
import com.vonchenchen.mediacodecdemo.video.EncodeTask;
import com.vonchenchen.mediacodecdemo.video.VideoEncodeProcessor;

public class SimpleDemoActivity extends Activity{

    private SurfaceView mMainSurfaceView;
    private Button mStartRecord;
    private Button mStartPlay;
    private TextView mFrameRateText;

    private boolean mIsStartPlay = false;

    private DecodeTask mDecodeTask;

    private VideoEncodeProcessor mVideoEncodeProcessor;

    private CameraManager.CameraSize mCurrentSize = CameraManager.CameraSize.SIZE_720P;
    private int mFps = 20;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_simpledemo);

        mMainSurfaceView = findViewById(R.id.surface_main);
        mStartRecord = findViewById(R.id.btn_startRecord);
        mStartPlay = findViewById(R.id.btn_startPlay);
        mFrameRateText = findViewById(R.id.tv_frameRate);

        mStartPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mIsStartPlay){
                    mStartPlay.setText("stopPlay");
                    mIsStartPlay = true;

                    //h264读取并解码线程
                    String inputPathAvc = "/sdcard/test.h264";
                    mDecodeTask = new DecodeTask(inputPathAvc);
                    mDecodeTask.setDecodeTaskEventListener(new DecodeTask.DecodeTaskEventListener() {
                        @Override
                        public void onDecodeThreadEnd() {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mStartPlay.setText("startPlay");
                                    mIsStartPlay = false;
                                    mDecodeTask.stopDecodeTask();
                                }
                            });
                        }
                    });

                    CameraManager.CamSizeDetailInfo info = CameraManager.getInstance().getCamSize(mCurrentSize);
                    //渲染线程
                    mDecodeTask.initTask(info.width, info.height, mMainSurfaceView, MediaFormat.MIMETYPE_VIDEO_AVC);
                    mDecodeTask.startDecodeTask();
                }else {
                    mStartPlay.setText("startPlay");
                    mIsStartPlay = false;
                    mDecodeTask.stopDecodeTask();
                }
            }
        });

        mStartRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!CameraManager.getInstance().isCamStart()) {

                    CameraManager.CamSizeDetailInfo info = CameraManager.getInstance().getCamSize(mCurrentSize);

                    //开始编码
                    mVideoEncodeProcessor.startEncode(mMainSurfaceView, info.width, info.height, mFps);

                    mStartRecord.setText("stopRecord");
                }else{
                    CameraManager.getInstance().stopCapture();
                    mVideoEncodeProcessor.stopCapture();

                    mStartRecord.setText("startRecord");
                }
            }
        });

        findViewById(R.id.btn_testback).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(SimpleDemoActivity.this, DummyActivity.class));
            }
        });

        //编码设置
        mVideoEncodeProcessor = new VideoEncodeProcessor("/sdcard/test.h264");
        mVideoEncodeProcessor.setOnVideoEncodeEventListener(new VideoEncodeProcessor.OnVideoEncodeEventListener() {
            @Override
            public void onFrameRate(final int frameRate) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mFrameRateText.setText("framerate="+frameRate);
                    }
                });

            }

            @Override
            public void onCameraTextureReady(SurfaceTexture camSurfaceTexture) {

                CameraManager.getInstance().startCapture(camSurfaceTexture);
            }
        });

        CameraManager.getInstance().closeCamera();
        CameraManager.getInstance().openCamera(0, mCurrentSize, mFps);
    }
}

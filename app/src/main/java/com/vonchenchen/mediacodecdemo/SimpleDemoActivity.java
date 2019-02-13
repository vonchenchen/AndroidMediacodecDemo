package com.vonchenchen.mediacodecdemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.vonchenchen.mediacodecdemo.camera.CameraManager;
import com.vonchenchen.mediacodecdemo.io.MediaDataWriter;
import com.vonchenchen.mediacodecdemo.video.DecodeTask;
import com.vonchenchen.mediacodecdemo.video.VideoEncoderWrapper;

import java.io.FileNotFoundException;

public class SimpleDemoActivity extends Activity{

    private SurfaceView mMainSurfaceView;
    private Button mStartRecord;
    private Button mStartPlay;
    private TextView mFrameRateText;

    private boolean mIsStartPlay = false;

    private DecodeTask mDecodeTask;

    private VideoEncoderWrapper mVideoEncoderWrapper;
    private MediaDataWriter mMediaDataWriter;

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

                    try {
                        mMediaDataWriter = new MediaDataWriter("/sdcard/test.h264");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    //开始编码
                    mVideoEncoderWrapper.startEncode(mMainSurfaceView, info.width, info.height, mFps);

                    mStartRecord.setText("stopRecord");
                }else{
                    CameraManager.getInstance().stopCapture();
                    mVideoEncoderWrapper.stopEncode();

                    mMediaDataWriter.close();
                    mMediaDataWriter = null;

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
        mVideoEncoderWrapper = new VideoEncoderWrapper();
        mVideoEncoderWrapper.setOnVideoEncodeEventListener(new VideoEncoderWrapper.OnVideoEncodeEventListener() {
            @Override
            public void onEncodeFrameRate(final int frameRate) {
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

            @Override
            public void onConfigFrameRecv(byte[] data, int length, int videoWidth, int videoHeight) {

                mMediaDataWriter.write(data, length);
            }

            @Override
            public void onKeyFrameRecv(byte[] data, int length, int videoWidth, int videoHeight) {

                mMediaDataWriter.write(data, length);
            }

            @Override
            public void onOtherFrameRecv(byte[] data, int length, int videoWidth, int videoHeight) {

                mMediaDataWriter.write(data, length);
            }
        });

        CameraManager.getInstance().closeCamera();
        CameraManager.getInstance().openCamera(0, mCurrentSize, mFps);
    }
}

package com.vonchenchen.mediacodecdemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.vonchenchen.mediacodecdemo.camera.CameraManager;
import com.vonchenchen.mediacodecdemo.io.MediaDataWriter;
import com.vonchenchen.mediacodecdemo.video.DecodeTask;
import com.vonchenchen.mediacodecdemo.video.EncodeInfo;
import com.vonchenchen.mediacodecdemo.video.VideoEncoderWrapper;

import java.io.FileNotFoundException;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;

public class SimpleDemoActivity extends Activity{

    private SurfaceView mMainSurfaceView;
    private Button mStartRecordH264Btn;
    private Button mStartPlayH264Btn;
    private Button mStartPlayVp8Btn;

    private TextView mFrameRateText;

    private boolean mIsStartPlay = false;
    private boolean mIsStartPlayVp8 = false;

    private DecodeTask mDecodeH264Task;
    private DecodeTask mDecodeVp8Task;

    private VideoEncoderWrapper mVideoEncoderWrapper;
    private MediaDataWriter mMediaDataWriter;

    private CameraManager.CameraSize mCurrentSize = CameraManager.CameraSize.SIZE_720P;
    private int mFps = 20;

    private EncodeInfo mEncodeInfo;
    private RadioGroup mBitrateModeRG;
    private EditText mBitrateEdit;
    private EditText mIFrameIntervalEdit;
    private RadioGroup mResolutionRG;
    private EditText mFpsEdit;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_simpledemo);

        mMainSurfaceView = findViewById(R.id.surface_main);
        mStartRecordH264Btn = findViewById(R.id.btn_startRecord);
        mStartPlayH264Btn = findViewById(R.id.btn_startPlay);
        mFrameRateText = findViewById(R.id.tv_frameRate);

        mBitrateModeRG = findViewById(R.id.rg_bitratemode);
        mResolutionRG = findViewById(R.id.rg_resolution);
        mFpsEdit = findViewById(R.id.et_fps);

        mEncodeInfo = new EncodeInfo();

        mStartPlayH264Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mIsStartPlay){
                    mStartPlayH264Btn.setText("stopPlay");
                    mIsStartPlay = true;

                    //h264读取并解码线程
                    String inputPathAvc = "/sdcard/test.h264";
                    mDecodeH264Task = new DecodeTask(inputPathAvc);
                    mDecodeH264Task.setDecodeTaskEventListener(new DecodeTask.DecodeTaskEventListener() {
                        @Override
                        public void onDecodeThreadEnd() {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mStartPlayH264Btn.setText("startPlay");
                                    mIsStartPlay = false;
                                    mDecodeH264Task.stopDecodeTask();
                                }
                            });
                        }
                    });

                    CameraManager.CamSizeDetailInfo info = CameraManager.getInstance().getCamSize(mCurrentSize);
                    //渲染线程
                    mDecodeH264Task.initTask(info.width, info.height, mMainSurfaceView, MediaFormat.MIMETYPE_VIDEO_AVC);
                    mDecodeH264Task.startDecodeTask();
                }else {
                    mStartPlayH264Btn.setText("startPlay");
                    mIsStartPlay = false;
                    mDecodeH264Task.stopDecodeTask();
                }
            }
        });

        mStartRecordH264Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!CameraManager.getInstance().isCamStart()) {

                    CameraManager.CamSizeDetailInfo info = CameraManager.getInstance().getCamSize(mCurrentSize);

                    //码率模式
                    String bitRateStr = mBitrateEdit.getText().toString().trim();
                    if(TextUtils.isEmpty(bitRateStr)){
                        mEncodeInfo.bitrate = 0;
                    }else{
                        mEncodeInfo.bitrate = Integer.parseInt(bitRateStr) * 1000;
                    }

                    //关键帧间隔
                    String keyFrameIntervalStr = mIFrameIntervalEdit.getText().toString().trim();
                    if(TextUtils.isEmpty(keyFrameIntervalStr)){
                        mEncodeInfo.keyFrameInterval = 0;
                    }else {
                        mEncodeInfo.keyFrameInterval = Integer.parseInt(keyFrameIntervalStr);
                    }

                    //编码帧率
                    String fpsStr = mFpsEdit.getText().toString().trim();
                    if(TextUtils.isEmpty(fpsStr)){

                    }else {
                        mFps = Integer.parseInt(fpsStr);
                    }

                    try {
                        String bitRateName = "";
                        if(mEncodeInfo.bitrate != 0){
                            bitRateName = mEncodeInfo.bitrate+"";
                        }

                        mMediaDataWriter = new MediaDataWriter("/sdcard/test_bitrate_"+bitRateName+"_encode_"+fpsStr+".h264");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    //开始编码
                    mVideoEncoderWrapper.startEncode(mMainSurfaceView, info.width, info.height, mFps, mEncodeInfo);

                    mStartRecordH264Btn.setText("stopRecord");
                }else{
                    CameraManager.getInstance().stopCapture();
                    mVideoEncoderWrapper.stopEncode();

                    mMediaDataWriter.close();
                    mMediaDataWriter = null;

                    mStartRecordH264Btn.setText("startRecord");
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

                //mMediaDataWriter.write(data, length);
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

        mStartPlayVp8Btn = findViewById(R.id.btn_playvp8);
        mStartPlayVp8Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!mIsStartPlayVp8){
                    mStartPlayVp8Btn.setText("stopPlayVp8");
                    mIsStartPlayVp8 = true;

                    //vp8读取并解码线程
                    String inputPathVp8 = "/sdcard/out.vp8";
                    mDecodeVp8Task = new DecodeTask(inputPathVp8);
                    mDecodeVp8Task.setDecodeTaskEventListener(new DecodeTask.DecodeTaskEventListener() {
                        @Override
                        public void onDecodeThreadEnd() {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mStartPlayVp8Btn.setText("startPlayVp8");
                                    mIsStartPlayVp8 = false;
                                    mDecodeVp8Task.stopDecodeTask();
                                }
                            });
                        }
                    });

                    //CameraManager.CamSizeDetailInfo info = CameraManager.getInstance().getCamSize(mCurrentSize);
                    //渲染线程
                    mDecodeVp8Task.initTask(352, 288, mMainSurfaceView, MediaFormat.MIMETYPE_VIDEO_VP8);
                    mDecodeVp8Task.startDecodeTask();
                }else {
                    mStartPlayVp8Btn.setText("startPlayVp8");
                    mIsStartPlayVp8 = false;
                    mDecodeVp8Task.stopDecodeTask();
                }
            }
        });

        mBitrateModeRG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if(i == R.id.rb_vbr){
                    mEncodeInfo.bitrateMode = BITRATE_MODE_VBR;
                }else if(i == R.id.rb_cbr){
                    mEncodeInfo.bitrateMode = BITRATE_MODE_CBR;
                }
            }
        });

        mResolutionRG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if(i == R.id.rb_480p){
                    mCurrentSize = CameraManager.CameraSize.SIZE_480P;
                }else if(i == R.id.rb_720p){
                    mCurrentSize = CameraManager.CameraSize.SIZE_720P;
                }else if(i == R.id.rb_1080p){
                    mCurrentSize = CameraManager.CameraSize.SIZE_1080P;
                }

                CameraManager.getInstance().closeCamera();
                CameraManager.getInstance().openCamera(0, mCurrentSize, mFps);
            }
        });

        mBitrateEdit = findViewById(R.id.et_bitrate);
        mIFrameIntervalEdit = findViewById(R.id.et_iframeInterval);

        mFpsEdit.setText(mFps+"");

        CameraManager.getInstance().closeCamera();
        CameraManager.getInstance().openCamera(0, mCurrentSize, mFps);
    }
}

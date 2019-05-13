package com.vonchenchen.mediacodecdemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import com.vonchenchen.mediacodecdemo.utils.AppUtils;
import com.vonchenchen.mediacodecdemo.video.DecodeTask;
import com.vonchenchen.mediacodecdemo.video.EncodeInfo;
import com.vonchenchen.mediacodecdemo.video.Logger;
import com.vonchenchen.mediacodecdemo.video.VideoEncoderWrapper;
import com.vonchenchen.mediacodecdemo.video.statistics.BitrateInfoCounter;
import com.vonchenchen.mediacodecdemo.video.statistics.StatisticsData;

import java.io.File;
import java.io.FileNotFoundException;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;

public class SimpleDemoActivity extends Activity{

    private static final String TAG = "SimpleDemoActivity";

    private static final int MSG_DISPLAY_I_FRAME_TIME = 1;

    private static final String BASE_PATH = "/sdcard/test/";

    private SurfaceView mMainSurfaceView;

    private SurfaceView mBigSurfaceView;
    private SurfaceView mSmallSurfaceView;

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

    private int mCameraIndex = 0;

    private EncodeInfo mEncodeInfo;
    private RadioGroup mBitrateModeRG;
    private EditText mBitrateEdit;
    private EditText mIFrameIntervalEdit;
    private RadioGroup mResolutionRG;
    private RadioGroup mCamIndexRG;
    private RadioGroup mProfileRG;
    private EditText mFpsEdit;

    private String mCurrentRecVideoPath = "";
    private EditText mPlayPathEdit;
    private Button mSwitchViewBtn;
    private Button mRequestKeyFrameBtn;
    private TextView mRequestKeyFrameSpendText;

    private BitrateInfoCounter mBitrateInfoCounter;

    /** 开始发出请求关键帧时间戳 */
    private long mStartRequestKeyFrameTs = 0;
    /** 接收到关键帧时间戳 */
    private long mEndRequestKeyFrameTs = 0;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {

            if(msg.what == MSG_DISPLAY_I_FRAME_TIME){

                mRequestKeyFrameSpendText.setText((long)msg.obj+"ms");
            }
        }
    };
    private TextView mBitrateInfoText;

    private int mDisplayTick = 0;
    private TextView mVersionText;
    private byte[] data;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_simpledemo);

        File file = new File(BASE_PATH);
        if(!file.exists()){
            file.mkdirs();
        }

        data = new byte[1024*1024*230];

        mVersionText = findViewById(R.id.tv_version);
        mVersionText.setText("version="+ AppUtils.packageName(SimpleDemoActivity.this));

        mPlayPathEdit = findViewById(R.id.tv_play_path);

        //手机测试
        mSmallSurfaceView = findViewById(R.id.surface_main);
        //大屏测试
        mBigSurfaceView = findViewById(R.id.sv_big);

        mMainSurfaceView = mBigSurfaceView;

        mStartRecordH264Btn = findViewById(R.id.btn_startRecord);
        mStartPlayH264Btn = findViewById(R.id.btn_startPlay);
        mFrameRateText = findViewById(R.id.tv_frameRate);
        mBitrateInfoText = findViewById(R.id.tv_bitrate_info);

        mBitrateModeRG = findViewById(R.id.rg_bitratemode);
        mResolutionRG = findViewById(R.id.rg_resolution);
        mCamIndexRG = findViewById(R.id.rg_camIndex);
        mProfileRG = findViewById(R.id.rg_profile);

        mFpsEdit = findViewById(R.id.et_fps);

        mSwitchViewBtn = findViewById(R.id.btn_switchView);
        mRequestKeyFrameBtn = findViewById(R.id.btn_request_keyframe);
        mRequestKeyFrameSpendText = findViewById(R.id.tv_request_key_frame_spend);

        mEncodeInfo = new EncodeInfo();
        mBitrateInfoCounter = new BitrateInfoCounter();

        mStartPlayH264Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mIsStartPlay){
                    mStartPlayH264Btn.setText("stopPlay");
                    mIsStartPlay = true;

                    //h264读取并解码线程
                    //String inputPathAvc = "/sdcard/test.h264";

                    String inputPathAvc = mPlayPathEdit.getText().toString().trim();
                    //String inputPathAvc = mCurrentRecVideoPath;

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

                    mDisplayTick = 0;

                    CameraManager.CamSizeDetailInfo info = CameraManager.getInstance().getCamSize(mCurrentSize);

                    try{
                        mCurrentRecVideoPath = getCurrentFileName();
                        mPlayPathEdit.setText(mCurrentRecVideoPath);

                        mMediaDataWriter = new MediaDataWriter(mCurrentRecVideoPath);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    //开始编码
                    mVideoEncoderWrapper.startEncode(mMainSurfaceView, info.width, info.height, mFps, mEncodeInfo);

                    mStartRecordH264Btn.setText("stopRecord");

                    //测试时间为1分钟
//                    mHandler.postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            stopRecord();
//                        }
//                    }, 60*1000);

                }else{
                    stopRecord();
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
            public void onStatisticsUpdate(final StatisticsData statisticsData) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        mBitrateInfoCounter.addData(statisticsData.getBitrate());

                        mDisplayTick++;
                        mFrameRateText.setText(statisticsData.toString()+"   tick="+mDisplayTick);
                    }
                });
            }

            @Override
            public void onCameraTextureReady(SurfaceTexture camSurfaceTexture) {

                //CameraManager.getInstance().openCamera(0, mCurrentSize, mFps);
                Logger.i(TAG, "onCameraTextureReady");
                CameraManager.getInstance().startCapture(camSurfaceTexture);
            }

            @Override
            public void onConfigFrameRecv(byte[] data, int length, int videoWidth, int videoHeight) {

                //mMediaDataWriter.write(data, length);
            }

            @Override
            public void onKeyFrameRecv(byte[] data, int length, int videoWidth, int videoHeight) {

                if(mStartRequestKeyFrameTs != 0){
                    mEndRequestKeyFrameTs = System.currentTimeMillis();

                    Message message = Message.obtain();
                    message.what = MSG_DISPLAY_I_FRAME_TIME;
                    message.obj = (mEndRequestKeyFrameTs - mStartRequestKeyFrameTs);

                    mHandler.sendMessage(message);

                    mStartRequestKeyFrameTs = 0;
                }

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
                updatePathText();
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
                updatePathText();

                CameraManager.getInstance().closeCamera();
                CameraManager.getInstance().openCamera(mCameraIndex, mCurrentSize, mFps);
            }
        });

        mCamIndexRG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {

                int index = mCameraIndex;
                if(i == R.id.rb_cam0){

                    index = 0;
                }else if(i == R.id.rb_cam1){

                    index = 1;
                }

                updatePathText();

                if(index != mCameraIndex){
                    CameraManager.getInstance().closeCamera();
                    CameraManager.getInstance().openCamera(mCameraIndex, mCurrentSize, mFps);
                }
            }
        });

        mProfileRG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {

                if(i == R.id.rb_baseline){

                    mEncodeInfo.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
                }else if(i == R.id.rb_main){

                    mEncodeInfo.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileMain;
                }else if(i == R.id.rb_high){

                    mEncodeInfo.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
                }

                updatePathText();
            }
        });

        findViewById(R.id.btn_refresh_bitrate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //码率
                String bitRateStr = mBitrateEdit.getText().toString().trim();
                if(TextUtils.isEmpty(bitRateStr)){
                    mEncodeInfo.bitrate = 0;
                }else{
                    mEncodeInfo.bitrate = Integer.parseInt(bitRateStr) * 1000;
                }

                updatePathText();
                mVideoEncoderWrapper.changeBitrate(mEncodeInfo.bitrate);
            }
        });

        findViewById(R.id.btn_drop_frame).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


            }
        });

        findViewById(R.id.btn_refresh_framerate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //更新编码帧率
                String fpsStr = mFpsEdit.getText().toString().trim();
                if(TextUtils.isEmpty(fpsStr)){

                }else {
                    mFps = Integer.parseInt(fpsStr);
                }
                updatePathText();
                mVideoEncoderWrapper.changeFramerate(mFps);
            }
        });

        mSwitchViewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(mMainSurfaceView == mBigSurfaceView){
                    mMainSurfaceView = mSmallSurfaceView;
                }else {
                    mMainSurfaceView = mBigSurfaceView;
                }
            }
        });

        mRequestKeyFrameBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                mStartRequestKeyFrameTs = System.currentTimeMillis();

                mVideoEncoderWrapper.requestKeyFrame();
            }
        });

        findViewById(R.id.btn_auto_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        mBitrateEdit = findViewById(R.id.et_bitrate);
        mIFrameIntervalEdit = findViewById(R.id.et_iframeInterval);

        mFpsEdit.setText(mFps+"");

        updatePathText();

        CameraManager.getInstance().closeCamera();
        CameraManager.getInstance().openCamera(mCameraIndex, mCurrentSize, mFps);
    }

    private String getCurrentFileName() {

        //码率
        String bitRateStr = mBitrateEdit.getText().toString().trim();
        if (TextUtils.isEmpty(bitRateStr)) {
            mEncodeInfo.bitrate = 0;
        } else {
            mEncodeInfo.bitrate = Integer.parseInt(bitRateStr) * 1000;
        }

        //关键帧间隔
        String keyFrameIntervalStr = mIFrameIntervalEdit.getText().toString().trim();
        if (TextUtils.isEmpty(keyFrameIntervalStr)) {
            mEncodeInfo.keyFrameInterval = 0;
        } else {
            mEncodeInfo.keyFrameInterval = Integer.parseInt(keyFrameIntervalStr);
        }

        //编码帧率
        String fpsStr = mFpsEdit.getText().toString().trim();
        if (TextUtils.isEmpty(fpsStr)) {

        } else {
            mFps = Integer.parseInt(fpsStr);
        }


        String bitRateName = "";
        if (mEncodeInfo.bitrate != 0) {
            bitRateName = mEncodeInfo.bitrate / 1000 + "k";
        }
        String bitrateCtl = "cbr";
        if (mEncodeInfo.bitrateMode == BITRATE_MODE_VBR) {
            bitrateCtl = "vbr";
        }

        String resolution = "480P";
        if (mCurrentSize == CameraManager.CameraSize.SIZE_480P) {
            resolution = "480P";
        } else if (mCurrentSize == CameraManager.CameraSize.SIZE_720P) {
            resolution = "720P";
        } else {
            resolution = "1080P";
        }

        String profile = "base";
        if (mEncodeInfo.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline) {
            profile = "base";
        } else if (mEncodeInfo.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain) {
            profile = "main";
        } else {
            profile = "hight";
        }

        return BASE_PATH + "264_" + resolution + "_" + profile + "_" + bitRateName + "_" + fpsStr + "fps_" + bitrateCtl + ".h264";
    }

    private void updatePathText(){

        mCurrentRecVideoPath = getCurrentFileName();
        mPlayPathEdit.setText(mCurrentRecVideoPath);
    }

    private void stopRecord(){

        CameraManager.getInstance().stopCapture();
        mVideoEncoderWrapper.stopEncode();

        if(mMediaDataWriter != null) {
            mMediaDataWriter.close();
            mMediaDataWriter = null;
        }

        mStartRecordH264Btn.setText("startRecord");

        BitrateInfoCounter.BitrateInfo bitrateInfo = mBitrateInfoCounter.count(mEncodeInfo.bitrate);
        mBitrateInfoText.setText(bitrateInfo.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraManager.getInstance().closeCamera();
    }
}

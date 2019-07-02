package com.vonchenchen.mediacodecdemo.video.screen.test;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.vonchenchen.demo.YuvUtils;
import com.vonchenchen.mediacodecdemo.R;
import com.vonchenchen.mediacodecdemo.io.MediaDataWriter;
import com.vonchenchen.mediacodecdemo.video.Logger;
import com.vonchenchen.mediacodecdemo.video.SimpleEncoder;
import com.vonchenchen.mediacodecdemo.video.screen.ScreenCaptor;
import com.vonchenchen.mediacodecdemo.video.statistics.StatisticsData;

//import com.android.util.*;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RecordScreenH264Activity extends Activity {

    private static final String TAG = "RecordScreenH264Activity";

    private static final int RECORD_REQUEST_CODE = 101;

    private boolean mIsStartRecord = false;

    private ScreenCaptor mScreenCaptor;
    private Button mRecordBtn;

    private MediaDataWriter mMediaDataWriter;

    private byte[] mI420Data = new byte[1920*1080*3/2];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_record1_screen);

        mRecordBtn = findViewById(R.id.btn_record);
        mRecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!mIsStartRecord) {

//                    try {
//                        mMediaDataWriter = new MediaDataWriter("/sdcard/test.rgba");
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    }


                    mScreenCaptor.startCaptureStream();
                    mRecordBtn.setText("stopRecord");

                }else {

                    if(mMediaDataWriter != null) {
                        mMediaDataWriter.close();
                        mMediaDataWriter = null;
                    }

                    mScreenCaptor.stopCaptureStream();
                    mRecordBtn.setText("startRecord");
                }

                mIsStartRecord = !mIsStartRecord;
            }
        });

        mScreenCaptor = new ScreenCaptor(RecordScreenH264Activity.this);
        mScreenCaptor.setOnCricularEncoderEventListener(new SimpleEncoder.OnCricularEncoderEventListener() {
            @Override
            public void onConfigFrameReceive(byte[] data, int length, int frameWidth, int frameHeight) {

                Logger.i(TAG, "onConfigFrameReceive length="+length);
            }

            @Override
            public void onKeyFrameReceive(byte[] data, int length, int frameWidth, int frameHeight) {

                Logger.i(TAG, "onKeyFrameReceive length="+length);
            }

            @Override
            public void onOtherFrameReceive(byte[] data, int length, int frameWidth, int frameHeight) {

                Logger.i(TAG, "onOtherFrameReceive length="+length);
            }

            @Override
            public void onStatisticsUpdate(StatisticsData statisticsData) {

            }
        });

        checkMyPermission();
    }

    private void checkMyPermission() {
        if ((ContextCompat.checkSelfPermission(RecordScreenH264Activity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(RecordScreenH264Activity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1101);
        }else {

            mScreenCaptor.initCapture(RECORD_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1101) {

            Logger.i(TAG, "onRequestPermissionsResult setRecordResult");

            if (grantResults.length != 0 && ((grantResults[0] != PackageManager.PERMISSION_GRANTED) || (grantResults[1] != PackageManager.PERMISSION_GRANTED))) {
                Toast.makeText(RecordScreenH264Activity.this,"请设置必须的应用权限，否则将会导致运行异常！",Toast.LENGTH_SHORT).show();
            } else if (grantResults.length != 0 && (grantResults[0] == PackageManager.PERMISSION_GRANTED) && (grantResults[1] == PackageManager.PERMISSION_GRANTED)) {

                mScreenCaptor.initCapture(RECORD_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RECORD_REQUEST_CODE && resultCode == RESULT_OK) {

            Logger.i(TAG, "onActivityResult setRecordResult");
            mScreenCaptor.setRecordResult(resultCode, data);
        }
    }
}

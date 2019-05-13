package com.vonchenchen.mediacodecdemo;

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

import com.vonchenchen.mediacodecdemo.io.MediaDataWriter;
import com.vonchenchen.mediacodecdemo.video.Logger;
import com.vonchenchen.mediacodecdemo.video.ScreenRecorder;
//import com.android.util.*;

import java.io.FileNotFoundException;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RecordScreenTestActivity extends Activity {

    private static final String TAG = "RecordScreenTestActivity";

    private static final int RECORD_REQUEST_CODE = 101;

    private boolean mIsStartRecord = false;

    private ScreenRecorder mScreenRecorder;
    private Button mRecordBtn;

    private MediaDataWriter mMediaDataWriter;

    private byte[] mI420Data = new byte[1920*1080*3/2];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_record_screen);

        mRecordBtn = findViewById(R.id.btn_record);
        mRecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(!mIsStartRecord) {

                    try {
                        mMediaDataWriter = new MediaDataWriter("/sdcard/raw");
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }


                    mScreenRecorder.startCapture();
                    mRecordBtn.setText("stopRecord");

                }else {

                    mMediaDataWriter.close();
                    mMediaDataWriter = null;

                    mScreenRecorder.stopCapture();
                    mRecordBtn.setText("startRecord");
                }

                mIsStartRecord = !mIsStartRecord;
            }
        });

        mScreenRecorder = new ScreenRecorder(RecordScreenTestActivity.this);
        mScreenRecorder.setOnImageRecvListener(new ScreenRecorder.OnImageRecvListener() {
            @Override
            public void onImageRecv(byte[] data, int width, int height) {

                //mMediaDataWriter.write(data, data.length);
//                try {
//                    Thread.sleep(30);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
                long start = System.currentTimeMillis();
                conver_argb_to_i420(mI420Data, data, width, height);
                long end = System.currentTimeMillis();

                Logger.i(TAG, "lidechen_test spend="+(end-start));
            }
        });

        checkMyPermission();
    }

    private void checkMyPermission() {
        if ((ContextCompat.checkSelfPermission(RecordScreenTestActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(RecordScreenTestActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1101);
        }else {

            mScreenRecorder.initCapture(RECORD_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1101) {

            Logger.i(TAG, "onRequestPermissionsResult setRecordResult");

            if (grantResults.length != 0 && ((grantResults[0] != PackageManager.PERMISSION_GRANTED) || (grantResults[1] != PackageManager.PERMISSION_GRANTED))) {
                Toast.makeText(RecordScreenTestActivity.this,"请设置必须的应用权限，否则将会导致运行异常！",Toast.LENGTH_SHORT).show();
            } else if (grantResults.length != 0 && (grantResults[0] == PackageManager.PERMISSION_GRANTED) && (grantResults[1] == PackageManager.PERMISSION_GRANTED)) {

                mScreenRecorder.initCapture(RECORD_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RECORD_REQUEST_CODE && resultCode == RESULT_OK) {

            Logger.i(TAG, "onActivityResult setRecordResult");
            mScreenRecorder.setRecordResult(resultCode, data);
        }
    }

    public static void conver_argb_to_i420(byte[] i420, byte[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;                   // Y start index
        int uIndex = frameSize;           // U statt index
        int vIndex = frameSize*5/4; // V start index: w*h*5/4

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
//                a = (argb[index] & 0xff000000) >> 24; //  is not used obviously
//                R = (argb[index] & 0xff0000) >> 16;
//                G = (argb[index] & 0xff00) >> 8;
//                B = (argb[index] & 0xff) >> 0;

                B = argb[index];
                G = argb[index+1];
                R = argb[index+2];

                // well known RGB to YUV algorithm
                Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;

                // I420(YUV420p) -> YYYYYYYY UU VV
                i420[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && i % 2 == 0) {
                    i420[uIndex++] = (byte)((U<0) ? 0 : ((U > 255) ? 255 : U));
                    i420[vIndex++] = (byte)((V<0) ? 0 : ((V > 255) ? 255 : V));
                }
                index += 4;
            }
        }
    }
}

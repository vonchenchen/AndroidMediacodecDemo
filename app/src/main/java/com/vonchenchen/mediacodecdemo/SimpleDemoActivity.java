package com.vonchenchen.mediacodecdemo;

import android.app.Activity;
import android.media.MediaFormat;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.vonchenchen.mediacodecdemo.video.DecodeTask;

public class SimpleDemoActivity extends Activity{

    private SurfaceView mMainSurfaceView;
    private Button mStartRecord;
    private Button mStartPlay;

    private boolean mIsStartPlay = false;

    private DecodeTask mDecodeTask;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_simpledemo);

        mMainSurfaceView = findViewById(R.id.surface_main);
        mStartRecord = findViewById(R.id.btn_startRecord);
        mStartPlay = findViewById(R.id.btn_startPlay);

        mStartPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mIsStartPlay){
                    mStartPlay.setText("stopPlay");
                    mIsStartPlay = true;

                    //模拟不断收到h264流
                    mDecodeTask.initTask(1280, 720, mMainSurfaceView, MediaFormat.MIMETYPE_VIDEO_AVC);
                    mDecodeTask.startTask();
                }else {
                    mStartPlay.setText("startPlay");
                    mIsStartPlay = false;

                    mDecodeTask.stopTask();
                }
            }
        });

        String inputPathAvc = "/sdcard/test.h264";
        mDecodeTask = new DecodeTask(inputPathAvc);
    }
}

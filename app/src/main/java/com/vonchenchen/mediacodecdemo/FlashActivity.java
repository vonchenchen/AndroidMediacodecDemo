package com.vonchenchen.mediacodecdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

public class FlashActivity extends AppCompatActivity {

    private static final String TAG = "FlashActivity";

    private static final int PERMISSION_REQ_ID_CAM = 0;
    private static final int PERMISSION_REQ_ID_STORAGE = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        findViewById(R.id.next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(FlashActivity.this, SimpleDemoActivity.class));
            }
        });

        if(checkSelfPermission(FlashActivity.this, Manifest.permission.CAMERA, PERMISSION_REQ_ID_CAM)){
            //openCamera();
        }
    }

    public boolean checkSelfPermission(Activity activity, String permission, int requestCode) {
        Log.d(TAG, "checkSelfPermission " + permission + " " + requestCode);
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == PERMISSION_REQ_ID_CAM){
            checkSelfPermission(FlashActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_STORAGE);
            //openCamera();
        }
    }
}

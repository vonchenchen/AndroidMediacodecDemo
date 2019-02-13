package com.vonchenchen.mediacodecdemo.camera;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.SurfaceHolder;


import com.vonchenchen.mediacodecdemo.camera.interfaces.OnCameraPreviewListener;
import com.vonchenchen.mediacodecdemo.video.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Created by vonchenchen on 07/11/2017.
 */

public class CameraManager {

    private static int PIXEL_FORMAT = ImageFormat.NV21;

    private static String TAG = CameraManager.class.getName();

    private Camera mCamera;

    private int mCamIndex;
    private int mSizeIndex;
    private int mFps;

    private PixelFormat mPixelFormat = new PixelFormat();

    private static CameraManager sInstance = new CameraManager();

    private static OnCameraPreviewListener sOnCameraPreviewListener;
    private static int sWidth;
    private static int sHeight;

    private SurfaceTexture mDummySurfaceTexture;

    private boolean mIsStart = false;

    private static Camera.PreviewCallback sCallback = new Camera.PreviewCallback(){

        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {

            if(sOnCameraPreviewListener != null){
                Logger.d(TAG, "onPreviewFrame "+bytes.length);
                sOnCameraPreviewListener.onPreview(sWidth, sHeight, bytes);
            }
        }
    };

    private CameraManager(){

    }

    public static CameraManager getInstance(){
        return sInstance;
    }

    public void selectCamera(int camIndex, int sizeIndex, int fps){

        mCamIndex = camIndex;
        mSizeIndex = sizeIndex;
        mFps = fps;

        stopCapture();
        mCamera = Camera.open(camIndex);
        if(mCamera == null){
            Logger.e(TAG,"[selectCamera] open camera error mCamIndex="+mCamIndex+" mSizeIndex="+mSizeIndex);
        }
        //setCameraParameters(mCamera, mSizeIndex, mFps);
    }

    public int getCameraWidth(){
        return sWidth;
    }

    public int getCameraHeight(){
        return sHeight;
    }

    private void setCameraParameters(Camera camera, int sizeIndex, int fps){
        Camera.Parameters parameters = camera.getParameters();
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        Camera.Size previewSize = CameraUtils.getCamSizeList(camera).get(sizeIndex);

        sWidth = previewSize.width;
        sHeight = previewSize.height;

        parameters.setPreviewSize(previewSize.width, previewSize.height);
        Camera.Size pictureSize = CameraUtils.getLargePictureSize(camera);
        parameters.setPictureSize(pictureSize.width, pictureSize.height);
        //parameters.setRotation(90);
        //parameters.setPreviewFrameRate(fps);
        //parameters.setPreviewFpsRange(fps-3, fps+3);
        parameters.setPreviewFormat(PIXEL_FORMAT);

        PixelFormat.getPixelFormatInfo(PIXEL_FORMAT, mPixelFormat);

        int bufSize = previewSize.width * previewSize.height * mPixelFormat.bitsPerPixel / 8;
        //int bufSize = width * height * 3 / 2;
        byte[] buffer = null;
        for (int i = 0; i < 3; i++) {
            buffer = new byte[bufSize];
            camera.addCallbackBuffer(buffer);
        }

        camera.setParameters(parameters);
        camera.setPreviewCallback(sCallback);
    }

    public List<Camera.Size> getCamSizeList(){
        return CameraUtils.getCamSizeList(mCamera);
    }

    public List<Camera.Size> getCamSizeList(int camIndex){
        stopCapture();
        Camera camera = Camera.open(camIndex);
        List<Camera.Size> list = CameraUtils.getCamSizeList(camera);
        camera.release();
        return list;
    }

    public void startCapture(){
        if(mCamera != null) {
            try {
                Logger.i(TAG, "[startEncode] enter");

                setCameraParameters(mCamera, mSizeIndex, mFps);

                mDummySurfaceTexture = new SurfaceTexture(1);
                mCamera.setPreviewTexture(mDummySurfaceTexture);
                mCamera.startPreview();

                mIsStart = true;
            } catch (IOException e) {
                Logger.e(TAG, "[startEncode] Exception"+e.toString());
            }
        }
    }

    public void startCapture(SurfaceTexture surfaceTexture, int degrees){
        if(mCamera != null) {
            try {
                Logger.i(TAG, "[startEncode] enter");
                setCameraParameters(mCamera, mSizeIndex, mFps);

                mCamera.setPreviewTexture(surfaceTexture);
                mCamera.setDisplayOrientation(degrees);

                mCamera.startPreview();

                mIsStart = true;
            } catch (IOException e) {
                Logger.e(TAG, "[startEncode] Exception"+e.toString());
            }
        }
    }

    public void startCapture(SurfaceTexture surfaceTexture){
        startCapture(surfaceTexture, 0);
    }

    public void startCapture(SurfaceHolder holder){
        if(mCamera != null) {
            try {
                Logger.i(TAG, "[startEncode] enter");
                setCameraParameters(mCamera, mSizeIndex, mFps);

                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();

                mIsStart = true;
            } catch (IOException e) {
                Logger.e(TAG, "[startEncode] Exception"+e.toString());
            }
        }
    }

    public void stopCapture(){
        if(mCamera != null) {
            try {
                Logger.i(TAG, "[stopCapture] enter");
                mCamera.stopPreview();
                mCamera.setPreviewTexture(null);
                mCamera.setPreviewCallbackWithBuffer(null);
                //mCamera = null;
            } catch (IOException e) {
                Logger.e(TAG, "[stopCapture] Exception"+e.toString());
            }
        }
        mIsStart = false;
    }

    public void stopPreview(){
        if(mCamera != null) {
            Logger.i(TAG, "[stopPreview] enter");
            mCamera.stopPreview();
            try {
                mCamera.setPreviewTexture(null);
            } catch (IOException e) {
                Logger.e(TAG, "[stopPreview] Exception"+e.toString());
            }
        }
        mIsStart = false;
    }

    public boolean isCamStart(){
        return mIsStart;
    }

    public void closeCamera(){
        if(mCamera != null) {
            Logger.i(TAG, "[closeCamera] enter");
            mCamera.release();
            mCamera = null;
        }
    }

    public void setOnCameraPreviewListener(OnCameraPreviewListener onCameraPreviewListener){
        sOnCameraPreviewListener = onCameraPreviewListener;
    }

    public Camera.Size openCamera(int index, CameraSize size, int fps){

        Logger.i(TAG, "[openCamera] enter");

        List<Camera.Size> list = getCamSizeList(index);

        for(int i=0; i<list.size(); i++){
            Camera.Size size1 = list.get(i);
            Logger.i(TAG, "[openCamera] camera info width="+size1.width+" height="+size1.height);
        }

        int targetHeight = 0;
        if(size == CameraSize.SIZE_480P){
            targetHeight = 480;
        }else if(size == CameraSize.SIZE_720P){
            targetHeight = 720;
        }else if(size == CameraSize.SIZE_1080P){
            targetHeight = 1080;
        }else if(size == CameraSize.SIZE_2160P){
            targetHeight = 2160;
        }

        for(int i=0; i<list.size(); i++){
            if(list.get(i).height == targetHeight){
                CameraManager.getInstance().selectCamera(index, i, fps);
                Camera.Size size1 = list.get(i);
                return size1;
            }
        }

        Logger.e(TAG, "[openCamera] can not find right resolution !!! our height is targetHeight"+targetHeight);

        return null;
    }

    public enum CameraSize{
        SIZE_480P,
        SIZE_720P,
        SIZE_1080P,
        /** 4K */
        SIZE_2160P
    }

    public class CamSizeDetailInfo{
        public int width;
        public int height;
    }

    public CamSizeDetailInfo getCamSize(CameraSize size){
        CamSizeDetailInfo info = new CamSizeDetailInfo();
        if(size == CameraSize.SIZE_480P){
            info.width = 640;
            info.height = 480;
        }else if(size == CameraSize.SIZE_720P){
            info.width = 1280;
            info.height = 720;
        }else if(size == CameraSize.SIZE_1080P){
            info.width = 1920;
            info.height = 1080;
        }
        return info;
    }
}

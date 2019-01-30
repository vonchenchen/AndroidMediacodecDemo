package com.vonchenchen.mediacodecdemo.video;

import android.view.SurfaceView;

import com.vonchenchen.mediacodecdemo.io.H264DataReader;
import com.vonchenchen.mediacodecdemo.io.IStreamDataReader;

import java.io.FileNotFoundException;

public class DecodeTask extends Thread{

    private String TAG = "DecodeTask";

    private String mPath;

    private boolean mIsLoop = true;

    private H264DataReader mH264DataReader;

    private RenderTask mRenderTask;

    private int mPts = 0;

    private SurfaceView mRenderSurfaceView;
    private int mWidth;
    private int mHeight;
    private String mMediaFormatType;

    private boolean mIsDecodeTaskRunning = false;

    private DecodeTaskEventListener mDecodeTaskEventListener = null;

    public DecodeTask(String path){
        mPath = path;
    }

    public void initTask(int width, int height, SurfaceView surfaceView, String mediaFormatType){

        mWidth = width;
        mHeight = height;
        mRenderSurfaceView = surfaceView;
        mMediaFormatType = mediaFormatType;

        mIsLoop = true;

        try {
            mH264DataReader = new H264DataReader(mPath , 1280*720*3);
        } catch (FileNotFoundException e) {
            Logger.e(TAG, "[initRender] "+e.toString());
        }

        mH264DataReader.setOnDataParsedListener(new IStreamDataReader.OnDataParsedListener() {
            @Override
            public void onParsed(byte[] data, int index, int length) {

                Logger.e(TAG, "lidechen_test decode1 ");
                //解码线程中解码
                int ret = mRenderTask.decode(data, index, length, mPts++);
                Logger.e(TAG, "lidechen_test decode2 ret="+ret);

                //通知渲染线程渲染
                CodecMsg msg = new CodecMsg();
                msg.currentMsg = CodecMsg.MSG.MSG_DECODE_FRAME_READY;
                mRenderTask.pushAsyncNotify(msg);

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void startDecodeTask(){

        mRenderTask = new RenderTask();
        mRenderTask.setOnRenderEventListener(new RenderTask.OnRenderEventListener() {
            @Override
            public void onTaskStart() {

                if(!mIsDecodeTaskRunning) {
                    mIsDecodeTaskRunning = true;

                    //解码器与渲染环境准本就绪 可以开始解码和渲染
                    DecodeTask.this.start();
                }
            }

            @Override
            public void onTaskEnd() {

            }
        });
        mRenderTask.initRender(mWidth, mHeight, mRenderSurfaceView, mMediaFormatType);
        mRenderTask.startRender();
    }

    public void stopDecodeTask(){
        mIsLoop = false;
        mRenderTask.stopRender();
    }

    private void release(){
        //停止读取
        mH264DataReader.close();
    }

    @Override
    public void run() {

        int ret = 0;
        while (mIsLoop){

            try {
                ret = mH264DataReader.readNextFrame();
            }catch (Exception e){
                Logger.e(TAG, "[run] "+e.toString());
            }
            if(ret <= 0){
                mIsLoop = false;
            }
            Logger.d(TAG, "lidechen_test ret="+ret);
        }

        mIsDecodeTaskRunning = false;
        release();
        Logger.d(TAG, "lidechen_test decode thread end");

        mRenderTask.stopRender();

        if(mDecodeTaskEventListener != null){
            mDecodeTaskEventListener.onDecodeThreadEnd();
        }
    }

    public void setDecodeTaskEventListener(DecodeTaskEventListener listener){
        mDecodeTaskEventListener = listener;
    }

    public interface DecodeTaskEventListener{
        void onDecodeThreadEnd();
    }
}
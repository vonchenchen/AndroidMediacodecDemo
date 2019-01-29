package com.vonchenchen.mediacodecdemo.video;

import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.vonchenchen.mediacodecdemo.io.H264DataReader;
import com.vonchenchen.mediacodecdemo.io.IStreamDataReader;
import com.vonchenchen.mediacodecdemo.io.IVFDataReader;
import com.vonchenchen.mediacodecdemo.video.egl.EglCore;
import com.vonchenchen.mediacodecdemo.video.egl.WindowSurface;
import com.vonchenchen.mediacodecdemo.video.gles.FullFrameRect;
import com.vonchenchen.mediacodecdemo.video.gles.Texture2dProgram;

import java.io.FileNotFoundException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by vonchenchen on 2018/5/17.
 */

public class VideoDecodeProcessor {

    private static final String TAG = "VideoDecodeProcessor";

    private volatile boolean mIsDecodeLoop;

    private SurfaceView mSurfaceView;
    //private DirectDecoder mCircularDecoder;
    private SimpleDecoder mSimpleDecoder;

    private IStreamDataReader mStreamDataReader;
    private String mMediaFormatType = MediaFormat.MIMETYPE_VIDEO_AVC;
    private String mInPath;

    private int mPts = 0;

    private EglCore mEglCore;
    /** 接收解码数据纹理 glGenTexture生成 */
    private int mTextureId;
    /** 接收解码数据纹理 glGenTexture生成的纹理转化为SurfaceTexture 将其传递给MediaCodec */
    public SurfaceTexture mDecodeSurfaceTexture;
    /** 封装了顶点和片元的坐标 以及opengl program */
    private FullFrameRect mEXTTexDrawer;//TODO 记得改名

    /** 真实渲染surface */
    private WindowSurface mRendererWindowSurface;

    /** 外部画布传入的surface 作为egl环境的窗口 切换egl环境 会渲染到不同的窗口 */
    private Surface mRenderSurface;
    private Surface mDecodeSurface;

    private Semaphore mFrameSem = new Semaphore(0);
    private Object mRenderCtlLock = new Object();
    private boolean mRenderOk = false;

    private final float[] mDecodeMVPMatrix = new float[16];

    int mWidth;
    int mHeight;
    private Thread mDecodeThread;

    private int mSurfaceWidth;
    private int mSurfaceHeight;

    private boolean mIsLoop = false;

    public VideoDecodeProcessor(SurfaceView surfaceView, String path, String mediaFormatType){

        mSurfaceView = surfaceView;
        mInPath = path;
        mMediaFormatType = mediaFormatType;

        Matrix.setIdentityM(mDecodeMVPMatrix, 0);

        Utils.printMat(mDecodeMVPMatrix, 4, 4);

        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {

                mRenderSurface = surfaceHolder.getSurface();
                Logger.i(TAG, "surfaceCreated mRenderSurface="+mRenderSurface);

                mIsLoop = true;
                mIsDecodeLoop = true;

                synchronized (mRenderCtlLock) {
                    mRenderCtlLock.notify();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                mSurfaceWidth = mSurfaceView.getMeasuredWidth();
                mSurfaceHeight = mSurfaceView.getMeasuredHeight();
                Logger.i(TAG, "surfaceChanged mRenderSurface="+mRenderSurface);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

                Logger.i(TAG, "surfaceDestroyed mRenderSurface="+mRenderSurface);

                mIsLoop = false;
                mIsDecodeLoop = false;
            }
        });
    }

    public void startPlay(int width, final int height){
        mPts = 0;
        //创建解码器

        mWidth = width;
        mHeight = height;

        //mRenderSurface = mSurfaceView.getHolder().getSurface();

        try {
            if(mMediaFormatType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {

                mStreamDataReader = new H264DataReader(mInPath, width * height * 3);
            }else if(mMediaFormatType.equals(MediaFormat.MIMETYPE_VIDEO_VP8)){

                mStreamDataReader = new IVFDataReader(mInPath, width * height * 3);
            }

            //解码线程回调
            mStreamDataReader.setOnDataParsedListener(new IStreamDataReader.OnDataParsedListener() {
                @Override
                public void onParsed(byte[] data, int index, int length) {

                    //Logger.i(TAG, "lidechen_test length="+length+" onParsed "+Utils.byteArrayToHexString(data));

                    long start = System.currentTimeMillis();
                    //解码
                    //mCircularDecoder.decode(data, index, length, mPts+=1);
                    int ret = mSimpleDecoder.decode(data, index, length, mPts+=1);

                    long end = System.currentTimeMillis();
                    Logger.i(TAG,"lidechen_test spend="+(end-start));

                    //解码完毕 释放信号量 通知frame ok
                    mFrameSem.release();

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        mIsDecodeLoop = true;

        //initDecodeThread();

        mIsLoop = true;

        //render
        new Thread(new Runnable() {
            @Override
            public void run() {

                while (true) {

                    //如果surface没有就绪 则等待 如果serface就绪 当前所被释放
                    if(!mRenderSurface.isValid()) {
                        try {
                            synchronized (mRenderCtlLock) {
                                mRenderCtlLock.wait();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    initGLEnv1();

                    initDecodeThread();
                    //开启解码线程
                    mDecodeThread.start();

                    //开启渲染loop
                    renderLoop();

                    release();
                }
            }
        }).start();
    }

    private void initDecodeThread(){

        //decode
        mDecodeThread = new Thread(new Runnable() {
            @Override
            public void run() {

                    int ret = 0;
                    while (mIsDecodeLoop) {

                        try {
                            ret = mStreamDataReader.readNextFrame();
                            if (ret <= 0) {
                                break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (mOnVideoDecodeEventListener != null) {
                        mOnVideoDecodeEventListener.onDecodeFinish();
                    }
                    //mIsLoop = false;
            }
        });
    }

    private void renderLoop(){
        while (mIsLoop) {
            try {
                mFrameSem.tryAcquire(1000, TimeUnit.MICROSECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
            if(!mIsLoop){
                break;
            }

            mDecodeSurfaceTexture.updateTexImage();

            mDecodeSurfaceTexture.getTransformMatrix(mDecodeMVPMatrix);

            Utils.printMat(mDecodeMVPMatrix, 4, 4);

            mRendererWindowSurface.makeCurrent();
            GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);

            float[] vertex = ScaleUtils.getScaleVertexMat(mSurfaceWidth, mSurfaceHeight, 1280, 720);
            mEXTTexDrawer.rescaleDrawRect(vertex);

            try {
                mEXTTexDrawer.drawFrame(mTextureId, mDecodeMVPMatrix);
            } catch (Exception e) {
                Logger.e("lidechen_test", "drawFrame Exception="+e.toString());
                continue;
            }
            mRendererWindowSurface.swapBuffers();

            Logger.i("lidechen_test", "frame ok");
        }
    }

    private void initGLEnv1(){

        initDecoder();
    }

    private void initDecoder(){

        releaseDecoder();

        //建立一个临时SurfaceTexture 用来创建egl环境
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        //初始化渲染窗口
        mRendererWindowSurface = new WindowSurface(mEglCore, mRenderSurface, false);
        mRendererWindowSurface.makeCurrent();

        //drawer封装opengl
        mEXTTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        //绑定一个TEXTURE_2D纹理
        mTextureId = mEXTTexDrawer.createTextureObject();
        //创建一个SurfaceTexture用来接收MediaCodec的解码数据
        mDecodeSurfaceTexture = new SurfaceTexture(mTextureId);
        mDecodeSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                Logger.i("lidechen_test", "onFrameAvailable");
            }
        });
        //监听MediaCodec解码数据到 mDecodeSurfaceTexture
        //使用SurfaceTexture创建一个解码Surface
        mDecodeSurface = new Surface(mDecodeSurfaceTexture);
//        //mDecodeSurface绑定为当前Egl环境的surface 此surface最终会给到MediaCodec 所以不要在外面用这个surface创建egl的surface
//        mDecodeWindowSurface = new WindowSurface(mEglCore, mDecodeSurface, true);
        mSimpleDecoder = new SimpleDecoder(mWidth, mHeight, mDecodeSurface, mMediaFormatType);
    }

    public void release(){
        mIsDecodeLoop = false;

        releaseDecoder();

        mRenderSurface.release();

        mStreamDataReader.close();
    }

    private void releaseDecoder(){

        //关闭解码器
        if(mSimpleDecoder != null) {
            mSimpleDecoder.close();
        }
        if(mDecodeSurface != null) {
            mDecodeSurface.release();
        }
        if(mDecodeSurfaceTexture != null) {
            mDecodeSurfaceTexture.release();
        }
        if(mEXTTexDrawer != null) {
            mEXTTexDrawer.release(true);
        }
        if(mEglCore != null) {
            mEglCore.release();
        }

        if(mRendererWindowSurface != null) {
            mRendererWindowSurface.release();
        }
    }

    public void setOnVideoDecodeEventListener(OnVideoDecodeEventListener listener){
        mOnVideoDecodeEventListener = listener;
    }

    private OnVideoDecodeEventListener mOnVideoDecodeEventListener;

    public interface OnVideoDecodeEventListener{
        void onDecodeFinish();
    }
}

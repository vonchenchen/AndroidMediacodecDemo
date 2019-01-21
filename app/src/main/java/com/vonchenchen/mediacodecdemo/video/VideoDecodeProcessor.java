package com.vonchenchen.mediacodecdemo.video;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.EGLSurface;
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
import com.vonchenchen.mediacodecdemo.video.gl.EGLConfigAttrs;
import com.vonchenchen.mediacodecdemo.video.gl.EGLContextAttrs;
import com.vonchenchen.mediacodecdemo.video.gl.EglHelper;
import com.vonchenchen.mediacodecdemo.video.gl.FrameBuffer;
import com.vonchenchen.mediacodecdemo.video.gl.GpuUtils;
import com.vonchenchen.mediacodecdemo.video.gl.WrapRenderer;
import com.vonchenchen.mediacodecdemo.video.gles.FullFrameRect;
import com.vonchenchen.mediacodecdemo.video.gles.Texture2dProgram;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Created by vonchenchen on 2018/5/17.
 */

public class VideoDecodeProcessor {

    private static final String TAG = "VideoDecodeProcessor";

    private volatile boolean mStopPlay;

    private SurfaceView mSurfaceView;
    //private CircularDecoder mCircularDecoder;
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
    private FullFrameRect m2DTexDrawer;//TODO 记得改名

    /** 解码后先渲染到这个自建surface 再通过切换context 绘制到mSurface视窗 */
    private WindowSurface mDecodeWindowSurface;
    /** 真实渲染surface */
    private WindowSurface mRendererWindowSurface;

    /** 外部画布传入的surface 作为egl环境的窗口 切换egl环境 会渲染到不同的窗口 */
    private Surface mRenderSurface;
    private Surface mDecodeSurface;

    private Semaphore mFrameSem = new Semaphore(0);

    private final float[] mDecodeMVPMatrix = new float[16];

    int mWidth;
    int mHeight;
    private Thread mDecodeThread;
    private FrameBuffer mSourceFrame;
    private int mInputSurfaceTextureId;
    private EglHelper mEgl;
    private EGLSurface mShowSurface;

    public VideoDecodeProcessor(SurfaceView surfaceView, String path, String mediaFormatType){

        mSurfaceView = surfaceView;
        mInPath = path;
        mMediaFormatType = mediaFormatType;

        Matrix.setIdentityM(mDecodeMVPMatrix, 0);

        Utils.printMat(mDecodeMVPMatrix, 4, 4);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {

                mRenderSurface = surfaceHolder.getSurface();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            }
        });
    }

    public void startPlay(int width, int height){
        mPts = 0;
        //创建解码器
        //mCircularDecoder = new CircularDecoder(mSurfaceView, mMediaFormatType);
        //mSimpleDecoder = new SimpleDecoder(width, height, mDecodeSurface, mMediaFormatType);
        //mSimpleDecoder = new SimpleDecoder(width, height, mRenderSurface, mMediaFormatType);

        mWidth = width;
        mHeight = height;

        try {
            if(mMediaFormatType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {

                mStreamDataReader = new H264DataReader(mInPath, width * height * 3);
            }else if(mMediaFormatType.equals(MediaFormat.MIMETYPE_VIDEO_VP8)){

                mStreamDataReader = new IVFDataReader(mInPath, width * height * 3);
            }

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

                    //释放信号量 通知frame ok
                    mFrameSem.release();

//                    if(ret >= 0) {
//                        mDecodeWindowSurface.makeCurrent();
//                        //mDecodeSurfaceTexture.updateTexImage();
//                        m2DTexDrawer.drawFrame(mTextureId, mDecodeMVPMatrix);
//                        mDecodeWindowSurface.setPresentationTime(mDecodeSurfaceTexture.getTimestamp());
//                        mDecodeWindowSurface.swapBuffers();
//                    }

//                    mRendererWindowSurface.makeCurrent();
//                    //mDecodeSurfaceTexture.updateTexImage();
//                    GLES20.glViewport(0, 0, 300, 300);
//                    m2DTexDrawer.drawFrame(mTextureId, mDecodeMVPMatrix);
//                    mRendererWindowSurface.setPresentationTime(mDecodeSurfaceTexture.getTimestamp());
//                    mRendererWindowSurface.swapBuffers();
//
//                    mDecodeWindowSurface.makeCurrent();

                    //mCameraTexture.getTransformMatrix(mCameraMVPMatrix);

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

        mStopPlay = true;

        //decode
        mDecodeThread = new Thread(new Runnable() {
            @Override
            public void run() {

                int ret = 0;
                while (mStopPlay) {
                    ret = mStreamDataReader.readNextFrame();
                    if(ret <=0){
                        break;
                    }
                }

                if(mOnVideoDecodeEventListener != null){
                    mOnVideoDecodeEventListener.onDecodeFinish();
                }
            }
        });

        //render
        new Thread(new Runnable() {
            @Override
            public void run() {

                //initGLEnv();
                //initGLEnv1();
                initGLEnv2();

                mDecodeThread.start();

                while (true){

                    try {
                        mFrameSem.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }

                    mDecodeSurfaceTexture.updateTexImage();

                    //将纹理绘制到framebuffer
                    mDecodeSurfaceTexture.getTransformMatrix(mRenderer.getTextureMatrix());
                    //创建或者获取framebuffer 将解码器中自己创建的mInputSurfaceTextureId纹理Id先绘制到framebuffer
                    mSourceFrame.bindFrameBuffer(mWidth, mHeight);
                    GLES20.glViewport(0,0, mWidth, mHeight);
                    mRenderer.draw(mInputSurfaceTextureId);
                    mSourceFrame.unBindFrameBuffer();

                    //FrameBuffer中纹理id
                    int textureId = mSourceFrame.getCacheTextureId();
                    if(mShowSurface == null){
                        mShowSurface = mEgl.createWindowSurface(mRenderSurface);
                    }
                    mEgl.makeCurrent(mShowSurface);
                    GLES20.glViewport(0,0,mWidth,mHeight);
                    mRenderer.draw(textureId);
                    mEgl.swapBuffers(mShowSurface);

                    Logger.i("lidechen_test", "frame ok");
                }
            }
        }).start();
    }

    private void initGLEnv(){

        //mSimpleDecoder = new SimpleDecoder(width, height, mRenderSurface, mMediaFormatType);

        //为渲染窗口创建egl环境
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        //封装egl与对应的渲染surfaces
        mRendererWindowSurface = new WindowSurface(mEglCore, mRenderSurface, false);
        //使能egl
        mRendererWindowSurface.makeCurrent();

        //drawer封装opengl
        m2DTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));
        //绑定一个TEXTURE_2D纹理
        mTextureId = m2DTexDrawer.createTextureObject();
        //创建一个SurfaceTexture用来接收MediaCodec的解码数据
        mDecodeSurfaceTexture = new SurfaceTexture(mTextureId);
        //监听MediaCodec解码数据到 mDecodeSurfaceTexture
        mDecodeSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {

                Logger.i("lidechen_test", "[onFrameAvailable]");
            }
        });
        //使用SurfaceTexture创建一个解码Surface
        mDecodeSurface = new Surface(mDecodeSurfaceTexture);
        //mDecodeSurface绑定为当前Egl环境的surface
        mDecodeWindowSurface = new WindowSurface(mEglCore, mDecodeSurface, true);

        mDecodeWindowSurface.makeCurrent();

        //mSimpleDecoder = new SimpleDecoder(width, height, mDecodeSurface, mMediaFormatType);
    }

    private void initGLEnv1(){

        //建立一个临时SurfaceTexture 用来创建egl环境
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        SurfaceTexture tmpSurfaceTexture = new SurfaceTexture(0);
        Surface tmpSurface = new Surface(tmpSurfaceTexture);
        WindowSurface tmpWindowSurface  = new WindowSurface(mEglCore, tmpSurface, false);
        tmpWindowSurface.makeCurrent();

        //初始化解码窗口 解码后纹理先离屏渲染到framebuffer 再拿到framebuffer的纹理id 将其绘制到渲染窗口
        //drawer封装opengl
        m2DTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));
        //绑定一个TEXTURE_2D纹理
        mTextureId = m2DTexDrawer.createTextureObject();
        //创建一个SurfaceTexture用来接收MediaCodec的解码数据
        mDecodeSurfaceTexture = new SurfaceTexture(mTextureId);
        //监听MediaCodec解码数据到 mDecodeSurfaceTexture
//        mDecodeSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
//            @Override
//            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//                Logger.i("lidechen_test", "[onFrameAvailable]");
//            }
//        });
        //使用SurfaceTexture创建一个解码Surface
        mDecodeSurface = new Surface(mDecodeSurfaceTexture);
//        //mDecodeSurface绑定为当前Egl环境的surface 此surface最终会给到MediaCodec 所以不要在外面用这个surface创建egl的surface
//        mDecodeWindowSurface = new WindowSurface(mEglCore, mDecodeSurface, true);
        mSimpleDecoder = new SimpleDecoder(mWidth, mHeight, mDecodeSurface, mMediaFormatType);

        //初始化渲染窗口
        mRendererWindowSurface = new WindowSurface(mEglCore, mRenderSurface, false);
    }

    private WrapRenderer mRenderer;

    MediaCodec mediaCodec;
    private void initGLEnv2(){

        mEgl = new EglHelper();
        //此处使用一个空的SurfaceTexture建立egl 其中texName可以传任何数字 makecurrent
        boolean ret= mEgl.createGLESWithSurface(new EGLConfigAttrs(),new EGLContextAttrs(),new SurfaceTexture(0));
        if(!ret){
            //todo 错误处理
            return;
        }
        //创建一个gl纹理并包装 这个纹理将被传入MediaCodec
        mInputSurfaceTextureId = GpuUtils.createTextureID(true);
        mDecodeSurfaceTexture = new SurfaceTexture(mInputSurfaceTextureId);

        mSimpleDecoder = new SimpleDecoder(mWidth, mHeight, new Surface(mDecodeSurfaceTexture), mMediaFormatType);

        //建立一个render 用于给真正的显示surface渲染
        if(mRenderer==null){
            mRenderer=new WrapRenderer(null);
        }
        mSourceFrame = new FrameBuffer();
        mRenderer.create();
        mRenderer.sizeChanged(mWidth, mHeight);
        mRenderer.setFlag(WrapRenderer.TYPE_MOVE);

        Logger.i("lidechen_test", "initGLEnv2 ok");
    }

    public void stopPlay(){
        mStopPlay = false;
        //关闭解码器
        //mCircularDecoder.close();
        mSimpleDecoder.close();

        mStreamDataReader.close();
    }

    public void setOnVideoDecodeEventListener(OnVideoDecodeEventListener listener){
        mOnVideoDecodeEventListener = listener;
    }

    private OnVideoDecodeEventListener mOnVideoDecodeEventListener;

    public interface OnVideoDecodeEventListener{
        void onDecodeFinish();
    }
}

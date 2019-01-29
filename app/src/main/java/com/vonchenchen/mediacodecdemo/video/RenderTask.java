package com.vonchenchen.mediacodecdemo.video;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.vonchenchen.mediacodecdemo.video.egl.EglCore;
import com.vonchenchen.mediacodecdemo.video.egl.WindowSurface;
import com.vonchenchen.mediacodecdemo.video.gles.FullFrameRect;
import com.vonchenchen.mediacodecdemo.video.gles.Texture2dProgram;

public class RenderTask {

    private static final String TAG = "RenderTask";

    private MsgPipe<CodecMsg> mMsgQueue;

    private DecodeWrapper mDecodeWrapper;

    private EglCore mEglCore;
    /** 接收解码数据纹理 glGenTexture生成 */
    private int mTextureId;
    /** 接收解码数据纹理 glGenTexture生成的纹理转化为SurfaceTexture 将其传递给MediaCodec */
    public SurfaceTexture mDecodeSurfaceTexture;
    /** 封装了顶点和片元的坐标 以及opengl program */
    private FullFrameRect mEXTTexDrawer;

    /** 当前要渲染的SurfaceView */
    private SurfaceView mRenderSurfaceView;
    /** 真实渲染surface */
    private WindowSurface mRendererWindowSurface;

    /** 解码到自定义Surface */
    private Surface mDecodeSurface;

    private final float[] mDecodeMVPMatrix = new float[16];

    /** 外部画布传入的surface 作为egl环境的窗口 切换egl环境 会渲染到不同的窗口 */
    private Surface mRenderSurface;
    private int mWidth;
    private int mHeight;
    private String mMediaFormatType;

    private int mSurfaceWidth;
    private int mSurfaceHeight;

    private OnRenderEventListener mOnRenderEventListener = null;

    public RenderTask(){

        mMsgQueue = new MsgPipe();
    }

    public void init(int width, int height, SurfaceView surfaceView, String mediaFormatType){

        mWidth = width;
        mHeight = height;
        mRenderSurfaceView = surfaceView;

        mRenderSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                mRenderSurface = surfaceHolder.getSurface();
                mSurfaceWidth = mRenderSurfaceView.getMeasuredWidth();
                mSurfaceHeight = mRenderSurfaceView.getMeasuredHeight();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

                mSurfaceWidth = mRenderSurfaceView.getMeasuredWidth();
                mSurfaceHeight = mRenderSurfaceView.getMeasuredHeight();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            }
        });

        if(surfaceView.getHolder().getSurface().isValid()){
            mRenderSurface = surfaceView.getHolder().getSurface();
            mSurfaceWidth = mRenderSurfaceView.getMeasuredWidth();
            mSurfaceHeight = mRenderSurfaceView.getMeasuredHeight();
        }

        mMsgQueue.setOnPipeListener(new MsgPipe.OnPipeListener<CodecMsg>() {
            @Override
            public void onPipeStart() {

                Logger.i(TAG, "lidechen_test onPipeStart");
                initGLEnv();

                if(mOnRenderEventListener != null){
                    mOnRenderEventListener.onTaskStart();
                }
            }

            @Override
            public void onPipeRecv(CodecMsg msg) {

                int ret = 0;

                if(msg.currentMsg == CodecMsg.MSG.MSG_DECODE_FRAME_READY){
                    //解码成功
                    ret = renderToRenderSurface();
                    Logger.i(TAG, "lidechen_test renderToRenderSurface ret="+ret);
                }else if(msg.currentMsg == CodecMsg.MSG.MSG_STOP_RENDER_TASK){

                }
            }

            @Override
            public void onPipeRelease() {

                if(mOnRenderEventListener != null){
                    mOnRenderEventListener.onTaskEnd();
                }
            }
        });
        mMsgQueue.startPipe();
    }

    /**
     * 加入通知事件
     * @param codecMsg
     */
    public void pushAsyncNotify(CodecMsg codecMsg){

        //收到消息后触发onPipeRecv
        mMsgQueue.addLast(codecMsg);
    }

    private void initGLEnv(){

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
        //mSimpleDecoder = new SimpleDecoder(mWidth, mHeight, mDecodeSurface, mMediaFormatType);
        mDecodeWrapper = new DecodeWrapper();
        mDecodeWrapper.init(mWidth, mHeight, mDecodeSurface, mMediaFormatType);
    }

    /**
     * 渲染到外部SurfaceView对应的surface上
     */
    private int renderToRenderSurface(){

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
            return -1;
        }
        mRendererWindowSurface.swapBuffers();
        return 0;
    }

    public int decode(byte[] input, int offset, int count , long pts){
        Logger.d(TAG,"lidechen_test debug0.5");
        return mDecodeWrapper.decode(input, offset, count, pts);
    }

    public void setOnRenderEventListener(OnRenderEventListener onRenderEventListener){
        mOnRenderEventListener = onRenderEventListener;
    }

    public interface OnRenderEventListener{
        void onTaskStart();
        void onTaskEnd();
    }
}

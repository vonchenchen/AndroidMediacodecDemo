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
    private String mMediaFormatType;

    /** mRenderSurfaceView的holder */
    private SurfaceHolder mRenderSurfaceHolder;
    private HolderCallback mHolderCallback;

    /** 真实渲染surface */
    private WindowSurface mRendererWindowSurface;

    /** 解码到自定义Surface */
    private Surface mDecodeSurface;

    private final float[] mDecodeMVPMatrix = new float[16];

    /** 外部画布传入的surface 作为egl环境的窗口 切换egl环境 会渲染到不同的窗口 */
    private Surface mRenderSurface;
    private int mCurrentFrameWidth;
    private int mCurrentFrameHeight;

    private int mSurfaceWidth;
    private int mSurfaceHeight;

    /** 渲染环境是否就绪 只有就绪才能渲染 */
    private boolean mIsRenderEnvReady = false;

    private OnRenderEventListener mOnRenderEventListener = null;

    public RenderTask(){

        mMsgQueue = new MsgPipe();
        mHolderCallback = new HolderCallback();
    }

    public void initRender(int width, int height, SurfaceView surfaceView, String mediaFormatType){

        mCurrentFrameWidth = width;
        mCurrentFrameHeight = height;
        mRenderSurfaceView = surfaceView;
        mMediaFormatType = mediaFormatType;

        mRenderSurfaceHolder = mRenderSurfaceView.getHolder();

        mRenderSurfaceHolder.addCallback(mHolderCallback);

        //如果当前渲染surface就绪 则赋值  否则在就绪回调中赋值
        if(surfaceView.getHolder().getSurface().isValid()){
            mRenderSurface = mRenderSurfaceHolder.getSurface();
            mSurfaceWidth = mRenderSurfaceView.getMeasuredWidth();
            mSurfaceHeight = mRenderSurfaceView.getMeasuredHeight();
        }

        mMsgQueue.setOnPipeListener(new MsgPipe.OnPipeListener<CodecMsg>() {
            @Override
            public void onPipeStart() {

                Logger.i(TAG, "lidechen_test onPipeStart");
            }

            @Override
            public void onPipeRecv(CodecMsg msg) {

                int ret = 0;

                if(msg.currentMsg == CodecMsg.MSG.MSG_RESUME_RENDER_TASK) {

                    Logger.d(TAG, "[onPipeRecv] MSG_RESUME_RENDER_TASK");

                    initGLEnv();
                    mIsRenderEnvReady = true;

                    if(mOnRenderEventListener != null){
                        mOnRenderEventListener.onTaskPrepare();
                    }
                }else if(msg.currentMsg == CodecMsg.MSG.MSG_PAUSE_RENDER_TASK){

                    mIsRenderEnvReady = false;

                    Logger.d(TAG, "[onPipeRecv] MSG_PAUSE_RENDER_TASK");

                    mRenderSurfaceHolder.addCallback(mHolderCallback);
                    releaseRender();

                }else if(msg.currentMsg == CodecMsg.MSG.MSG_DECODE_FRAME_READY){

                    Logger.d(TAG, "[onPipeRecv] MSG_DECODE_FRAME_READY");


                    if(!mIsRenderEnvReady){
                        return;
                    }

                    //解码成功 开始渲染
                    //try {
                        ret = renderToRenderSurface();
                    //}catch (Exception e){
                    //    Logger.e(TAG, "lidechen_test onPipeRecv "+e.toString());
                    //}
                    //Logger.i(TAG, "lidechen_test renderToRenderSurface ret="+ret);
                }else if(msg.currentMsg == CodecMsg.MSG.MSG_STOP_RENDER_TASK){

                    Logger.d(TAG, "[onPipeRecv] MSG_STOP_RENDER_TASK");

                    //停止解码任务
                    mMsgQueue.stopPipe();

                    //发一条空消息 避免线程等待
                    CodecMsg msgEmpty = new CodecMsg();
                    mMsgQueue.addFirst(msgEmpty);
                }
            }

            @Override
            public void onPipeRelease() {

                //任务停止后清除资源
                release();

                if(mOnRenderEventListener != null){
                    mOnRenderEventListener.onTaskEnd();
                }
            }
        });

    }

    public void startRender(){

        mMsgQueue.startPipe();

        //清空消息管道 发送开始渲染消息
        mMsgQueue.clearPipeData();
        CodecMsg msg = getResumeRenderMsg();
        mMsgQueue.addLast(msg);
    }

    private CodecMsg getResumeRenderMsg(){
        CodecMsg msg = new CodecMsg();
        msg.currentMsg = CodecMsg.MSG.MSG_RESUME_RENDER_TASK;
        return msg;
    }

    private CodecMsg getPauseRenderMsg(){
        CodecMsg msg = new CodecMsg();
        msg.currentMsg = CodecMsg.MSG.MSG_PAUSE_RENDER_TASK;
        return msg;
    }

    public void stopRender(){

        CodecMsg codecMsg = new CodecMsg();
        codecMsg.currentMsg = CodecMsg.MSG.MSG_STOP_RENDER_TASK;
        mMsgQueue.addLast(codecMsg);
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

        if(mEglCore == null){
            mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        }

        //初始化渲染窗口
        mRendererWindowSurface = new WindowSurface(mEglCore, mRenderSurface, false);
        mRendererWindowSurface.makeCurrent();

        if(mEXTTexDrawer == null) {
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

            mDecodeWrapper = new DecodeWrapper();
            mDecodeWrapper.init(mCurrentFrameWidth, mCurrentFrameHeight, mDecodeSurface, mMediaFormatType);
            mDecodeWrapper.setOnDecoderEnventLisetener(new SimpleDecoder.OnDecoderEnventLisetener() {
                @Override
                public void onFrameSizeInit(int frameWidth, int frameHeight) {

                }

                @Override
                public void onFrameSizeChange(int frameWidth, int frameHeight) {

                }
            });
        }
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

        float[] vertex = ScaleUtils.getScaleVertexMat(mSurfaceWidth, mSurfaceHeight, mCurrentFrameWidth, mCurrentFrameHeight);
        mEXTTexDrawer.rescaleDrawRect(vertex);

        mEXTTexDrawer.drawFrame(mTextureId, mDecodeMVPMatrix);
        mRendererWindowSurface.swapBuffers();
        return 0;
    }

    public int decode(byte[] input, int offset, int count , long pts){

//        byte[] data = new byte[50];
//        System.arraycopy(input, offset, data, 0, 50);
//        String str = Utils.byteArrayToHexString(data);
//        Logger.i(TAG, "lidechen_test decode="+str);

        return mDecodeWrapper.decode(input, offset, count, pts);
    }

    /**
     * 只释放绘制相关 不释放解码相关
     */
    private void releaseRender(){

        mMsgQueue.clearPipeData();

        if(mRendererWindowSurface != null){
            mRendererWindowSurface.release();
            mRendererWindowSurface = null;
        }
    }

    private void release(){

        mMsgQueue.clearPipeData();

        if(mRendererWindowSurface != null){
            mRendererWindowSurface.release();
            mRendererWindowSurface = null;
        }

        if(mEXTTexDrawer != null){
            mEXTTexDrawer.release(false);
            mEXTTexDrawer = null;
        }

        if(mDecodeSurfaceTexture != null){
            mDecodeSurfaceTexture.release();
            mDecodeSurfaceTexture = null;
        }

        if(mDecodeSurface != null){
            mDecodeSurface.release();
            mDecodeSurface = null;
        }

        //先释放渲染surface相关 再释放egl相关 否则再次使用渲染surface创建egl环境报错
        if(mEglCore != null){
            mEglCore.release();
            mEglCore = null;
        }

        if(mDecodeWrapper != null) {
            mDecodeWrapper.release();
            mDecodeWrapper = null;
        }
    }

    public void setOnRenderEventListener(OnRenderEventListener onRenderEventListener){
        mOnRenderEventListener = onRenderEventListener;
    }

    public interface OnRenderEventListener{
        void onTaskPrepare();
        void onTaskEnd();
    }

    class HolderCallback implements SurfaceHolder.Callback{

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            mRenderSurface = surfaceHolder.getSurface();

            mSurfaceWidth = mRenderSurfaceView.getMeasuredWidth();
            mSurfaceHeight = mRenderSurfaceView.getMeasuredHeight();

            CodecMsg msg = getResumeRenderMsg();
            mMsgQueue.addFirst(msg);

            Logger.i(TAG, "lidechen_test surface surfaceCreated");
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            mSurfaceWidth = mRenderSurfaceView.getMeasuredWidth();
            mSurfaceHeight = mRenderSurfaceView.getMeasuredHeight();

            Logger.i(TAG, "lidechen_test surface surfaceChanged");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            CodecMsg msg = getPauseRenderMsg();
            mMsgQueue.addFirst(msg);

            Logger.i(TAG, "lidechen_test surface surfaceDestroyed");
        }
    }
}

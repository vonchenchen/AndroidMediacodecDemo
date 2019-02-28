package com.vonchenchen.mediacodecdemo.video;

import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.vonchenchen.mediacodecdemo.video.egl.EglCore;
import com.vonchenchen.mediacodecdemo.video.egl.WindowSurface;
import com.vonchenchen.mediacodecdemo.video.gles.FullFrameRect;
import com.vonchenchen.mediacodecdemo.video.gles.Texture2dProgram;

public class EncodeTask {

	static private final String TAG = "EncodeTask";
	static private final int MSG_FRAME_AVAILABLE = 1;
	static private final int MSG_FINISH = 2;

	private MsgPipe<CodecMsg> mMsgQueue;

	/** 接收相机纹理 glGenTexture生成 */
	private int mTextureId;
	/** 接收相机纹理 glGenTexture生成的纹理转化为SurfaceTexture 将其传递给相机作为相机绘制的纹理 */
    public SurfaceTexture mCameraTexture;

    /** 封装了顶点和片元的坐标 以及opengl program */
    private FullFrameRect mInternalTexDrawer;

    /** 外部画布传入的surface 作为egl环境的窗口 切换egl环境 会渲染到不同的窗口 */
    private SurfaceView mRenderSurfaceView;
	private Surface mRenderSurface;

    /** 相机矩阵 由当前相机纹理生成的SurfaceTexture获取 */
    private final float[] mCameraMVPMatrix = new float[16];
    /** 编码视频矩阵 mCameraMVPMatrix X mEncodeTextureMatrix */
    private final float[] mEncodeHDMatrix = new float[16];
	private final float[] mEncodeMatrix = new float[16];
	/** 处理当前编码图像的 平移 旋转 缩放 */
    private final float[] mEncodeTextureMatrix = new float[16];

    /** 控件宽高 用于计算比例 */
	private int mSurfaceWidth;
	private int mSurfaceHeight;

	/** 相机采集宽高 */
    private int mCaptureWidth;
    private int mCaptureHeight;

    /** 小流宽高 */
    private int mStreamWidth;
	private int mStreamHeight;

	/** 大流宽高 */
	private int mHDStreamWidth;
	private int mHEStreamHeight;

	private int mFrameRate;

	private EglCore mEglCore;
	/** 非高清编码surface Surface通过MediaCodec的createInputSurface创建 每个surface维护一个egl句柄
	 *  渲染后相当于直接将数据传递给MediaCodec*/
	private WindowSurface mEncodeWindowSurface;
	private WindowSurface mHDEncodeWindowSurface;
	/** 渲染surface 由外部需要渲染的画布传入 */
	private WindowSurface mRenderWindowSurface;

	private SimpleEncoder mSimpleEncoder;
	private SimpleEncoder mHDEncoder;

	private EncodeInfo mEncodeInfo;

	private SurfaceHolder mSurfaceHolder;
	private int mFrameCount = 0;

	/** 渲染surface的holder */
	private HolderCallback mHolderCallback;

	private SimpleEncoder.OnCricularEncoderEventListener mOnCricularEncoderEventListener;
	private OnEncodeTaskEventListener mOnEncodeTaskEventListener;

	private float[] mBaseScaleVertexBuf = {
			// 0 bottom left
			-1.0f, -1.0f,
			// 1 bottom right
			1.0f, -1.0f,
			// 2 top left
			-1.0f,  1.0f,
			// 3 top right
			1.0f,  1.0f,
	};

	public EncodeTask(SurfaceView surfaceView , int width , int height, int frameRate, SimpleEncoder.OnCricularEncoderEventListener listener) {

		mMsgQueue = new MsgPipe<>();

		mRenderSurfaceView = surfaceView;
		mSurfaceHolder = mRenderSurfaceView.getHolder();
		mHolderCallback = new HolderCallback();
		mSurfaceHolder.addCallback(mHolderCallback);

		mRenderSurface = mSurfaceHolder.getSurface();

		mCaptureWidth = width;
		mCaptureHeight = height;

		if(mRenderSurface.isValid()) {
			mSurfaceWidth = mRenderSurfaceView.getMeasuredWidth();
			mSurfaceHeight = mRenderSurfaceView.getMeasuredHeight();
		}

		mFrameRate = frameRate;

		mOnCricularEncoderEventListener = listener;
	}

	/**
	 * 设置
	 * @param encodeInfo
	 */
	public void setEncodeInfo(EncodeInfo encodeInfo){
		mEncodeInfo = encodeInfo;
	}

	public void startEncodeTask(){

		mMsgQueue.setOnPipeListener(new MsgPipe.OnPipeListener<CodecMsg>() {
			@Override
			public void onPipeStart() {

				initMats();
			}

			@Override
			public void onPipeRecv(CodecMsg msg) {

				if(msg.currentMsg == CodecMsg.MSG.MSG_ENCODE_CAPTURE_FRAME_READY){

					renderAndEncode();
				}else if(msg.currentMsg == CodecMsg.MSG.MSG_ENCODE_RESUME_RENDER){

					initGL();
				}else if(msg.currentMsg == CodecMsg.MSG.MSG_ENCODE_PAUSE_RENDER){

					releaseRender();
				}else if(msg.currentMsg == CodecMsg.MSG.MSG_ENCODE_STOP_TASK){

					//停止解码任务
					mMsgQueue.stopPipe();
					//发一条空消息 避免线程等待
					CodecMsg msgEmpty = new CodecMsg();
					mMsgQueue.addFirst(msgEmpty);
				}else if(msg.currentMsg == CodecMsg.MSG.MSG_ENCODE_CHANGE_BITRATE){
					//改变编码码率
					resetEncodeBitrate(msg);
				}
			}

			@Override
			public void onPipeRelease() {

				release();
			}
		});

		mMsgQueue.clearPipeData();
		pushCodecMsgFirst(CodecMsg.MSG.MSG_ENCODE_RESUME_RENDER);
		mMsgQueue.startPipe();
	}

	public void stopEncodeTask(){

		pushCodecMsgFirst(CodecMsg.MSG.MSG_ENCODE_STOP_TASK);
	}

	private void initMats(){

		Matrix.setIdentityM(mEncodeTextureMatrix, 0);
		Matrix.translateM(mEncodeTextureMatrix, 0, 0.5f, 0.5f, 0);
		// 左右镜像编码后视频 flip
		Matrix.rotateM(mEncodeTextureMatrix, 0, 180, 0, 1, 0);
		Matrix.translateM(mEncodeTextureMatrix, 0, -0.5f, -0.5f, 0);
	}

	private void initGL() {

		if(mEglCore == null) {
			//创建egl环境
			mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
		}

		if(!mRenderSurface.isValid()){
			//如果mRenderSurface没有就绪 直接退出 surfaceCreated触发后会再次触发MSG_ENCODE_RESUME_RENDER事件 调用initGL()
			Logger.i(TAG, "mRenderSurface is not valid");
			return;
		}

		//封装egl与对应的surface
		mRenderWindowSurface = new WindowSurface(mEglCore, mRenderSurface, false);
		mRenderWindowSurface.makeCurrent();

		if(mInternalTexDrawer == null) {
			//drawer封装opengl program相关
			mInternalTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
			//mInternalTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_BW));
			//mInternalTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_FILT));
			//绑定一个纹理 根据TEXTURE_EXT 内部绑定一个相机纹理
			mTextureId = mInternalTexDrawer.createTextureObject();
			//使用纹理创建SurfaceTexture 用来接收相机数据
			mCameraTexture = new SurfaceTexture(mTextureId);
			//监听接收数据
			mCameraTexture.setOnFrameAvailableListener(new OnFrameAvailableListener() {
				@Override
				public void onFrameAvailable(SurfaceTexture surfaceTexture) {
					//相机采集到一帧画面
					CodecMsg codecMsg = new CodecMsg();
					codecMsg.currentMsg = CodecMsg.MSG.MSG_ENCODE_CAPTURE_FRAME_READY;
					mMsgQueue.addLast(codecMsg);
				}
			});

			if(mOnEncodeTaskEventListener != null){
				mOnEncodeTaskEventListener.onCameraTextureReady(mCameraTexture);
			}

			mStreamWidth = mCaptureWidth;
			mStreamHeight = mCaptureHeight;

			mSimpleEncoder = new SimpleEncoder(mStreamWidth, mStreamHeight, mFrameRate, MediaFormat.MIMETYPE_VIDEO_AVC, true, mEncodeInfo);
			mSimpleEncoder.setOnCricularEncoderEventListener(mOnCricularEncoderEventListener);
			//getInputSurface()最终获取的是MediaCodec调用createInputSurface()方法创建的Surface
			//这个Surface传入当前egl环境 作为egl的窗口参数(win) 通过eglCreateWindowSurface与egldisplay进行关联
			mEncodeWindowSurface = new WindowSurface(mEglCore, mSimpleEncoder.getInputSurface(), true);

			if (mHDEncoder != null) {
				mHDEncodeWindowSurface = new WindowSurface(mEglCore, mHDEncoder.getInputSurface(), true);
			}
		}
	}

	/**
	 * 获取本地opengl纹理包装后的SurfaceTexture 将传递给相机绘制
	 * @return
	 */
	public SurfaceTexture getCameraTexture(){
		return mCameraTexture;
	}

	/** 丢帧计数器 */
	private int mFrameSkipCnt = 0;

	private void renderAndEncode() {
        //Log.d(TAG, "drawFrame");
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }

		mCameraTexture.updateTexImage();

        /********* draw to Capture Window **********/
        // Latch the next frame from the camera.
		if(mRenderWindowSurface != null) {
			mRenderWindowSurface.makeCurrent();

			//用于接收相机预览纹理id的SurfaceTexture
			//updateTexImage()方法在OpenGLES环境调用 将数据绑定给OpenGLES对应的纹理对象GL_OES_EGL_image_external 对应shader中samplerExternalOES
			//updateTexImage 完毕后接收下一帧
			//由于在OpenGL ES中，上传纹理（glTexImage2D(), glSubTexImage2D()）是一个极为耗时的过程，在1080×1920的屏幕尺寸下传一张全屏的texture需要20～60ms。这样的话SurfaceFlinger就不可能在60fps下运行。
			//因此， Android采用了image native buffer,将graphic buffer直接作为纹理（direct texture）进行操作

			mCameraTexture.getTransformMatrix(mCameraMVPMatrix);
			//显示图像全部 glViewport 传入当前控件的宽高
			GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);

			//Matrix.rotateM(mCameraMVPMatrix, 0, 270, 0, 0, 1);

			//通过修改顶点坐标 将采集到的视频按比例缩放到窗口中
			float[] drawVertexMat = ScaleUtils.getScaleVertexMat(mSurfaceWidth, mSurfaceHeight, mCaptureWidth, mCaptureHeight);
			mInternalTexDrawer.rescaleDrawRect(drawVertexMat);
			mInternalTexDrawer.drawFrame(mTextureId, mCameraMVPMatrix);

			mRenderWindowSurface.swapBuffers();
		}

		mFrameCount ++;

		//丢帧
//		mFrameSkipCnt++;
//		if(mFrameSkipCnt % 2 == 0){
//			return;
//		}

		if(mHDEncoder != null) {
			if(mFrameCount == 1) {
				//mHDEncoder.requestKeyFrame();
			}

			mHDEncodeWindowSurface.makeCurrent();
			// 给编码器显示的区域
			GLES20.glViewport(0, 0, mHDEncoder.getWidth() , mHDEncoder.getHeight());
			// 如果是横屏 不需要设置
			Matrix.multiplyMM(mEncodeHDMatrix, 0, mCameraMVPMatrix, 0, mEncodeTextureMatrix, 0);
			// 恢复为基本scales
			mInternalTexDrawer.rescaleDrawRect(mBaseScaleVertexBuf);
			// 下面往编码器绘制数据
			mInternalTexDrawer.drawFrame(mTextureId, mEncodeHDMatrix);
			mHDEncoder.frameAvailableSoon();
			mHDEncodeWindowSurface.setPresentationTime(mCameraTexture.getTimestamp());
			mHDEncodeWindowSurface.swapBuffers();
		}

		if(mSimpleEncoder != null) {
			if(mFrameCount == 1 /*|| mFrameCount%10 == 0*/) {
				//mSimpleEncoder.requestKeyFrame();
			}

			// 切到当前egl环境
			mEncodeWindowSurface.makeCurrent();
			// 给编码器显示的区域
			GLES20.glViewport(0, 0, mSimpleEncoder.getWidth() , mSimpleEncoder.getHeight());
			// 如果是横屏 不需要设置
			Matrix.multiplyMM(mEncodeMatrix, 0, mCameraMVPMatrix, 0, mEncodeTextureMatrix, 0);
			// 恢复为基本scale
			mInternalTexDrawer.rescaleDrawRect(mBaseScaleVertexBuf);
			// 下面往编码器绘制数据  mEncoderSurface中维护的egl环境中的win就是 mEncoder中MediaCodec中的surface
			// 也就是说这一步其实是往编码器MediaCodec中放入了数据
			mInternalTexDrawer.drawFrame(mTextureId, mEncodeMatrix);
			//通知从MediaCodec中读取编码完毕的数据
			mSimpleEncoder.frameAvailableSoon();
			mEncodeWindowSurface.setPresentationTime(mCameraTexture.getTimestamp());
			mEncodeWindowSurface.swapBuffers();

			Logger.i("lidechen_test", "test3");
			//mEncodeWindowSurface.readImageTest();
		}
	}

	void resetEncodeBitrate(CodecMsg msg){

		mEncodeInfo.bitrate = msg.encodeInfo.bitrate;
		if(mSimpleEncoder != null){
			mSimpleEncoder.changeBitrate(mEncodeInfo.bitrate);
		}
		if(mHDEncoder != null){
			mSimpleEncoder.changeBitrate(mEncodeInfo.bitrate);
		}
	}

	private void releaseRender(){

		mMsgQueue.clearPipeData();

		if(mRenderWindowSurface != null){
			//解除egl绑定的当前eglsurface 释放Android本地surface
			mRenderWindowSurface.release();
			mRenderWindowSurface = null;
		}
	}

	private void pushCodecMsgLast(CodecMsg.MSG msg){

		CodecMsg codecMsg = new CodecMsg();
		codecMsg.currentMsg = msg;
		mMsgQueue.addLast(codecMsg);
	}

	private void pushCodecMsgFirst(CodecMsg.MSG msg){

		CodecMsg codecMsg = new CodecMsg();
		codecMsg.currentMsg = msg;
		mMsgQueue.addFirst(codecMsg);
	}

	public void changeBitrate(int bitrate){

		CodecMsg codecMsg = new CodecMsg();
		codecMsg.currentMsg = CodecMsg.MSG.MSG_ENCODE_CHANGE_BITRATE;
		codecMsg.encodeInfo.bitrate = bitrate;
		mMsgQueue.addFirst(codecMsg);
	}

    public void release(){

		if (mHDEncoder != null) {
			mHDEncoder.shutdown();
			mHDEncoder = null;
		}

		if(mSimpleEncoder != null){
			mSimpleEncoder.shutdown();
			mSimpleEncoder = null;
		}

		if (mHDEncodeWindowSurface != null) {
			mHDEncodeWindowSurface.release();
			mHDEncodeWindowSurface = null;
		}

		if (mEncodeWindowSurface != null) {
			mEncodeWindowSurface.release();
			mEncodeWindowSurface = null;
		}

		if (mRenderWindowSurface != null) {
			mRenderWindowSurface.release();
			mRenderWindowSurface = null;
		}

		if (mInternalTexDrawer != null) {
			mInternalTexDrawer.release(true);
			mInternalTexDrawer = null;
		}

		if (mEglCore != null) {
			mEglCore.release();
			mEglCore = null;
		}

		if (mCameraTexture != null) {
			mCameraTexture.release();
			mCameraTexture = null;
		}

		if(mSurfaceHolder != null){
			mSurfaceHolder.removeCallback(mHolderCallback);
		}
	}

	class HolderCallback implements SurfaceHolder.Callback{

		@Override
		public void surfaceCreated(SurfaceHolder surfaceHolder) {

			mRenderSurface = surfaceHolder.getSurface();
			mSurfaceWidth = mRenderSurfaceView.getMeasuredWidth();
			mSurfaceHeight = mRenderSurfaceView.getMeasuredHeight();

			pushCodecMsgFirst(CodecMsg.MSG.MSG_ENCODE_RESUME_RENDER);
		}

		@Override
		public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

			mSurfaceWidth = mRenderSurfaceView.getMeasuredWidth();
			mSurfaceHeight = mRenderSurfaceView.getMeasuredHeight();
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

			pushCodecMsgFirst(CodecMsg.MSG.MSG_ENCODE_PAUSE_RENDER);
		}
	}

	public void setOnEncodeTaskEventListener(OnEncodeTaskEventListener onEncodeTaskEventListener){
		mOnEncodeTaskEventListener = onEncodeTaskEventListener;
	}

	public interface OnEncodeTaskEventListener{
		void onCameraTextureReady(SurfaceTexture camSurfaceTexture);
	}
}

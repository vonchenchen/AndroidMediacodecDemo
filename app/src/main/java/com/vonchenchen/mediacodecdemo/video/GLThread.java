package com.vonchenchen.mediacodecdemo.video;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.vonchenchen.mediacodecdemo.video.egl.EglCore;
import com.vonchenchen.mediacodecdemo.video.egl.WindowSurface;
import com.vonchenchen.mediacodecdemo.video.gles.FullFrameRect;
import com.vonchenchen.mediacodecdemo.video.gles.Texture2dProgram;

import java.io.IOException;

public class GLThread extends HandlerThread implements OnFrameAvailableListener, SurfaceHolder.Callback{

	static private final String TAG = "GLThread";
	static private final int MSG_FRAME_AVAILABLE = 1;
	static private final int MSG_FINISH = 2;

	private MainHandler mHandler;

	/** 接收相机纹理 glGenTexture生成 */
	private int mTextureId;
	/** 接收相机纹理 glGenTexture生成的纹理转化为SurfaceTexture 将其传递给相机作为相机绘制的纹理 */
    public SurfaceTexture mCameraTexture;

    /** 封装了顶点和片元的坐标 以及opengl program */
    private FullFrameRect mInternalTexDrawer;

    /** 外部画布传入的surface 作为egl环境的窗口 切换egl环境 会渲染到不同的窗口 */
	private Surface mSurface;
	private boolean mSurfaceReady ;

    /** 相机矩阵 由当前相机纹理生成的SurfaceTexture获取 */
    private final float[] mCameraMVPMatrix = new float[16];
    /** 编码视频矩阵 mCameraMVPMatrix X mEncodeTextureMatrix */
    private final float[] mTmp2Matrix = new float[16];
	private final float[] mTmp3Matrix = new float[16];
	/** 处理当前编码图像的 平移 旋转 缩放 */
    private final float[] mEncodeTextureMatrix = new float[16];

    private int mCaptureWidth;
    private int mCaptureHeight;

    private int mDisplayViewWidth;
    private int mDisplayViewHeight;

	private int mFrameRate;

	private int count = 0;

	private EglCore mEglCore;
	/** 非高清编码surface Surface通过MediaCodec的createInputSurface创建 每个surface维护一个egl句柄
	 *  渲染后相当于直接将数据传递给MediaCodec*/
	private WindowSurface mEncoderSurface;
	private WindowSurface mHDEncoderSurface;
	/** 渲染surface 由外部需要渲染的画布传入 */
	private WindowSurface mDisplaySurface;

	private CircularEncoder mEncoder;
	private CircularEncoder mHDEncoder;

	private SurfaceHolder mSurfaceHolder;
	private int mFrameCount = 0;

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

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mSurface = holder.getSurface();
		mSurfaceReady = true;
	}

	@Override
	public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
		mSurfaceReady = false;
		surfaceHolder.addCallback(this);
		mSurface = surfaceHolder.getSurface();
		mSurfaceReady = true;
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mSurfaceReady = false;
		holder.removeCallback(this);
	}

	public class MainHandler extends Handler {

		public MainHandler() {
			super(GLThread.this.getLooper());
		}

        @Override
        public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_FRAME_AVAILABLE:
				frameAvailableInThread();
				break;
			case MSG_FINISH:
				finishInThread();
				break;
			}
        }
	}

	public GLThread(Context context, SurfaceHolder surfaceHolder , int width , int height, int displayViewWidth, int displayViewHeight, int frameRate, CircularEncoder.OnCricularEncoderEventListener listener) {
		super("GLThread");

		mSurfaceHolder = surfaceHolder;
		mSurfaceHolder.addCallback(this);
		mSurface = mSurfaceHolder.getSurface();
		mSurfaceReady = mSurface.isValid();
		mCaptureWidth = width;
		mCaptureHeight = height;
		mDisplayViewWidth = displayViewWidth;
		mDisplayViewHeight = displayViewHeight;
		mFrameRate = frameRate;

		Matrix.setIdentityM(mEncodeTextureMatrix, 0);
		Matrix.translateM(mEncodeTextureMatrix, 0, 0.5f, 0.5f, 0);
		Matrix.scaleM(mEncodeTextureMatrix, 0, -1, -1, 1);
		// 这里判断当前的屏幕横竖屏状态来做发送到编码器的图像数据是否需要旋转处理
		int rotate = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ? 90 : 180 ;
		Matrix.rotateM(mEncodeTextureMatrix, 0, rotate, 0, 0, 1);
		// 左右镜像编码后视频 flip
		Matrix.rotateM(mEncodeTextureMatrix, 0, 180, 0, 1, 0);
		Matrix.translateM(mEncodeTextureMatrix, 0, -0.5f, -0.5f, 0);

		initEncoder(listener);
		//initEncoderSurface();
	}

	private void initEncoder(CircularEncoder.OnCricularEncoderEventListener listener){
		try {
			mEncoder = new CircularEncoder(mCaptureWidth , mCaptureHeight , mFrameRate, MediaFormat.MIMETYPE_VIDEO_AVC,true);
			mEncoder.setOnCricularEncoderEventListener(listener);
		} catch (IOException e) {
			Logger.printErrStackTrace(TAG, e, "initEncoder");
		}
	}

	void initEncoderSurface() {
		if (mEglCore != null) {
			if (mEncoder != null) {
				//getInputSurface()最终获取的是MediaCodec调用createInputSurface()方法创建的Surface
				//这个Surface传入当前egl环境 作为egl的窗口参数(win) 通过eglCreateWindowSurface与egldisplay进行关联
				mEncoderSurface = new WindowSurface(mEglCore, mEncoder.getInputSurface(), true);
			}
			if (mHDEncoder != null) {
				mHDEncoderSurface = new WindowSurface(mEglCore, mHDEncoder.getInputSurface(), true);
			}
		}
	}

	@Override
	public synchronized void start() {
		super.start();
		mHandler = new MainHandler();
	}

	@Override
	public void run() {
		initGL();
		initEncoderSurface();
		super.run();

		if (mDisplaySurface != null) {
			Logger.e(TAG , "[GLThread]mDisplaySurface release");
			mDisplaySurface.release();
			mDisplaySurface = null;
		}

		if (mEglCore != null) {
			Logger.e(TAG , "[GLThread]mEglCore release");
			mEglCore.release();
			mEglCore = null;
		}
	}

	private void initGL() {

		//创建egl环境
		mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
		//封装egl与对应的surface
		mDisplaySurface = new WindowSurface(mEglCore,mSurface , false);
		mDisplaySurface.makeCurrent();

		//drawer封装opengl program相关
        mInternalTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
		//mInternalTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_BW));
		//mInternalTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT_FILT));
		//绑定一个纹理 根据TEXTURE_EXT 内部绑定一个相机纹理
        mTextureId = mInternalTexDrawer.createTextureObject();
        //使用纹理创建SurfaceTexture 用来接收相机数据
        mCameraTexture = new SurfaceTexture(mTextureId);
        //监听接收数据
        mCameraTexture.setOnFrameAvailableListener(this);
	}

	/**
	 * 获取本地opengl纹理包装后的SurfaceTexture 将传递给相机绘制
	 * @return
	 */
	public SurfaceTexture getCameraTexture(){
		return mCameraTexture;
	}

	@Override
	public void onFrameAvailable(SurfaceTexture surfaceTexture) {

		frameAvailable();
	}

	private void frameAvailable() {
		if (isAlive() && mSurfaceReady) {
			try {
				mHandler.sendEmptyMessage(MSG_FRAME_AVAILABLE);
			} catch (Exception e) {
				Logger.printErrStackTrace(TAG , e , "getException on frameAvailable");
			}

		}
	}

	/** 丢帧计数器 */
	private int mFrameSkipCnt = 0;

	private void frameAvailableInThread() {
        //Log.d(TAG, "drawFrame");
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }

        /********* draw to Capture Window **********/
        // Latch the next frame from the camera.
		mDisplaySurface.makeCurrent();

		//用于接收相机预览纹理id的SurfaceTexture
		//updateTexImage()方法在OpenGLES环境调用 将数据绑定给OpenGLES对应的纹理对象GL_OES_EGL_image_external 对应shader中samplerExternalOES
		//updateTexImage 完毕后接收下一帧
		//由于在OpenGL ES中，上传纹理（glTexImage2D(), glSubTexImage2D()）是一个极为耗时的过程，在1080×1920的屏幕尺寸下传一张全屏的texture需要20～60ms。这样的话SurfaceFlinger就不可能在60fps下运行。
		//因此， Android采用了image native buffer,将graphic buffer直接作为纹理（direct texture）进行操作

        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(mCameraMVPMatrix);
        // Fill the SurfaceView with it.
		//GLES20.glViewport(0, 0, mCaptureWidth, mCaptureHeight);
		GLES20.glViewport(0, 0, mDisplayViewWidth, mDisplayViewHeight);

		//通过修改顶点坐标 将采集到的视频按比例缩放到窗口中
		ScaleUtils.Param param = ScaleUtils.getScale(mDisplayViewWidth, mDisplayViewHeight, mCaptureWidth, mCaptureHeight);
		float[] drawMatrix = new float[8];
		float scaleWidth = ((float) param.width)/mDisplayViewWidth;
		float scaleHeight = ((float) param.height)/mDisplayViewHeight;
		if(scaleWidth == 1){
			float halfHeight = scaleHeight;
			drawMatrix[0] = -1;
			drawMatrix[1] = -halfHeight;
			drawMatrix[2] = 1;
			drawMatrix[3] = -halfHeight;
			drawMatrix[4] = -1;
			drawMatrix[5] = halfHeight;
			drawMatrix[6] = 1;
			drawMatrix[7] = halfHeight;
		}else{
			float halfWidth = scaleWidth;
			drawMatrix[0] = -halfWidth;
			drawMatrix[1] = -1;
			drawMatrix[2] = halfWidth;
			drawMatrix[3] = -1;
			drawMatrix[4] = -halfWidth;
			drawMatrix[5] = 1;
			drawMatrix[6] = halfWidth;
			drawMatrix[7] = 1;
		}
		mInternalTexDrawer.rescaleDrawRect(drawMatrix);
		mInternalTexDrawer.drawFrame(mTextureId, mCameraMVPMatrix);

		mDisplaySurface.swapBuffers();

		//Logger.i("lidechen_test", "test1 scaleWidth="+scaleWidth+" scaleHeight="+scaleHeight);
		//mDisplaySurface.readImageTest();

		mFrameCount ++;

		//丢帧
//		mFrameSkipCnt++;
//		if(mFrameSkipCnt % 2 == 0){
//			return;
//		}

		/********* draw to HD encoder **********/
		if(mHDEncoder != null) {
			if(mFrameCount == 1 /*|| mFrameCount%10 == 0*/) {
				mHDEncoder.requestKeyFrame();
			}

			mHDEncoderSurface.makeCurrent();
			// 给编码器显示的区域
			GLES20.glViewport(0, 0, mHDEncoder.getWidth() , mHDEncoder.getHeight());
			// 如果是横屏 不需要设置
			Matrix.multiplyMM(mTmp2Matrix, 0, mCameraMVPMatrix, 0, mEncodeTextureMatrix, 0);
			// 恢复为基本scale
			mInternalTexDrawer.rescaleDrawRect(mBaseScaleVertexBuf);
			// 下面往编码器绘制数据
			mInternalTexDrawer.drawFrame(mTextureId, mTmp2Matrix);
			mHDEncoder.frameAvailableSoon();
			mHDEncoderSurface.setPresentationTime(mCameraTexture.getTimestamp());
			mHDEncoderSurface.swapBuffers();

			Logger.i("lidechen_test", "test2");
			//mHDEncoderSurface.readImageTest();
		}

		/********* draw to mix encoder **********/
		if(mEncoder != null) {
			if(mFrameCount == 1 /*|| mFrameCount%10 == 0*/) {
				mEncoder.requestKeyFrame();
			}

			// 切到当前egl环境
			mEncoderSurface.makeCurrent();
			// 给编码器显示的区域
			GLES20.glViewport(0, 0, mEncoder.getWidth() , mEncoder.getHeight());
			// 如果是横屏 不需要设置
			Matrix.multiplyMM(mTmp3Matrix, 0, mCameraMVPMatrix, 0, mEncodeTextureMatrix, 0);
			// 恢复为基本scale
			mInternalTexDrawer.rescaleDrawRect(mBaseScaleVertexBuf);
			// 下面往编码器绘制数据  mEncoderSurface中维护的egl环境中的win就是 mEncoder中MediaCodec中的surface
			// 也就是说这一步其实是往编码器MediaCodec中放入了数据
			mInternalTexDrawer.drawFrame(mTextureId, mTmp3Matrix);
			//通知从MediaCodec中读取编码完毕的数据
			mEncoder.frameAvailableSoon();
			mEncoderSurface.setPresentationTime(mCameraTexture.getTimestamp());
			mEncoderSurface.swapBuffers();

//			File file = new File("/sdcard/test.png");
//			try {
//				mEncoderSurface.saveFrame(file);
//			} catch (IOException e) {
//				e.printStackTrace();
//			}

			Logger.i("lidechen_test", "test3");
			//mEncoderSurface.readImageTest();
		}

	}

	public void setSize(int width , int height) {
		mCaptureWidth = width;
		mCaptureHeight = height;
	}


    public void finish() {
		Logger.v(TAG , "finish");
    	mHandler.sendEmptyMessage(MSG_FINISH);
    	try {
			join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }

    private void finishInThread() {
		Logger.v(TAG , "finishInThread");
        if (mCameraTexture != null) {
            mCameraTexture.release();
            mCameraTexture = null;
        }
    	if (mInternalTexDrawer != null) {
    		mInternalTexDrawer.release(true);
    		mInternalTexDrawer = null;
    	}
    	quit();
    }

    public void release(){

		this.finish();

		if (mHDEncoder != null) {
			mHDEncoder.shutdown();
			mHDEncoder = null;
		}

		if(mEncoder != null){
			mEncoder.shutdown();
			mEncoder = null;
		}

		if (mHDEncoderSurface != null) {
			mHDEncoderSurface.release();
			mHDEncoderSurface = null;
		}

		if (mEncoderSurface != null) {
			mEncoderSurface.release();
			mEncoderSurface = null;
		}

		if (mDisplaySurface != null) {
			Logger.d(TAG , "mDisplaySurface release");
			mDisplaySurface.release();
			mDisplaySurface = null;
		}

		if (mEglCore != null) {
			Logger.d(TAG , "mEglCore release");
			mEglCore.release();
			mEglCore = null;
		}

		if(mSurfaceHolder != null){
			Logger.d(TAG , "mSurfaceHolder removecallback");
			mSurfaceHolder.removeCallback(this);
		}

		//this.finish();
	}
}

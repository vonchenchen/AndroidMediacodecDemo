
[TOC]

## Android MediaCodec测试demo

Android MeidaCodec编解码与渲染测试demo

用于测试MediaCodec相关功能，主要涉及调用Android MediaCodec进行编解码，以及渲染相关流程，渲染部分主要参考了Grafik，目前主要在rk3288平台验证。

### 1.目前测试demo提供了以下功能:
#### 1.1.H264编码以及保存视频
利用Surface和Opengl直接将oes纹理传入MediaCodec进行操作，避免频繁拷贝数据。
#### 1.2.H264解码渲染
Opengl绘制相机图片。
#### 1.3.VP8解码渲染
工程根目录下out.vp8是一段使用libvpx中demo编码的vp8视频，ivf封装，可以使用IVFDataReader读取。
#### 1.4.H264码率控制模式设置
可以测试当前编码器设置vbr，cbr是否有效。
#### 1.5.H264码率设置（可以动态设置）
此处需要注意给编码器设置的初始帧率必须接近真实帧率，否则码率设置和实际编码结果差距可能较大。
#### 1.6.H264帧率设置（可以动态设置）
目前默认为最大帧率，如果通过丢帧在一定范围内实现帧率控制。
#### 1.7.H264 IDR间隔设置
#### 1.8.MediaCodec解码后通过Opengl渲染视频
通过纹理传递MediaCodec解码结果，通过OpenGL控制最终渲染。
#### 1.9.应用推后台测试
这里主要是需要监听Surface状态，通过一个消息队列控制是否需要重新初始化渲染，编解码使用的surface是通过纹理创建的，所以推后台不会影响编码和解码，只是停止渲染
#### 1.10.相机分辨率选择

### 2.视频采集编码及其渲染流程

#### 2.1初始化环境

视频编码采用了自建SurfaceTexure的方式，直接使用自建纹理填入相机，主要实现流程在EncodeTask中。构造函数中传入相机需要渲染的SurfaceView，并监听其中SurfaceHolder的相应事件。这里还自建了一个MsgPipe，内部会开启一个线程，用于处理编码和渲染中的相关状态，包括资源的销毁和重新初始化，这里之所以开启线程还有一个考虑就是给Opengl提供线程。

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
		...
	}

初始化完毕后，会开启MsgPipe线程，等待相应消息。下面是消息对应的注释

	    /** 编码采集帧成功 */
        MSG_ENCODE_CAPTURE_FRAME_READY,
        /** 编码恢复渲染 */
        MSG_ENCODE_RESUME_RENDER,
        /** 编码暂停渲染 */
        MSG_ENCODE_PAUSE_RENDER,
        /** 结束编码任务 */
        MSG_ENCODE_STOP_TASK,
        /** 改变编码器参数 */
        MSG_ENCODE_CHANGE_BITRATE,
        /** 改变编码帧率 */
        MSG_ENCODE_CHANGE_FRAMERATE;

根据外部给MsgPipe发送的消息，下面会逐条处理。

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
				}else if(msg.currentMsg == CodecMsg.MSG.MSG_ENCODE_CHANGE_FRAMERATE){
					//改变编码帧率
					resetEncodeFramerate(msg);
				}
			}

首先看一下initGL()函数，用来初始化当前线程的Opengl环境以及编码器相关参数，其中Egl和Opengl相关使用了Grafika中相关代码，目前为了保持视频比例增加了黑边填充相关绘制。下面简要描述一函数调用流程和相关变量的意义。

	private void initGL() {

		if(mEglCore == null) {
			//创建egl环境
			mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
		}
		...
		//封装egl与对应的surface
		mRenderWindowSurface = new WindowSurface(mEglCore, mRenderSurface, false);
		mRenderWindowSurface.makeCurrent();

		if(mInternalTexDrawer == null) {
			//drawer封装opengl program相关
			mInternalTexDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
			//绑定一个纹理 根据TEXTURE_EXT 内部绑定一个相机纹理
			mTextureId = mInternalTexDrawer.createTextureObject();
			//使用纹理创建SurfaceTexture 用来接收相机数据
			mCameraTexture = new SurfaceTexture(mTextureId);
			...
			mSimpleEncoder = new SimpleEncoder(mStreamWidth, mStreamHeight, mInitFrameRate, MediaFormat.MIMETYPE_VIDEO_AVC, true, mEncodeInfo);
			...
		}
	}

EglCore中封装了Egl相关，主要用于创建和存储Opengl上下文，这里只需要创建一次，推后台等情况也不需要销毁。WindowSurface封装了Egl与android中Surface绑定相关的操作，调用makeCurrent就可以把当前surface挂载到当前Egl环境，swapBuffers则将当前缓存中的内容交换到显示中，如果推后台导致surface失效，则需要重新创建。FullFrameRect中封装了Opengl的program相关，由其创建纹理，其在当前Egl环境下绘制纹理则会被显示到Egl对应的窗口中。

#### 2.2编码与渲染

前文在初始化的时候，创建了一个纹理，这个纹理被包装为Android的SurfaceTexture，创建完毕后将被设置给Camera作为Camera渲染的画布。

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

当Camera填充完毕后，会触发一次onFrameAvailable，这时已经采集到了帧，我们发送一条消息给消息队列。这里首先调用updateTexImage方法读出图片，读完后相机将开始下一次采集。这样就可以在当前Egl环境下使用Opengl绘制前文创建的纹理，这个纹理的内容就是刚才相机画上去的东西。

		mCameraTexture.updateTexImage();

        /********* draw to Capture Window **********/
        // Latch the next frame from the camera.
		if(mRenderWindowSurface != null) {
			mRenderWindowSurface.makeCurrent();

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
绘制完毕后，将接着使用这个纹理Id，将其绘制到编码器的画布上。MediaCodec作为编码器可以调用createInputSurface创建一个Surface，作为一块输入缓冲区。

		mHDEncodeWindowSurface = new WindowSurface(mEglCore, mHDEncoder.getInputSurface(), true);
		....

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


### 3.视频解码及其渲染流程

#### 3.1初始化
解码类似编码，也做了一个消息队列，用来管理线程状态，避免推后台影响渲染。
与编码不同，MediaCodec作为解码器，并没有提供输入surface，这里需要手动创建一个sueface，用作decode的缓冲区。这里利用FullFrameRect创建一个program并创建一个纹理Id，用这纹理Id创建一个SurfaceTexture，最终使用SurfaceTexture创建一个Surface，将这个Surface设置给MediaCodec作为解码缓存区。mRendererWindowSurface则是外部需要被渲染的Surface的封装，首先将这个surface挂到Egl环境上，用来初始化当前Egl环境。
		
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
        }
		....
        }

#### 3.2解码与渲染

当视频了流抛出时，首先利用上文创建的MediaCodec解码，解码完毕后给消息队列发送一条消息。由于上文已经将包装有自建纹理的Surface传入MediaCodec，这里解码后会直接将数据发送到Surface上。调用updateTexImage方法可以将当前数据读出，这样就可以按照Opengl绘制流程渲染解码出来的图片。
	
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

### 4.其他问题

#### 4.1关于视频流分辨率变化导致MeidaCodecc崩溃
在实际项目使用中，RK3288平台下，当对方H264视频分辨率变化后，RK3288经常会发生Vpu相关崩溃的问题。目前看报错应该是RK自己的vpu库产生的崩溃，这部分代码目前没有开源。目前解决方案是在抛出码流时，先手动解析H264视频的SPS，从中拿出视频宽高并与本地记录的初始视频宽高做比对，如果变化则给解码器发送一条重置消息，将MediaCodec相关变量全部release后重新创建，这样基本解决了视频分辨率变化的崩溃问题。



/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vonchenchen.mediacodecdemo.video;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Encodes video in a fixed-size circular buffer.
 * <p>
 * The obvious way to do this would be to store each packet in its own buffer and hook it
 * into a linked list.  The trouble with this approach is that it requires constant
 * allocation, which means we'll be driving the GC to distraction as the frame rate and
 * bit rate increase.  Instead we create fixed-size pools for video data and metadata,
 * which requires a bit more work for us but avoids allocations in the steady state.
 * <p>
 * Video must always start with a sync frame (a/k/a key frame, a/k/a I-frame).  When the
 * circular buffer wraps around, we either need to delete all of the data between the frame at
 * the head of the list and the next sync frame, or have the file save function know that
 * it needs to scan forward for a sync frame before it can start saving data.
 * <p>
 * When we're told to save a snapshot, we create a MediaMuxer, write all the frames out,
 * and then go back to what we were doing.
 */
public class CircularEncoder {
    private static final String TAG = "CircularEncoder";
    private static final boolean VERBOSE = false;

    private static String VIDEO_MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int I_FRAME_INTERVAL = 1;           // sync frame every second

    private VideoEncoderThread mVideoEncoderThread;
    private Surface mInputSurface;
    private MediaCodec mVideoEncoder;
    private int mVideoWidth, mVideoHeight;
    private int mFrameRate;
    private boolean mHDBuffer ;

    private long mRecTs;
    private int mFrameCount = 0;

    /**
     * Configures encoder, and prepares the input Surface.
     *
     * @param width Width of encoded video, in pixels.  Should be a multiple of 16.
     * @param height Height of encoded video, in pixels.  Usually a multiple of 16 (1080 is ok).
     * @param frameRate Expected frame rate.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public CircularEncoder(int width, int height, int frameRate)
    		throws IOException {
        this(width , height , frameRate , VIDEO_MIME_TYPE, true);

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public CircularEncoder(int width, int height, int frameRate , String mimeType, boolean hd)
            throws IOException {
        mHDBuffer = hd;
        // The goal is to size the buffer so that we can accumulate N seconds worth of video,
        // where N is passed in as "desiredSpanSec".  If the codec generates data at roughly
        // the requested bit rate, we can compute it as time * bitRate / bitsPerByte.
        //
        // Sync frames will appear every (frameRate * IFRAME_INTERVAL) frames.  If the frame
        // rate is higher or lower than expected, various calculations may not work out right.
        //
        // Since we have to start muxing from a sync frame, we want to ensure that there's
        // room for at least one full GOP in the buffer, preferrably two.
//        if (preRecordSec < IFRAME_INTERVAL * 2) {
//            throw new RuntimeException("Requested time span is too short: " + preRecordSec + " vs. " + (IFRAME_INTERVAL * 2));
//        }
        mVideoWidth = width;
        mVideoHeight = height;
        mFrameRate = frameRate;

        mVideoEncoder = createVideoEncoder();
        mInputSurface = mVideoEncoder.createInputSurface();
        mVideoEncoder.start();

        // Start the encoder thread last. That way we're sure it can see all of the state we've initialized.
        mVideoEncoderThread = new VideoEncoderThread(mVideoEncoder , width + "_" + height);
        mVideoEncoderThread.start();

        mVideoEncoderThread.waitUntilReady();
    }

    public int  getWidth() {
        return mVideoWidth;
    }

    public int getHeight() {
        return mVideoHeight;
    }


    public int getBitrate() {
        if(mVideoWidth * mVideoHeight == 1280 * 720) {
            return 1000 * 1024;
        }

        if(mVideoWidth * mVideoHeight == 1920 * 1080) {
            return 2000 * 1024;
        }

        if(mVideoWidth * mVideoHeight == 3840 * 2160) {
            return 4000 * 1024;
        }

        return 400 * 1024;
    }

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private MediaCodec createVideoEncoder() {
		MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mVideoWidth, mVideoHeight);

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, getBitrate());
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mVideoWidth * mVideoHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger("bitrate-mode" , 2);
        format.setInteger(MediaFormat.KEY_PROFILE , MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        format.setInteger("level" , MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        MediaCodec videoEncoder = null;
        try {
            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            videoEncoder.reset();
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return videoEncoder;
	}

    /**
     * Returns the encoder's input surface.
     */
    Surface getInputSurface() {
        return mInputSurface;
    }


    /**
     * Shuts down the encoder thread, and releases encoder resources.
     * <p>
     * Does not return until the encoder thread has stopped.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    void shutdown() {
        if (VERBOSE) Logger.d(TAG, "releasing encoder objects");


        if (mVideoEncoderThread.getId() != Thread.currentThread().getId()) {
	        Handler handler = mVideoEncoderThread.getHandler();
	        handler.sendEmptyMessage(VideoEncoderHandler.MSG_SHUTDOWN);
	        try {
	        	mVideoEncoderThread.join();
	        } catch (Exception ex) { }
        } else {
            try {
                mVideoEncoderThread.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (mInputSurface != null) {
        	mInputSurface.release();
        	mInputSurface = null;
        }

        if (mVideoEncoder != null) {
            try {
                mVideoEncoder.stop();
                mVideoEncoder.reset();
                mVideoEncoder.release();
                mVideoEncoder = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Notifies the encoder thread that a new frame will shortly be provided to the encoder.
     * <p>
     * There may or may not yet be data available from the encoder output.  The encoder
     * has a fair mount of latency due to processing, and it may want to accumulate a
     * few additional buffers before producing output.  We just need to drain it regularly
     * to avoid a situation where the producer gets wedged up because there's no room for
     * additional frames.
     * <p>
     * If the caller sends the frame and then notifies us, it could get wedged up.  If it
     * notifies us first and then sends the frame, we guarantee that the output buffers
     * were emptied, and it will be impossible for a single additional frame to block
     * indefinitely.
     */
    void frameAvailableSoon() {
        Handler handler = mVideoEncoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(VideoEncoderHandler.MSG_FRAME_AVAILABLE_SOON));
    }


    void requestKeyFrame() {
        Handler handler = mVideoEncoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(VideoEncoderHandler.MSG_REQUEST_I_FRAME));
    }



	/**
     * Object that encapsulates the encoder thread.
     * <p>
     * We want to sleep until there's work to do.  We don't actually know when a new frame
     * arrives at the encoder, because the other thread is sending frames directly to the
     * input surface.  We will see data appear at the decoder output, so we can either use
     * an infinite timeout on dequeueOutputBuffer() or wait() on an object and require the
     * calling app wake us.  It's very useful to have all of the buffer management local to
     * this thread -- avoids synchronization -- so we want to do the file muxing in here.
     * So, it's best to sleep on an object and do something appropriate when awakened.
     * <p>
     * This class does not manage the MediaCodec encoder startup/shutdown.  The encoder
     * should be fully started before the thread is created, and not shut down until this
     * thread has been joined.
     */
    private class VideoEncoderThread extends Thread {
        MediaFormat mEncodedFormat;
        private MediaCodec.BufferInfo mBufferInfo;

        private VideoEncoderHandler mHandler;
        private int mCount = 0;
        private final Object mLock = new Object();
        private volatile boolean mReady = false;
        private byte[] configbyte ;
        private static final int HEADER_LENGTH = 4;

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        VideoEncoderThread(MediaCodec mediaCodec, String name) {
            mVideoEncoder = mediaCodec;
            mBufferInfo = new MediaCodec.BufferInfo();
            setName("VideoEncoderThread-" + name);
            //createFile();
        }

        /**
         * Thread entry point.
         * Prepares the Looper, Handler, and signals anybody watching that we're ready to go.
         */
        @Override
        public void run() {
            Looper.prepare();
            mHandler = new VideoEncoderHandler(this);    // must create on encoder thread
            //Logger.d(TAG, "video encoder thread ready");
            synchronized (mLock) {
                mReady = true;
                mLock.notify();    // signal waitUntilReady()
            }

            Looper.loop();

            synchronized (mLock) {
                mReady = false;
                mHandler = null;
            }
            //Logger.d(TAG, "video looper quit");
        }


        /**
         * Waits until the encoder thread is ready to receive messages.
         * <p>
         * Call from non-encoder thread.
         */
        void waitUntilReady() {
            synchronized (mLock) {
                while (!mReady) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        /**
         * Returns the Handler used to send messages to the encoder thread.
         */
        public VideoEncoderHandler getHandler() {
            synchronized (mLock) {
                // Confirm ready state.
                if (!mReady) {
                    throw new RuntimeException("not ready");
                }
            }
            return mHandler;
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        void cirEncoderFlush() {
            if (mVideoEncoder != null) {
                // Ideally MediaCodec would honor BUFFER_FLAG_SYNC_FRAME so we could
                // indicate this in queueInputBuffer() below and guarantee _this_ frame
                // be encoded as a key frame, but sadly that flag is ignored.  Instead,
                // we request a key frame "soon".
                Logger.d(TAG, "Sync frame request");
                Bundle b = new Bundle();
                b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                mVideoEncoder.setParameters(b);
            }
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        void drainVideoEncoder() {
            final int TIMEOUT_USEC = 0;     // no timeout -- check for buffers, bail if none
            //mVideoEncoder.flush();
            ByteBuffer[] encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
            byte[] outData;

            Logger.d(TAG, "drainVideoEncoder");

            while (true) {
                int encoderStatus = mVideoEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    Logger.d(TAG, "drainVideoEncoder INFO_TRY_AGAIN_LATER");
                    break;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    Logger.d(TAG, "drainVideoEncoder INFO_OUTPUT_BUFFERS_CHANGED");
                    encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Should happen before receiving buffers, and should only happen once.
                    // The MediaFormat contains the csd-0 and csd-1 keys, which we'll need
                    // for MediaMuxer.  It's unclear what else MediaMuxer might want, so
                    // rather than extract the codec-specific data and reconstruct a new
                    // MediaFormat later, we just grab it here and keep it around.
                    mEncodedFormat = mVideoEncoder.getOutputFormat();
                    Logger.d(TAG, "drainVideoEncoder INFO_OUTPUT_FORMAT_CHANGED "+mEncodedFormat);
                    //Logger.d(TAG, "encoder output format changed: " + mEncodedFormat);
                } else if (encoderStatus < 0) {
                    Logger.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                    // let's ignore it
                } else {

                    Logger.d(TAG, "drainVideoEncoder mBufferInfo size: "+mBufferInfo.size+" offset: "+mBufferInfo.offset+" pts: "+mBufferInfo.presentationTimeUs);

                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                    }
                    long pts = computePresentationTime(mCount);
                    mCount += 1;
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    outData = new byte[mBufferInfo.size];
                    encodedData.get(outData);

                    if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {

                        //SPS PPS
                        configbyte = new byte[outData.length];
                        System.arraycopy(outData, 0, configbyte, 0, configbyte.length);
                        if (VERBOSE){
                            Logger.v(TAG , "OnEncodedData BUFFER_FLAG_CODEC_CONFIG " + configbyte.length);
                        }

                        Logger.d(TAG, "drainVideoEncoder CODEC_CONFIG: "+ toString(outData));

                        if(mOnCricularEncoderEventListener != null){
                            mOnCricularEncoderEventListener.onConfigFrameReceive(outData, mBufferInfo.size);
                        }

                    } else if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_SYNC_FRAME) {

                        byte[] keyframe = new byte[mBufferInfo.size + configbyte.length];
                        System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                        System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);

                        if (VERBOSE) {
                            Logger.v(TAG , "OnEncodedData BUFFER_FLAG_SYNC_FRAME " + keyframe.length);
                        }

                        Logger.d(TAG, "drainVideoEncoder CODEC_SYNC_FRAME: "+ toString(keyframe));

                        if(mOnCricularEncoderEventListener != null){
                            mOnCricularEncoderEventListener.onKeyFrameReceive(keyframe, keyframe.length);
                        }

                        mFrameCount ++;
                        long curr = System.currentTimeMillis();
                        if(curr - mRecTs > 1000){
                            if(mOnCricularEncoderEventListener != null){
                                mOnCricularEncoderEventListener.onFrameRateReceive(mFrameCount);
                            }
                            mRecTs = curr;
                            mFrameCount = 0;
                        }

                    } else {

                        byte[] pFrame = new byte[outData.length - HEADER_LENGTH];
                        System.arraycopy(outData, HEADER_LENGTH, pFrame, 0, pFrame.length);

                        if (VERBOSE) {
                            Logger.v(TAG , "OnEncodedData pFrame " + pFrame.length);
                        }

                        Logger.d(TAG, "drainVideoEncoder P_FRAME: "+ toString(outData));

                        if(mOnCricularEncoderEventListener != null){
                            mOnCricularEncoderEventListener.onOtherFrameReceive(outData, mBufferInfo.size);
                        }

                        mFrameCount ++;
                        long curr = System.currentTimeMillis();
                        if(curr - mRecTs > 1000){
                            if(mOnCricularEncoderEventListener != null){
                                mOnCricularEncoderEventListener.onFrameRateReceive(mFrameCount);
                            }
                            mRecTs = curr;
                            mFrameCount = 0;
                        }
                    }

                    mVideoEncoder.releaseOutputBuffer(encoderStatus, false);
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
        }

        String toString(byte[] a) {
            if (a == null) {
                return "null";
            }
            int iMax = a.length - 1;
            if (iMax == -1) {
                return "[]";
            }

            StringBuilder b = new StringBuilder();
            b.append('[');
            for (int i = 0; ; i++) {
                b.append(Integer.toHexString(0x000000ff & a[i]));
                if (i == iMax) {
                    return b.append(']').toString();
                }
                b.append(", ");
            }
        }

        /**
         * Generates the presentation time for frame N, in microseconds.
         */
        private long computePresentationTime(long frameIndex) {
            return 132 + frameIndex * 1000000 / 30;
        }


        /**
         * Drains the encoder output.
         * <p>
         * See notes for {@link CircularEncoder#frameAvailableSoon()}.
         */
        void frameAvailableSoon() {
            if (VERBOSE) Logger.d(TAG, "frameAvailableSoon");
            try{
                drainVideoEncoder();
            } catch (Exception e) {
                Logger.printErrStackTrace(TAG , e , "get Exception on frameAvailableSoon");
            }
        }

        /**
         * Tells the Looper to quit.
         */
        void shutdown() {

            if (VERBOSE) Logger.d(TAG, "shutdown");
            Looper looper = Looper.myLooper();
            if(looper == null) {
                return ;
            }
            looper.quit();
        }

        private String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test2.h264";
        private BufferedOutputStream outputStream;

        private void createFile() {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
            try {
                outputStream = new BufferedOutputStream(new FileOutputStream(file));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Handler for EncoderThread.  Used for messages sent from the UI thread (or whatever
     * is driving the encoder) to the encoder thread.
     * <p>
     * The object is created on the encoder thread.
     */
    private static class VideoEncoderHandler extends Handler {
        static final int MSG_FRAME_AVAILABLE_SOON = 1;
        static final int MSG_REQUEST_I_FRAME = 2;
        static final int MSG_SHUTDOWN = 3;

        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private WeakReference<VideoEncoderThread> mWeakEncoderThread;

        /**
         * Constructor.  Instantiate object from encoder thread.
         */
        VideoEncoderHandler(VideoEncoderThread et) {
            mWeakEncoderThread = new WeakReference<>(et);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message msg) {
            int what = msg.what;
            if (VERBOSE) {
                Logger.v(TAG, "EncoderHandler: what=" + what);
            }

            VideoEncoderThread encoderThread = mWeakEncoderThread.get();
            if (encoderThread == null) {
                Logger.w(TAG, "EncoderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_FRAME_AVAILABLE_SOON:
                    encoderThread.frameAvailableSoon();
                    break;
                case MSG_REQUEST_I_FRAME:
                    encoderThread.cirEncoderFlush();
                    break;
                case MSG_SHUTDOWN:
                    encoderThread.shutdown();
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }

    private OnCricularEncoderEventListener mOnCricularEncoderEventListener;

    public void setOnCricularEncoderEventListener(OnCricularEncoderEventListener onCricularEncoderEventListener){
        mOnCricularEncoderEventListener = onCricularEncoderEventListener;
    }

    public interface OnCricularEncoderEventListener{
        void onConfigFrameReceive(byte[] data, int length);
        void onKeyFrameReceive(byte[] data, int length);
        void onOtherFrameReceive(byte[] data, int length);
        void onFrameRateReceive(int frameRate);
    }
}

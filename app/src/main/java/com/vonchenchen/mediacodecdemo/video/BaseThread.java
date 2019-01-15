package com.vonchenchen.mediacodecdemo.video;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

public abstract class BaseThread extends HandlerThread {

    static private final String TAG = "BaseThread";
    static public final int MSG_FRAME_AVAILABLE = 1;
    static public final int MSG_FINISH = 2;

    private MainHandler mHandler;

    public BaseThread(String name) {
        super(name);
    }

    @Override
    public synchronized void start() {
        super.start();
        mHandler = new MainHandler();
    }

    protected abstract void frameAvailableInThread();

    protected abstract void finishThread();

    public class MainHandler extends Handler {

        public MainHandler() {
            super(BaseThread.this.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_FRAME_AVAILABLE:
                    frameAvailableInThread();
                    break;
                case MSG_FINISH:
                    finishThread();
                    break;
            }
        }
    }
}

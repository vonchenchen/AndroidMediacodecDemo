package com.vonchenchen.mediacodecdemo.video;

import java.util.LinkedList;

public class MsgPipe<T> extends Thread{

    private static final String TAG = "MsgPipe";

    private LinkedList<T> mMsgQueue = new LinkedList();
    private OnPipeListener mOnPipeListener = null;

    private volatile boolean mIsLoop;

    @Override
    public void run() {

        T msg = null;

        if (mOnPipeListener != null) {
            mOnPipeListener.onPipeStart();
        }

        Logger.i(TAG, "MsgPipe start id="+Thread.currentThread().getId());

        while (mIsLoop){
            if(mMsgQueue.isEmpty()){
                synchronized (mMsgQueue){
                    try {
                        mMsgQueue.wait(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }else {
                synchronized (mMsgQueue) {
                    msg = mMsgQueue.removeFirst();
                }
                if (mOnPipeListener != null) {
                    mOnPipeListener.onPipeRecv(msg);
                }
            }
        }

        if (mOnPipeListener != null) {
            mOnPipeListener.onPipeRelease();
        }

        Logger.i(TAG, "MsgPipe stop id="+Thread.currentThread().getId());
    }

    public void addLast(T msg){
        synchronized (mMsgQueue) {
            mMsgQueue.addLast(msg);
            mMsgQueue.notify();
        }
    }

    public void addFirst(T msg){
        synchronized (mMsgQueue) {
            mMsgQueue.addFirst(msg);
            mMsgQueue.notify();
        }
    }

    public void startPipe(){
        mIsLoop = true;
        start();
    }

    public void stopPipe(){
        mIsLoop = false;
    }

    public void clearPipeData(){
        if(mMsgQueue != null){
            synchronized (mMsgQueue) {
                mMsgQueue.clear();
            }
        }
    }

    public void setOnPipeListener(OnPipeListener listener){
        mOnPipeListener = listener;
    }

    public interface OnPipeListener<T>{
        void onPipeStart();
        void onPipeRecv(T msg);
        void onPipeRelease();
    }
}

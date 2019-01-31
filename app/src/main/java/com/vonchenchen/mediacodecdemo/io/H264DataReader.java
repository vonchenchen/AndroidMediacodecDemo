package com.vonchenchen.mediacodecdemo.io;

import com.vonchenchen.mediacodecdemo.video.Logger;

import java.io.FileNotFoundException;

public class H264DataReader extends MediaDataReader implements IStreamDataReader {

    private static String TAG = "H264DataReader";

    private byte[] mData;
    private int mDataRearIndex;

    public H264DataReader(String path, int maxFrameSize) throws FileNotFoundException {
        super(path);
        mData = new byte[maxFrameSize];
        mDataRearIndex = 0;
    }

    @Override
    public int readNextFrame(){
        Logger.i(TAG, "[readNextFrame] mDataRearIndex="+mDataRearIndex+" length ="+(mData.length - mDataRearIndex));
        int length = read(mData, mDataRearIndex, mData.length - mDataRearIndex);
        if(length <= 0){
            return length;
        }
        mDataRearIndex = length;
        for(int i=0; i<mDataRearIndex; ){
            if(isH264Header(mData, i)) {
                if(i != 0){
                    System.arraycopy(mData, i, mData, 0, length-i);
                }
                boolean isFind = false;
                for(int j=i+1; j<mDataRearIndex; j++){
                    if(isH264Header(mData, j)){

                        //卡顿可能是idr分开读取引起
                        int dataLen = j-i;
                        if(mOnDataParsedListener != null){
                            mOnDataParsedListener.onParsed(mData, i, dataLen);
                        }
                        System.arraycopy(mData, dataLen, mData, 0, mDataRearIndex-dataLen);
                        mDataRearIndex -= dataLen;
                        if(mDataRearIndex <= 0){
                            mDataRearIndex = 0;
                            return length;
                        }
                        isFind = true;
                        i = 0;
                        break;
                    }
                }
                if(!isFind){
                    return length;
                }
            }else {
                i++;
            }
        }
        return length;
    }

    private boolean isH264Header(byte[] data, int index){
        if(mData[index] == 0 && mData[index+1] == 0 && mData[index+2] == 0 && mData[index+3] == 1){
            return true;
        }else {
            return false;
        }
    }

    @Override
    public void setOnDataParsedListener(OnDataParsedListener onOnDataParsedListener){
        mOnDataParsedListener = onOnDataParsedListener;
    }

    private OnDataParsedListener mOnDataParsedListener = null;

    public interface OnH264DataParsedListener{
        void onParsed(byte[] data, int index, int length);
    }
}

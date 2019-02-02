package com.vonchenchen.mediacodecdemo.io;

import com.vonchenchen.mediacodecdemo.video.Logger;

import java.io.FileNotFoundException;

public class H264DataReader extends MediaDataReader implements IStreamDataReader {

    private static String TAG = "H264DataReader";

    private byte[] mData;
    private int mDataRearIndex;
    private int mFrameDataHeaderIndex;
    private int mFrameDataLength = 0;

    public H264DataReader(String path, int maxFrameSize) throws FileNotFoundException {
        super(path);
        mData = new byte[maxFrameSize];
        mDataRearIndex = 0;
    }

//    @Override
//    public int readNextFrame(){
//        Logger.i(TAG, "[readNextFrame] mDataRearIndex="+mDataRearIndex+" length ="+(mData.length - mDataRearIndex));
//        int length = read(mData, mDataRearIndex, mData.length - mDataRearIndex);
//        if(length <= 0){
//            return length;
//        }
//        mDataRearIndex = length;
//        for(int mainIndex=0; mainIndex<mDataRearIndex; ){
//
//            if(isH264Header(mData, mainIndex)) {
//
//                if(mainIndex != 0){
//                    //数组整体前移
//                    System.arraycopy(mData, mainIndex, mData, 0, length-mainIndex);
//                }
//                boolean isFind = false;
//
//                for(int frameRareIndex=mainIndex+1; frameRareIndex<mDataRearIndex; frameRareIndex++){
//                    if(isH264Header(mData, frameRareIndex)){
//
//                        //卡顿可能是idr分开读取引起
//                        int dataLen = frameRareIndex-mainIndex;
//                        if(mOnDataParsedListener != null){
//                            mOnDataParsedListener.onParsed(mData, mainIndex, dataLen);
//                        }
//                        System.arraycopy(mData, dataLen, mData, 0, mDataRearIndex-dataLen);
//                        mDataRearIndex -= dataLen;
//
//                        if(mDataRearIndex <= 0){
//                            mDataRearIndex = 0;
//                            return length;
//                        }
//                        isFind = true;
//                        mainIndex = 0;
//                        break;
//                    }
//                }
//                if(!isFind){
//                    return length;
//                }
//            }else {
//                mainIndex++;
//            }
//        }
//
//        return length;
//    }

    @Override
    public int readNextFrame(){

        int realReadLength = read(mData, mDataRearIndex, mData.length-mDataRearIndex);
        if(realReadLength <= 0){
            //读到文件末尾
            return realReadLength;
        }

        for(int index=0; index<realReadLength; ){

            boolean isFindCompleteFrame = false;
            int frameLength = 1;
            if(isH264Header(mData, index)){

                //发现帧头 开始寻找帧尾
                for(int frameRareIndex=index+1; frameRareIndex<mData.length; frameRareIndex++){
                    if(isH264Header(mData, frameRareIndex)){
                        isFindCompleteFrame = true;
                        break;
                    }else {
                        frameLength++;
                    }
                }

                if(!isFindCompleteFrame){
                    //如果没有找到完整H264帧 将剩余数据移到数组头 开始下一次读取
                    System.arraycopy(mData, index, mData, 0, frameLength);
                    //下次从此处开始赋值
                    mDataRearIndex = frameLength;
                    return mDataRearIndex;
                }

                if(isSps(mData[index+4])){

                    //如果当前帧长度为0 则设置此处为帧起始 下面pps sei也一样 直到解析到视频帧一起抛出这些数据
                    if(mFrameDataLength == 0) {
                        mFrameDataHeaderIndex = index;
                    }
                    mFrameDataLength += frameLength;
                }else if(isPps(mData[index+4])){

                    if(mFrameDataLength == 0) {
                        mFrameDataHeaderIndex = index;
                    }
                    mFrameDataLength += frameLength;
                }else if(isSei(mData[index+4])){

                    if(mFrameDataLength == 0) {
                        mFrameDataHeaderIndex = index;
                    }
                    mFrameDataLength += frameLength;
                }else {

                    if(mFrameDataLength == 0) {
                        mFrameDataHeaderIndex = index;
                    }
                    mFrameDataLength += frameLength;

                    if(mOnDataParsedListener != null){
                        mOnDataParsedListener.onParsed(mData, mFrameDataHeaderIndex, mFrameDataLength);
                    }

                    mFrameDataLength = 0;
                    mFrameDataHeaderIndex = 0;
                }

                index += frameLength;
            } else{
                index += 1;
            }
        }

        return 0;
    }

    private boolean isH264Header(byte[] data, int index){
        if(mData[index] == 0 && mData[index+1] == 0 && mData[index+2] == 0 && mData[index+3] == 1){
            return true;
        }else {
            return false;
        }
    }

    private boolean isSps(byte data){

        int nal_unit_type = ((short)data) & 0x001f;
        if(nal_unit_type == 7){
            return true;
        }
        return false;
    }

    private boolean isPps(byte data){

        int nal_unit_type = ((short)data) & 0x001f;
        if(nal_unit_type == 8){
            return true;
        }
        return false;
    }

    private boolean isSei(byte data){

        int nal_unit_type = ((short)data) & 0x001f;
        if(nal_unit_type == 6){
            return true;
        }
        return false;
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

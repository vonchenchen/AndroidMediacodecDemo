package com.vonchenchen.mediacodecdemo.io;

import com.vonchenchen.mediacodecdemo.video.Logger;
import com.vonchenchen.mediacodecdemo.video.Utils;
import com.vonchenchen.mediacodecdemo.video.streamformat.IvfFormat;

import java.io.FileNotFoundException;

public class IVFDataReader extends MediaDataReader implements IStreamDataReader {

    private final String TAG = "IVFDataReader";

    private byte[] mData;
    private int mDataRearIndex;

    private int mMaxFrameSize;
    private OnDataParsedListener mOnDataParsedListener = null;

    public IVFDataReader(String path, int maxFrameSize) throws FileNotFoundException {
        super(path);
        mData = new byte[maxFrameSize];
        mDataRearIndex = 0;
        mMaxFrameSize = maxFrameSize;

        //跳过数据头
        //read(mData, IvfFormat.IVF_HEADER_LEN);
    }

    @Override
    public int readNextFrame() {

        //读取每个ivf帧头
        int length = read(mData, IvfFormat.IVF_FRAME_HEADER_LEN);
        if(length < 12){
            return 0;
        }

        //ivf 数据头  不能抛给解码器
        if(mData[0] == 'D' && mData[1] == 'K' && mData[2] == 'I' && mData[3] == 'F'){
            read(mData, IvfFormat.IVF_FRAME_HEADER_LEN, IvfFormat.IVF_HEADER_LEN - IvfFormat.IVF_FRAME_HEADER_LEN);

            if(mOnDataParsedListener != null){
                //mOnDataParsedListener.onParsed(mData, 0, IvfFormat.IVF_HEADER_LEN);
            }
            return IvfFormat.IVF_HEADER_LEN;
        }

        //从ivf frame头读出帧长度
        int frameLengh = Utils.getLittleEndianUint32(mData, 0);
        Logger.i(TAG, "IVF payload frameLengh="+frameLengh);

        //读出后面的帧数据  连ivf封装格式一起抛出
//        int readLength = read(mData, 12, frameLengh);
//        if(mOnDataParsedListener != null){
//            mOnDataParsedListener.onParsed(mData, 0, IvfFormat.IVF_HEADER_LEN+frameLengh);
//        }

        //读出后面的帧数据  去掉ivf封装只抛出数据
        int readLength = read(mData, 0, frameLengh);
        if(mOnDataParsedListener != null){
            mOnDataParsedListener.onParsed(mData, 0, frameLengh);
        }

        return (IvfFormat.IVF_HEADER_LEN+frameLengh);
    }

    @Override
    public void setOnDataParsedListener(OnDataParsedListener onDataParsedListener) {
        mOnDataParsedListener = onDataParsedListener;
    }
}

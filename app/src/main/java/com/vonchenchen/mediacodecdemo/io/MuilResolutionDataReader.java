package com.vonchenchen.mediacodecdemo.io;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;

/**
 * 读取多个H264视频
 * 用于模拟传输中对方视频分辨率变化的情况
 */
public class MuilResolutionDataReader<T extends MediaDataReader> implements IStreamDataReader {

    private List<MediaDataReader> mediaDataReaderList;

    public MuilResolutionDataReader(List<MediaDataReader> list) throws FileNotFoundException {

        mediaDataReaderList = list;
    }

    @Override
    public int readNextFrame() {



        return 0;
    }

    @Override
    public void setOnDataParsedListener(OnDataParsedListener onDataParsedListener) {

    }

    private OnDataParsedListener mOnDataParsedListener = null;

    @Override
    public void close() {

    }
}

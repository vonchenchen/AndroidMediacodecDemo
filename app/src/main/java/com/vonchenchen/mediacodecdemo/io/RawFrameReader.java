package com.vonchenchen.mediacodecdemo.io;

import java.io.FileNotFoundException;

public class RawFrameReader extends MediaDataReader implements IStreamDataReader{

    private OnDataParsedListener mOnDataParsedListener;

    public RawFrameReader(String path) throws FileNotFoundException {
        super(path);
    }

    @Override
    public int readNextFrame() {



        return 0;
    }

    @Override
    public void setOnDataParsedListener(OnDataParsedListener onDataParsedListener) {
        mOnDataParsedListener = onDataParsedListener;
    }
}

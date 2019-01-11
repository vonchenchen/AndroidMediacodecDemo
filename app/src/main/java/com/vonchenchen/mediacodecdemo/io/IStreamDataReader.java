package com.vonchenchen.mediacodecdemo.io;

public interface IStreamDataReader {

    int readNextFrame();
    void setOnDataParsedListener(OnDataParsedListener onDataParsedListener);
    void close();

    interface OnDataParsedListener {
        void onParsed(byte[] data, int index, int length);
    }
}

package com.vonchenchen.mediacodecdemo.io;

import com.vonchenchen.mediacodecdemo.video.Logger;
import com.vonchenchen.mediacodecdemo.video.Utils;

import java.io.FileNotFoundException;

public class FileDataProvider extends AbsDataProvider {

    private static String TAG = "FileDataProvider";

    private static final int READ_SIZE = 1024 * 2;

    private String mPath;
    private MediaDataReader mMediaDataReader;
    private DataInfo mDataInfo;

    public FileDataProvider(String path){

        mPath = path;
        try {
            mMediaDataReader = new MediaDataReader(mPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        mDataInfo = new DataInfo();
        mDataInfo.data = new byte[READ_SIZE];
        mDataInfo.index = 0;
    }

    @Override
    public DataInfo provideData() {
        mDataInfo.length = mMediaDataReader.read(mDataInfo.data, mDataInfo.index, READ_SIZE);
        Logger.i(TAG, Utils.byteArrayToHexString(mDataInfo.data));
        return mDataInfo;
    }
}

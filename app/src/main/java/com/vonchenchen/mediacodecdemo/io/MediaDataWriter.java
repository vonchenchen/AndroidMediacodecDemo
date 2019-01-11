package com.vonchenchen.mediacodecdemo.io;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by vonchenchen on 2018/5/19.
 */

public class MediaDataWriter {

    private static final String TAG = "MediaDataWriter";

    private String mPath;
    private File mTargetFile;
    private OutputStream mFileOutputStream;
    private int mCurrentIndex;

    public MediaDataWriter(String path) throws FileNotFoundException {
        mPath = path;
        mTargetFile = new File(path);
        mFileOutputStream = new FileOutputStream(mTargetFile);
        mCurrentIndex = 0;
    }

    /**
     * 尾部追加
     * @param data
     * @param length
     */
    public void write(byte[] data, int length){

        try {
            mFileOutputStream.write(data, 0, length);
            mFileOutputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "MediaDataWriter [writeToTail] "+e.toString());
            try {
                mFileOutputStream.close();
            } catch (IOException e1) {
                Log.e(TAG, "MediaDataWriter [writeToIndex] "+e.toString());
            }
        }
    }

    public void writeToIndex(byte[] data, int index, int length){

        try {
            mFileOutputStream.write(data, index, length);
            mFileOutputStream.flush();
            mCurrentIndex = index;
        } catch (IOException e) {
            Log.e(TAG, "MediaDataWriter [writeToIndex] "+e.toString());
            try {
                mFileOutputStream.close();
            } catch (IOException e1) {
                Log.e(TAG, "MediaDataWriter [writeToIndex] "+e.toString());
            }
        }
    }

    public void deleteFile(){
        if(mTargetFile != null && !mTargetFile.isDirectory()){
            mTargetFile.delete();
        }
    }

    public void close(){
        if(mFileOutputStream != null){
            try {
                mFileOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "MediaDataWriter [close] "+e.toString());
            }
        }
    }
}

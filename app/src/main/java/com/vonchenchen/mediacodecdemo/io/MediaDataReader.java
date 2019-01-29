package com.vonchenchen.mediacodecdemo.io;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by vonchenchen on 2018/5/17.
 */

public class MediaDataReader{

    private static final String TAG = "MediaDataReader";

    private String mPath;
    private File mTargetFile;
    private InputStream mFileInputStream;

    public MediaDataReader(String path) throws FileNotFoundException {
        mPath = path;
        mTargetFile = new File(path);
        mFileInputStream = new FileInputStream(mTargetFile);
    }

    /**
     * 尾部追加
     * @param data
     * @param length
     */
    public int read(byte[] data, int length){

        int ret = -1;
        try {
            if(mFileInputStream == null){
                mTargetFile = new File(mPath);
                mFileInputStream = new FileInputStream(mTargetFile);
            }
            ret = mFileInputStream.read(data, 0, length);
            return ret;
        } catch (IOException e) {
            Log.e(TAG, "MediaDataReader [readToTail] "+e.toString());
            try {
                mFileInputStream.close();
            } catch (IOException e1) {
                Log.e(TAG, "MediaDataReader [readToTail] "+e.toString());
            }
        }
        return ret;
    }

    public int read(byte[] data, int index, int length){

        int ret = -1;
        try {
            if(mFileInputStream == null){
                mTargetFile = new File(mPath);
                mFileInputStream = new FileInputStream(mTargetFile);
            }
            ret = mFileInputStream.read(data, index, length);
            return ret;
        } catch (IOException e) {
            Log.e(TAG, "MediaDataReader [readToTail] "+e.toString());
            try {
                mFileInputStream.close();
            } catch (IOException e1) {
                Log.e(TAG, "MediaDataReader [readToTail] "+e.toString());
            }
        }
        return ret;
    }

    public void close(){
        if(mFileInputStream != null){
            try {
                mFileInputStream.close();
                mFileInputStream = null;
            } catch (IOException e) {
                Log.e(TAG, "MediaDataReader [close] "+e.toString());
            }
        }
    }

    public void reset(){
        close();
        mTargetFile = new File(mPath);
        try {
            mFileInputStream = new FileInputStream(mTargetFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}

package com.vonchenchen.mediacodecdemo.io;

public abstract class AbsDataProvider {

    public class DataInfo{
        public byte[] data;
        public int index;
        public int length;
    }

    abstract public DataInfo provideData();
}

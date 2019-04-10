package com.vonchenchen.mediacodecdemo.video.statistics;

public class StatisticsData {

    private int frameRate;
    private int bitrate;

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public int getBitrate() {
        return bitrate;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    @Override
    public String toString() {
        return "StatisticsData{" +
                "frameRate=" + frameRate +
                ", bitrate=" + bitrate +
                '}';
    }
}

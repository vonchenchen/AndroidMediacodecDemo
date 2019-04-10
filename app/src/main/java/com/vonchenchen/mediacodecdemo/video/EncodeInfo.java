package com.vonchenchen.mediacodecdemo.video;

import android.media.MediaCodecInfo;

public class EncodeInfo {

    /** 码率模式 */
    public int bitrateMode;
    /** 码率 */
    public int bitrate;
    /** 关键帧间隔 */
    public int keyFrameInterval;
    /** 帧率 */
    public int framerate;
    /** profile */
    public int profile;

    public EncodeInfo(){
        profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
    }
}

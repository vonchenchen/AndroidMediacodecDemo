package com.vonchenchen.mediacodecdemo.video;

public class CodecMsg {

    enum MSG{
        /** 通知解码成功 */
        MSG_DECODE_FRAME_READY,
        /** 结束渲染任务 */
        MSG_STOP_RENDER_TASK,
        /** 暂停渲染任务 */
        MSG_PAUSE_RENDER_TASK,
        /** 恢复渲染任务 */
        MSG_RESUME_RENDER_TASK,
        /** 解码器重置 */
        MSG_RESET_DECODER,

        /** 编码采集帧成功 */
        MSG_ENCODE_CAPTURE_FRAME_READY,
        /** 编码恢复渲染 */
        MSG_ENCODE_RESUME_RENDER,
        /** 编码暂停渲染 */
        MSG_ENCODE_PAUSE_RENDER,
        /** 结束编码任务 */
        MSG_ENCODE_STOP_TASK,
        /** 改变编码器参数 */
        MSG_ENCODE_CHANGE_BITRATE;
    }

    public CodecMsg(){
        encodeInfo = new EncodeInfo();
    }

    public MSG currentMsg;

    public int currentFrameWidth;
    public int currentFrameHeight;

    public EncodeInfo encodeInfo;

    public byte[] data;
    public int offset;
    public int length;
    public long pts;
}

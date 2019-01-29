package com.vonchenchen.mediacodecdemo.video;

public class CodecMsg {

    enum MSG{
        /** 通知解码成功 */
        MSG_DECODE_FRAME_READY,
        /** 停止渲染任务 */
        MSG_STOP_RENDER_TASK;
    }

    public MSG currentMsg;
}

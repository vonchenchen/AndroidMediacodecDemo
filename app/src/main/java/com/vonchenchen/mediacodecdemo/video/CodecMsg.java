package com.vonchenchen.mediacodecdemo.video;

public class CodecMsg {

    enum MSG{
        /** 通知解码成功 */
        MSG_DECODE_FRAME_READY,
        /** 停止渲染任务 */
        MSG_STOP_RENDER_TASK,
        /** 暂停渲染任务 */
        MSG_PAUSE_RENDER_TASK,
        /** 恢复渲染任务 */
        MSG_RESUME_RENDER_TASK
    }

    public MSG currentMsg;
}

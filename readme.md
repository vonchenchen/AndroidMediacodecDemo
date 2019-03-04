## Android MediaCodec测试demo

本demo用于测试MediaCodec相关功能，主要涉及调用Android MediaCodec进行编解码，以及渲染相关流程，渲染部分主要参考了Grafik。

#### 目前测试demo提供了以下功能:
1.H264编码以及保存视频
2.H264解码渲染
3.VP8解码渲染
4.H264码率控制模式设置
5.H264码率设置（可以动态设置）
6.H264帧率设置（可以动态设置）
7.H264 IDR间隔设置
8.MediaCodec解码后通过Opengl渲染视频
9.应用推后台测试
10.相机分辨率选择

### 1.视频采集编码及其渲染流程

视频编码采用了自建SurfaceTexure的方式，直接使用自建纹理填入相机

### 2.视频解码及其渲染流程

### 3.Opengl相关代码分析
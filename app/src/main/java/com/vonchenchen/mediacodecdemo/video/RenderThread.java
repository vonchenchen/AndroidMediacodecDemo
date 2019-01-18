package com.vonchenchen.mediacodecdemo.video;

import android.content.res.Configuration;
import android.opengl.Matrix;

import com.vonchenchen.mediacodecdemo.video.egl.WindowSurface;

public class RenderThread extends BaseThread{

    /** 相机矩阵 由当前相机纹理生成的SurfaceTexture获取 */
    private final float[] mCameraMVPMatrix = new float[16];
    /** 处理当前编码图像的 平移 旋转 缩放 */
    private final float[] mEncodeTextureMatrix = new float[16];

    /** 渲染surface 由外部需要渲染的画布传入 */
    private WindowSurface mDisplaySurface;

    public RenderThread(String name) {
        super(name);

        Matrix.setIdentityM(mEncodeTextureMatrix, 0);
        Matrix.translateM(mEncodeTextureMatrix, 0, 0.5f, 0.5f, 0);
        Matrix.scaleM(mEncodeTextureMatrix, 0, -1, -1, 1);
        Matrix.rotateM(mEncodeTextureMatrix, 0, 0, 0, 0, 1);
        Matrix.translateM(mEncodeTextureMatrix, 0, -0.5f, -0.5f, 0);
    }

    @Override
    protected void frameAvailableInThread() {

    }

    @Override
    protected void finishThread() {

    }

    @Override
    public void run() {
        super.run();
    }

    @Override
    public synchronized void start() {
        super.start();
    }
}

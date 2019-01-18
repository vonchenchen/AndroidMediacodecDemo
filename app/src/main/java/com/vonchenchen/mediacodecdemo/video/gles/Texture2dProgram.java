/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vonchenchen.mediacodecdemo.video.gles;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.vonchenchen.mediacodecdemo.video.Logger;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_RGB;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glDeleteFramebuffers;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glFramebufferTexture2D;
import static android.opengl.GLES20.glGenFramebuffers;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glTexImage2D;
import static android.opengl.GLES20.glTexParameterf;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES20.glUniform1f;
import static android.opengl.GLES20.glUniform1fv;
import static android.opengl.GLES20.glUniform2fv;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;


/**
 * GL program and supporting functions for textured 2D shapes.
 */
public class Texture2dProgram {
    private static final String TAG = "Texture2dProgram";

    public enum ProgramType {
        TEXTURE_2D, TEXTURE_EXT, TEXTURE_EXT_BW, TEXTURE_EXT_FILT, COLOR
    }

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_COLOR =
            "precision mediump float;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.25);\n" +
            "}\n";

    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_2D =
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    // Simple fragment shader for use with external 2D textures (e.g. what we get from
    // SurfaceTexture).
    private static final String FRAGMENT_SHADER_EXT =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    // Fragment shader that converts color to black & white with a simple transformation.
    private static final String FRAGMENT_SHADER_EXT_BW =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "    vec4 tc = texture2D(sTexture, vTextureCoord);\n" +
            "    float color = tc.r * 0.3 + tc.g * 0.59 + tc.b * 0.11;\n" +
            "    gl_FragColor = vec4(color, color, color, 1.0);\n" +
            "}\n";

    // Fragment shader with a convolution filter.  The upper-left half will be drawn normally,
    // the lower-right half will have the filter applied, and a thin red line will be drawn
    // at the border.
    //
    // This is not optimized for performance.  Some things that might make this faster:
    // - Remove the conditionals.  They're used to present a half & half view with a red
    //   stripe across the middle, but that's only useful for a demo.
    // - Unroll the loop.  Ideally the compiler does this for you when it's beneficial.
    // - Bake the filter kernel into the shader, instead of passing it through a uniform
    //   array.  That, combined with loop unrolling, should reduce memory accesses.
    public static final int KERNEL_SIZE = 9;
    private static final String FRAGMENT_SHADER_EXT_FILT =
            "#extension GL_OES_EGL_image_external : require\n" +
            "#define KERNEL_SIZE " + KERNEL_SIZE + "\n" +
            "precision highp float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float uKernel[KERNEL_SIZE];\n" +
            "uniform vec2 uTexOffset[KERNEL_SIZE];\n" +
            "uniform float uColorAdjust;\n" +
            "void main() {\n" +
            "    int i = 0;\n" +
            "    vec4 sum = vec4(0.0);\n" +
            "    if (vTextureCoord.x < vTextureCoord.y - 0.005) {\n" +
            "        for (i = 0; i < KERNEL_SIZE; i++) {\n" +
            "            vec4 texc = texture2D(sTexture, vTextureCoord + uTexOffset[i]);\n" +
            "            sum += texc * uKernel[i];\n" +
            "        }\n" +
            "    sum += uColorAdjust;\n" +
            "    } else if (vTextureCoord.x > vTextureCoord.y + 0.005) {\n" +
            "        sum = texture2D(sTexture, vTextureCoord);\n" +
            "    } else {\n" +
            "        sum.r = 1.0;\n" +
            "    }\n" +
            "    gl_FragColor = sum;\n" +
            "}\n";

    private ProgramType mProgramType;

    // Handles to the GL program and various components of it.
    private int mProgramHandle;
    private int muMVPMatrixLoc;
    private int muTexMatrixLoc;
    private int muKernelLoc;
    private int muTexOffsetLoc;
    private int muColorAdjustLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;

    private int mTextureTarget;

    private float[] mKernel = new float[KERNEL_SIZE];
    private float[] mTexOffset;
    private float mColorAdjust;

    private int mFBOHandle;

    private boolean mHasAlpha;

    /**
     * 在当前egl环境下创建program 并拿出相关变量的索引到java层
     * Prepares the program in the current EGL context.
     */
    public Texture2dProgram(ProgramType programType) {
        mProgramType = programType;

        switch (programType) {
            case TEXTURE_2D:
                mTextureTarget = GL_TEXTURE_2D;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D);
                break;
            case TEXTURE_EXT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT);
                break;
            case TEXTURE_EXT_BW:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_BW);
                break;
            case TEXTURE_EXT_FILT:
                mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT_FILT);
                break;
            case COLOR:
            	mTextureTarget = 0;
                mProgramHandle = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_COLOR);
                break;
            default:
                throw new RuntimeException("Unhandled type " + programType);
        }
        if (mProgramHandle == 0) {
            throw new RuntimeException("Unable to create program");
        }
        Logger.d(TAG, "Created program " + mProgramHandle + " (" + programType + ")");

        // get locations of attributes and uniforms

        maPositionLoc = glGetAttribLocation(mProgramHandle, "aPosition");
        GlUtil.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = glGetAttribLocation(mProgramHandle, "aTextureCoord");
//        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muMVPMatrixLoc = glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muTexMatrixLoc = glGetUniformLocation(mProgramHandle, "uTexMatrix");
        GlUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");
        muKernelLoc = glGetUniformLocation(mProgramHandle, "uKernel");
        if (muKernelLoc < 0) {
            // no kernel in this one
            muKernelLoc = -1;
            muTexOffsetLoc = -1;
            muColorAdjustLoc = -1;
        } else {
            // has kernel, must also have tex offset and color adj
            muTexOffsetLoc = glGetUniformLocation(mProgramHandle, "uTexOffset");
            GlUtil.checkLocation(muTexOffsetLoc, "uTexOffset");
            muColorAdjustLoc = glGetUniformLocation(mProgramHandle, "uColorAdjust");
            GlUtil.checkLocation(muColorAdjustLoc, "uColorAdjust");

            // initialize default values
            setKernel(new float[] {0f, 0f, 0f,  0f, 1f, 0f,  0f, 0f, 0f}, 0f);
            setTexSize(256, 256);
        }
    }

    /**
     * Releases the program.
     * <p>
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    public void release() {
        Logger.d(TAG, "deleting program " + mProgramHandle);
        glDeleteProgram(mProgramHandle);
        if (mFBOHandle != 0) {
        	int[] ids = new int[] { mFBOHandle };
        	glDeleteFramebuffers(1, ids, 0);
        	mFBOHandle = 0;
        }
        mProgramHandle = -1;
    }

    /**
     * Returns the program type.
     */
    public ProgramType getProgramType() {
        return mProgramType;
    }

    /**
     * Creates a texture object suitable for use with this program.
     * <p>
     * On exit, the texture will be bound.
     */
    public int createTextureObject() {
        int[] textures = createTextureObjects(1);
        return textures[0];
    }

    public int[] createTextureObjects(int count) {
    	if (mTextureTarget == 0) return null;
    	return createTextureObjects(count, 0, 0, mTextureTarget);
    }

    public int[] createTextureObjects(int count, int width, int height, int textureTarget) {
    	int[] textures = new int[count];
    	glGenTextures(count, textures, 0);
    	if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glGenTextures");

    	for (int i = 0; i < count; i++) {
            int texId = textures[i];
            glBindTexture(textureTarget, texId);
            if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glBindTexture " + texId);

            if (textureTarget == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
	            glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER,
	            		GL_LINEAR);
	            glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER,
	                    GL_LINEAR);
	            glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S,
	                    GL_CLAMP_TO_EDGE);
	            glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T,
	                    GL_CLAMP_TO_EDGE);
	            if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glTexParameter");
            } else {
	            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,
	            		GL_LINEAR);
	            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER,
	                    GL_LINEAR);
	            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S,
	                    GL_CLAMP_TO_EDGE);
	            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T,
	                    GL_CLAMP_TO_EDGE);
	            if (width > 0 && height > 0) {
	            	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height,
	            			0, GL_RGB, GL_UNSIGNED_BYTE, null);
	            }
	            if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glTexParameter");
            }
		}

    	return textures;
    }

    public void beginDrawToTexture(int textureId) {

    	if (mFBOHandle == 0) {
    		int[] ids = new int[1];
    		glGenFramebuffers(1, ids, 0);
        	if (GlUtil.CHECK_ERROR) {
        	    GlUtil.checkGlError("glGenFramebuffers");
            }
    		mFBOHandle = ids[0];
    	}
		glBindFramebuffer(GL_FRAMEBUFFER, mFBOHandle);
    	if (GlUtil.CHECK_ERROR) {
    	    GlUtil.checkGlError("glBindFramebuffer");
        }
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureId, 0);
    	if (GlUtil.CHECK_ERROR) {
    	    GlUtil.checkGlError("glFramebufferTexture2D");
        }
    }

    public void beginDrawToDefault() {
//		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
    	if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("beginDrawToDefault");
    }

    /**
     * Configures the convolution filter values.
     *
     * @param values Normalized filter values; must be KERNEL_SIZE elements.
     */
    public void setKernel(float[] values, float colorAdj) {
        if (values.length != KERNEL_SIZE) {
            throw new IllegalArgumentException("Kernel size is " + values.length +
                    " vs. " + KERNEL_SIZE);
        }
        System.arraycopy(values, 0, mKernel, 0, KERNEL_SIZE);
        mColorAdjust = colorAdj;
        //Logger.d(TAG, "filt kernel: " + Arrays.toString(mKernel) + ", adj=" + colorAdj);
    }

    /**
     * Sets the size of the texture.  This is used to find adjacent texels when filtering.
     */
    public void setTexSize(int width, int height) {
        float rw = 1.0f / width;
        float rh = 1.0f / height;

        // Don't need to create a new array here, but it's syntactically convenient.
        mTexOffset = new float[] {
            -rw, -rh,   0f, -rh,    rw, -rh,
            -rw, 0f,    0f, 0f,     rw, 0f,
            -rw, rh,    0f, rh,     rw, rh
        };
        //Logger.d(TAG, "filt size: " + width + "x" + height + ": " + Arrays.toString(mTexOffset));
    }

    public void setHasAlpha(boolean hasAlpha) {
    	mHasAlpha = hasAlpha;
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     *        vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     *        for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    public void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                     int vertexCount, int coordsPerVertex, int vertexStride,
                     float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride) {
    	if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("draw start");

    	if (mHasAlpha) {
    		GLES20.glEnable(GLES20.GL_BLEND);
    		GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    	}
    	else {
    		GLES20.glDisable(GLES20.GL_BLEND);
    	}

        // Select the program.
        glUseProgram(mProgramHandle);
        if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glUseProgram");

        if (mTextureTarget != 0) {
	        // Set the texture.
	        glActiveTexture(GL_TEXTURE0);
	        glBindTexture(mTextureTarget, textureId);
        }

        // Copy the model / view / projection matrix over.
        glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glUniformMatrix4fv");

        if (texMatrix != null) {
	        // Copy the texture transformation matrix over.
	        glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
	        if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glUniformMatrix4fv");
        }

        // Enable the "aPosition" vertex attribute.
        glEnableVertexAttribArray(maPositionLoc);
        if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        glVertexAttribPointer(maPositionLoc, coordsPerVertex,
            GL_FLOAT, false, vertexStride, vertexBuffer);
        if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glVertexAttribPointer");

        if (maTextureCoordLoc >= 0) {
            // Enable the "aTextureCoord" vertex attribute.
	        glEnableVertexAttribArray(maTextureCoordLoc);
	        if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glEnableVertexAttribArray");
	
	        // Connect texBuffer to "aTextureCoord".
	        glVertexAttribPointer(maTextureCoordLoc, 2,
	                GL_FLOAT, false, texStride, texBuffer);
	        if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glVertexAttribPointer");
        }

        // Populate the convolution kernel, if present.
        if (muKernelLoc >= 0) {
            glUniform1fv(muKernelLoc, KERNEL_SIZE, mKernel, 0);
            glUniform2fv(muTexOffsetLoc, KERNEL_SIZE, mTexOffset, 0);
            glUniform1f(muColorAdjustLoc, mColorAdjust);
        }

        // Draw the rect.
        glDrawArrays(GL_TRIANGLE_STRIP, firstVertex, vertexCount);
        if (GlUtil.CHECK_ERROR) GlUtil.checkGlError("glDrawArrays");

        // Done -- disable vertex array, texture, and program.
        glDisableVertexAttribArray(maPositionLoc);
        if (maTextureCoordLoc >= 0) {
        	glDisableVertexAttribArray(maTextureCoordLoc);
        }
        if (mTextureTarget != 0) glBindTexture(mTextureTarget, 0);
        glUseProgram(0);
    }
}

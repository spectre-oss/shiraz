// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.opengl.GLES20;
import java.nio.ByteBuffer;

/** Called only with a current EGL context; resources die with the per-case context. */
final class GlChecks extends ProbeChecks {
  static int shader(int type, String source) {
    int shader = GLES20.glCreateShader(type);
    check(shader != 0);
    GLES20.glShaderSource(shader, source);
    GLES20.glCompileShader(shader);
    int[] status = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
    if (status[0] != GLES20.GL_TRUE)
      throw new AssertionError(GLES20.glGetShaderInfoLog(shader));
    return shader;
  }
  static void run(String mode) {
    switch (mode) {
      case "clear":
        GLES20.glClearColor(1, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GLES20.glReadPixels(0, 0, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel);
        check((pixel.get(0) & 255) > 240 && (pixel.get(1) & 255) < 10);
        break;
      case "gles_shader":
        int vertex = shader(
            GLES20.GL_VERTEX_SHADER, "attribute vec4 position; void main(){gl_Position=position;}");
        int fragment = shader(GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float; void main(){gl_FragColor=vec4(1.,0.,0.,1.);}");
        int program = GLES20.glCreateProgram();
        try {
          GLES20.glAttachShader(program, vertex);
          GLES20.glAttachShader(program, fragment);
          GLES20.glLinkProgram(program);
          int[] status = new int[1];
          GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
          if (status[0] != GLES20.GL_TRUE)
            throw new AssertionError(GLES20.glGetProgramInfoLog(program));
        } finally {
          GLES20.glDeleteProgram(program);
          GLES20.glDeleteShader(vertex);
          GLES20.glDeleteShader(fragment);
        }
        break;
      case "gles_texture":
        int[] texture = new int[1], framebuffer = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glGenFramebuffers(1, framebuffer, 0);
        try {
          GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
          GLES20.glTexParameteri(
              GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
          GLES20.glTexParameteri(
              GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
          ByteBuffer data = ByteBuffer.allocateDirect(4);
          data.put(new byte[] {(byte) 255, 0, 0, (byte) 255}).rewind();
          GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA,
              GLES20.GL_UNSIGNED_BYTE, data);
          GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
          GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
              GLES20.GL_TEXTURE_2D, texture[0], 0);
          check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
              == GLES20.GL_FRAMEBUFFER_COMPLETE);
          ByteBuffer read = ByteBuffer.allocateDirect(4);
          GLES20.glReadPixels(0, 0, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, read);
          check((read.get(0) & 255) == 255 && read.get(1) == 0);
        } finally {
          GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
          GLES20.glDeleteFramebuffers(1, framebuffer, 0);
          GLES20.glDeleteTextures(1, texture, 0);
        }
        break;
      case "gles_buffer":
        int[] buffer = new int[1];
        GLES20.glGenBuffers(1, buffer, 0);
        try {
          GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer[0]);
          GLES20.glBufferData(
              GLES20.GL_ARRAY_BUFFER, 16, ByteBuffer.allocateDirect(16), GLES20.GL_STATIC_DRAW);
          int[] size = new int[1];
          GLES20.glGetBufferParameteriv(GLES20.GL_ARRAY_BUFFER, GLES20.GL_BUFFER_SIZE, size, 0);
          check(size[0] == 16);
        } finally {
          GLES20.glDeleteBuffers(1, buffer, 0);
        }
        break;
      case "gles_scissor":
        GLES20.glClearColor(0, 0, 1, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(0, 0, 1, 1);
        GLES20.glClearColor(1, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        ByteBuffer red = ByteBuffer.allocateDirect(4), blue = ByteBuffer.allocateDirect(4);
        GLES20.glReadPixels(0, 0, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, red);
        GLES20.glReadPixels(3, 3, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, blue);
        check((red.get(0) & 255) == 255 && (blue.get(2) & 255) == 255);
        break;
      case "gles_blend":
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        int[] value = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_BLEND_SRC_RGB, value, 0);
        check(GLES20.glIsEnabled(GLES20.GL_BLEND) && value[0] == GLES20.GL_SRC_ALPHA);
        break;
      default:
        throw new IllegalArgumentException(mode);
    }
    check(GLES20.glGetError() == GLES20.GL_NO_ERROR);
  }
}

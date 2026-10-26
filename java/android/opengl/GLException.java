// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;
public class GLException extends RuntimeException {
 private final int error;
 public GLException(int error){super("GL error "+error);this.error=error;}
 public GLException(int error,String message){super(message);this.error=error;}
 public int getError(){return error;}
}

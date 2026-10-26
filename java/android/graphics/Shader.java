// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Source colors sampled in local drawing coordinates. */
public class Shader {
 public enum TileMode { CLAMP, REPEAT, MIRROR }
 private final Matrix local=new Matrix();
 public Shader(){}
 public synchronized void setLocalMatrix(Matrix matrix){local.set(matrix);}
 public synchronized boolean getLocalMatrix(Matrix matrix){if(matrix==null)throw new NullPointerException("matrix");if(local.isIdentity())return false;matrix.set(local);return true;}
 final synchronized float[] inverse(float[] canvas){
  Matrix combined=new Matrix();combined.setValues(new float[]{canvas[0],canvas[2],canvas[4],canvas[1],canvas[3],canvas[5],0,0,1});combined.preConcat(local);
  Matrix inverse=new Matrix();if(!combined.isAffine()||!combined.invert(inverse))throw new IllegalArgumentException("Shader requires an invertible affine matrix");
  float[] m=new float[9];inverse.getValues(m);for(float v:m)if(!Float.isFinite(v)||Math.abs(v)>10000000)throw new IllegalArgumentException("Shader matrix range");
  return new float[]{m[0],m[3],m[1],m[4],m[2],m[5]};
 }
}

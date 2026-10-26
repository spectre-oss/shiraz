// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable.shapes;
import android.graphics.*;
public class RectShape extends Shape {
 private RectF bounds=new RectF();
 protected final RectF rect(){return bounds;}
 protected void onResize(float w,float h){bounds.set(0,0,w,h);}
 public void draw(Canvas canvas,Paint paint){canvas.drawRect(bounds.left,bounds.top,bounds.right,bounds.bottom,paint);}
 public void getOutline(Outline outline){outline.setRect(0,0,(int)Math.ceil(getWidth()),(int)Math.ceil(getHeight()));}
 public RectShape clone() throws CloneNotSupportedException{RectShape result=(RectShape)super.clone();result.bounds=new RectF(bounds);return result;}
}

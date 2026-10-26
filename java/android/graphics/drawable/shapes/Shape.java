// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable.shapes;
import android.graphics.*;
public abstract class Shape implements Cloneable {
 private float width,height;
 public abstract void draw(Canvas canvas,Paint paint);
 public final float getWidth(){return width;}public final float getHeight(){return height;}
 public final void resize(float w,float h){if(!Float.isFinite(w)||!Float.isFinite(h)||w>16384||h>16384)throw new IllegalArgumentException("Shape size bound");w=Math.max(0,w);h=Math.max(0,h);if(w!=width||h!=height){width=w;height=h;onResize(w,h);}}
 protected void onResize(float w,float h){}
 public boolean hasAlpha(){return true;}
 public void getOutline(Outline outline){outline.setEmpty();}
 public Shape clone() throws CloneNotSupportedException{return (Shape)super.clone();}
}

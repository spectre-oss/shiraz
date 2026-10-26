// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable.shapes;
import android.graphics.*;
public class OvalShape extends RectShape {
 public void draw(Canvas canvas,Paint paint){canvas.drawOval(rect(),paint);}
 public void getOutline(Outline outline){outline.setOval(0,0,(int)Math.ceil(getWidth()),(int)Math.ceil(getHeight()));}
 public OvalShape clone() throws CloneNotSupportedException{return (OvalShape)super.clone();}
}

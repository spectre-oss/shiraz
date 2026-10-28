// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.Canvas;
import android.graphics.Rect;

/** Rotates a child around an absolute or relative pivot as its level changes. */
public class RotateDrawable extends DrawableWrapper {
 private float fromDegrees=0,toDegrees=360,pivotX=.5f,pivotY=.5f;
 private boolean pivotXRelative=true,pivotYRelative=true;
 public RotateDrawable(){super(null);}
 private static float finite(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite rotation");return value;}
 public void setFromDegrees(float value){fromDegrees=finite(value);invalidateSelf();}
 public float getFromDegrees(){return fromDegrees;}
 public void setToDegrees(float value){toDegrees=finite(value);invalidateSelf();}
 public float getToDegrees(){return toDegrees;}
 public void setPivotX(float value){pivotX=finite(value);invalidateSelf();}
 public float getPivotX(){return pivotX;}
 public void setPivotY(float value){pivotY=finite(value);invalidateSelf();}
 public float getPivotY(){return pivotY;}
 public void setPivotXRelative(boolean value){pivotXRelative=value;invalidateSelf();}
 public boolean isPivotXRelative(){return pivotXRelative;}
 public void setPivotYRelative(boolean value){pivotYRelative=value;invalidateSelf();}
 public boolean isPivotYRelative(){return pivotYRelative;}
 @Override protected boolean onLevelChange(int level){super.onLevelChange(level);invalidateSelf();return true;}
 @Override public void draw(Canvas canvas){
  Drawable child=getDrawable();if(child==null)return;
  Rect bounds=child.getBounds();int saved=canvas.save();
  try{
   float x=bounds.left+(pivotXRelative?bounds.width()*pivotX:pivotX);
   float y=bounds.top+(pivotYRelative?bounds.height()*pivotY:pivotY);
   canvas.rotate(fromDegrees+(toDegrees-fromDegrees)*(getLevel()/10000f),x,y);
   child.draw(canvas);
  }finally{canvas.restoreToCount(saved);}
 }
}

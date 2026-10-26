// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
public class ImageSpan extends DynamicDrawableSpan {
 private Drawable drawable;
 private final String source;
 public ImageSpan(Drawable drawable){this(drawable,ALIGN_BOTTOM);}
 public ImageSpan(Drawable drawable,int verticalAlignment){this(drawable,null,verticalAlignment);}
 public ImageSpan(Drawable drawable,String source,int verticalAlignment){super(verticalAlignment);this.drawable=drawable;this.source=source;}
 public ImageSpan(Context context,Bitmap bitmap){this(context,bitmap,ALIGN_BOTTOM);}
 public ImageSpan(Context context,Bitmap bitmap,int verticalAlignment){super(verticalAlignment);drawable=fit(new BitmapDrawable(context.getResources(),bitmap));source=null;}
 public ImageSpan(Context context,int resourceId){this(context,resourceId,ALIGN_BOTTOM);}
 public ImageSpan(Context context,int resourceId,int verticalAlignment){super(verticalAlignment);drawable=fit(context.getDrawable(resourceId));source=null;}
 private static Drawable fit(Drawable d){if(d!=null)d.setBounds(0,0,Math.max(0,d.getIntrinsicWidth()),Math.max(0,d.getIntrinsicHeight()));return d;}
 public Drawable getDrawable(){return drawable;}
 public String getSource(){return source;}
}

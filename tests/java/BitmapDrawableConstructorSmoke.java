// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
public final class BitmapDrawableConstructorSmoke {
    private static final class AppDrawable extends BitmapDrawable {
        AppDrawable(){super();getPaint().setFlags(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);}
    }
    public static void main(String[] args){
        BitmapDrawable drawable=new AppDrawable();
        if(drawable.getPaint()==null||!drawable.getPaint().isAntiAlias()||drawable.getBitmap()!=null||drawable.getIntrinsicWidth()!=-1)throw new AssertionError("empty drawable constructor must initialize paint");
        System.out.println("BITMAP_DRAWABLE_CONSTRUCTOR_OK");
    }
}

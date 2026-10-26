// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.res.Resources;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
public final class VectorFixture {
    private static void check(boolean value,String why) { if (!value) throw new AssertionError(why); }
    private static int id(Resources r,String type,String name) { int id=r.getIdentifier(name,type,"org.shiraz.fixture"); check(id != 0,"vector fixture ID "+name); return id; }
    private static Drawable load(Resources r,String name) { Drawable d=r.getDrawable(id(r,"drawable",name)); check(d instanceof VectorDrawable,"real vector type"); return d; }
    private static Bitmap draw(Drawable d,int w,int h) { Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); d.setBounds(0,0,w,h); d.draw(new Canvas(b)); return b; }
    public static void check(Resources r) {
        Drawable group=load(r,"vector_group"); Bitmap b=draw(group,16,16);
        check(b.getPixel(5,5)==Color.RED && b.getPixel(10,6)==Color.RED && b.getPixel(2,2)==0 && b.getPixel(13,5)==0,"group affine geometry"); b.recycle();
        b=draw(load(r,"vector_overlap"),16,16); check((b.getPixel(8,8)>>>24)==128 && (b.getPixel(2,2)>>>24)==128 && (b.getPixel(8,8)&0xffffff)==255,"root alpha applies after composition"); b.recycle();
        Drawable state=load(r,"vector_state"); check(state.isStateful(),"stateful fill"); b=draw(state,16,16); check(b.getPixel(2,8)==Color.RED && b.getPixel(13,8)==0,"default path state"); b.recycle();
        state.setState(new int[]{android.R.attr.state_pressed}); b=draw(state,16,16); check(b.getPixel(2,8)==Color.GREEN,"pressed path state"); b.recycle();
        state.setLayoutDirection(1); b=draw(state,16,16); check(b.getPixel(13,8)==Color.GREEN && b.getPixel(2,8)==0,"RTL mirror"); b.recycle();
        state.setLayoutDirection(0); state.setTintList(ColorStateList.valueOf(Color.BLUE)); b=draw(state,16,16); check(b.getPixel(2,8)==Color.BLUE,"root tint"); b.recycle();
        state.setColorFilter(new PorterDuffColorFilter(Color.RED,PorterDuff.Mode.SRC_IN)); b=draw(state,16,16); check(b.getPixel(2,8)==Color.RED,"explicit filter overrides tint"); b.recycle(); state.clearColorFilter();
        state.setAlpha(100); state.setChangingConfigurations(0x1000); Drawable.ConstantState saved=state.getConstantState(); Drawable copy=saved.newDrawable(r); check(copy.getAlpha()==100 && (copy.getChangingConfigurations()&0x1000)!=0,"constant state properties"); state.setAlpha(255); copy.setTintList(ColorStateList.valueOf(Color.GREEN)); b=draw(copy,16,16); check((b.getPixel(2,8)>>>24)==100 && (b.getPixel(2,8)&0xffffff)==0x00ff00,"independent clone state"); b.recycle(); b=draw(state,16,16); check(b.getPixel(2,8)==Color.BLUE,"clone mutations independent"); b.recycle();
        group.setBounds(0,0,8,8); b=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(b); canvas.scale(8,4); group.draw(canvas); check(b.getPixel(20,10)==Color.RED && b.getPixel(50,10)==0,"device scale cache"); b.recycle(); b=draw(group,32,64); check(b.getPixel(10,20)==Color.RED && b.getPixel(28,20)==0,"portrait reraster"); b.recycle();
        Resources.Theme theme=r.newTheme(); theme.applyStyle(id(r,"style","FixtureBase"),true); Drawable themed=r.getDrawable(id(r,"drawable","vector_theme"),theme); b=draw(themed,8,8); check(b.getPixel(3,3)==0xff112233,"theme vector color"); b.recycle(); check(themed.canApplyTheme(),"theme dependency"); theme.applyStyle(id(r,"style","FixtureOverlay"),true); themed.applyTheme(theme); b=draw(themed,8,8); check(b.getPixel(3,3)==0xff445566,"theme reapplication"); b.recycle();
        b=draw(load(r,"vector_trim"),8,8);check(b.getPixel(6,1)==Color.BLACK&&b.getPixel(1,6)==0,"Trimmed closed path fill");b.recycle();
        Drawable clip=load(r,"vector_clip");b=draw(clip,16,24);check(b.getPixel(2,12)==Color.BLACK&&b.getPixel(6,12)==0&&b.getPixel(14,12)==0,"nonuniform clip and even-odd hole");b.recycle();
        clip.setLayoutDirection(1);b=draw(clip,16,24);check(b.getPixel(14,12)==Color.BLACK&&b.getPixel(10,12)==0&&b.getPixel(2,12)==0,"mirrored clip matches painted geometry");b.recycle();
        Drawable scope=load(r,"vector_clip_scope");b=draw(scope,20,20);check(b.getPixel(1,5)==Color.BLUE&&b.getPixel(5,5)==Color.RED&&b.getPixel(9,5)==Color.GREEN,"nested translated clips");check(b.getPixel(5,13)==Color.YELLOW&&b.getPixel(17,1)==Color.BLACK,"nested and root sibling clip restoration");check(b.getPixel(3,17)==Color.MAGENTA&&b.getPixel(15,17)==Color.BLUE,"clip affects later paint only");b.recycle();
        Drawable scopeCopy=scope.getConstantState().newDrawable(r);
        try{Object target=scope.getClass().getMethod("shirazTarget",String.class).invoke(scope,"moving");target.getClass().getMethod("setTranslateX",float.class).invoke(target,0f);}catch(Exception failure){throw new AssertionError(failure);}
        b=draw(scope,20,20);check(b.getPixel(1,5)==Color.RED&&b.getPixel(5,5)==Color.GREEN,"animated group moves clip and path together");b.recycle();b=draw(scopeCopy,20,20);check(b.getPixel(1,5)==Color.BLUE&&b.getPixel(9,5)==Color.GREEN,"constant state independently copies clip graph");b.recycle();
        System.out.println("SHIRAZ_FIXTURE_VECTORS_OK");
    }
}

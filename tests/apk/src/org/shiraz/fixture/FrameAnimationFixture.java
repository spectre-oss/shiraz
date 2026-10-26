// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.Resources;
public final class FrameAnimationFixture {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static final class Owner implements Drawable.Callback {
        Runnable pending;long when;int schedules;
        public void invalidateDrawable(Drawable d){}
        public void scheduleDrawable(Drawable d,Runnable r,long at){pending=r;when=at;schedules++;}
        public void unscheduleDrawable(Drawable d,Runnable r){if(pending==r)pending=null;}
        void tick(){Runnable r=pending;pending=null;check(r!=null,"scheduled frame");r.run();}
    }
    public static void check(Resources resources){
        AnimationDrawable a=(AnimationDrawable)resources.getDrawable(resources.getIdentifier("frame_animation","drawable","org.shiraz.fixture"));
        check(a.getNumberOfFrames()==2&&a.getDuration(1)==40&&a.isOneShot(),"XML frames and timing");
        Owner owner=new Owner();a.setCallback(owner);a.setBounds(0,0,2,2);
        Bitmap pixels=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(pixels);
        a.draw(canvas);check(pixels.getPixel(0,0)==Color.RED,"first frame drawn before start");
        long before=android.os.SystemClock.uptimeMillis();a.start();check(a.isRunning()&&owner.when>=before+20,"first frame deadline");
        int scheduled=owner.schedules;a.start();check(owner.schedules==scheduled,"start is idempotent");
        owner.tick();a.draw(canvas);check(pixels.getPixel(0,0)==Color.BLUE&&!a.isRunning()&&owner.pending==null,"one shot ends on last frame");
        a.setOneShot(false);a.start();owner.tick();owner.tick();check(a.getCurrent()==a.getFrame(0)&&a.isRunning(),"loop wraps");
        a.setVisible(false,false);check(!a.isRunning()&&owner.pending==null,"hide cancels");a.run();check(owner.pending==null,"stale tick ignored");
        a.setVisible(true,false);check(a.isRunning()&&a.getCurrent()==a.getFrame(0),"show resumes looping");
        a.stop();check(!a.isRunning()&&owner.pending==null,"stop cancels");
        try{a.addFrame(new ColorDrawable(0),-1);throw new AssertionError("negative duration");}catch(IllegalArgumentException expected){}
        AnimationDrawable empty=new AnimationDrawable();empty.start();check(!empty.isRunning(),"empty animation");
        pixels.recycle();System.out.println("FRAME_ANIMATION_OK");
    }
}

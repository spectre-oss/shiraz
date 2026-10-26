// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.ImageView;

public final class ImageViewFixture {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static int spec(int size,int mode) { return View.MeasureSpec.makeMeasureSpec(size,mode); }
    private static Bitmap frame(ImageView image,int width,int height) {
        image.measure(spec(width,View.MeasureSpec.EXACTLY),spec(height,View.MeasureSpec.EXACTLY));
        image.layout(0,0,width,height); Bitmap pixels=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        image.draw(new Canvas(pixels)); return pixels;
    }
    private static void row(Bitmap pixels,int y,int... colors) {
        for (int x=0;x<colors.length;x++) if (pixels.getPixel(x,y) != colors[x]) throw new AssertionError("image pixel "+x+","+y+" was "+Integer.toHexString(pixels.getPixel(x,y)));
    }
    public static void checkQueued(Context context) {
        final boolean[] stale={false,false}; ImageView image=new ImageView(context); ColorDrawable first=new ColorDrawable(Color.RED), second=new ColorDrawable(Color.BLUE);
        image.setImageDrawable(first); first.scheduleSelf(new Runnable() { public void run() { stale[0]=true; } },0);
        image.setImageDrawable(second); Runnable task=new Runnable() { public void run() { stale[1]=true; } };
        second.scheduleSelf(task,0); second.unscheduleSelf(task);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() { public void run() {
            check(!stale[0] && !stale[1],"image replacement and explicit unschedule cancel queued callbacks");
            System.out.println("SHIRAZ_FIXTURE_IMAGEVIEW_QUEUE_OK");
        } });
    }
    private static void canvasChecks() {
        Bitmap target=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(target); Paint paint=new Paint(); paint.setColor(Color.RED);
        check(canvas.getSaveCount() == 1,"initial canvas stack"); int saved=canvas.save();
        canvas.translate(2,1); canvas.scale(2,3); canvas.clipRect(0,0,2,2); canvas.drawRect(0,0,1,1,paint);
        check(target.getPixel(2,1) == Color.RED && target.getPixel(3,3) == Color.RED && target.getPixel(4,1) == 0,"translated nonuniform rectangle");
        canvas.translate(10,10); canvas.drawColor(Color.BLUE);
        check(target.getPixel(2,1) == Color.BLUE && target.getPixel(5,6) == Color.BLUE && target.getPixel(6,6) == 0,"device clip remains fixed after translation; drawColor ignores matrix");
        canvas.restoreToCount(saved); check(canvas.getSaveCount() == 1,"canvas restore count");
        paint.setColor(Color.GREEN); canvas.drawRect(0,0,1,1,paint); check(target.getPixel(0,0) == Color.GREEN,"restore resets transform and clip");
        try { canvas.restore(); throw new AssertionError("restore underflow accepted"); } catch (IllegalStateException expected) { }
        try { canvas.scale(-1,1); throw new AssertionError("mirroring accepted"); } catch (UnsupportedOperationException expected) { }
        try { canvas.translate(Float.NaN,0); throw new AssertionError("nonfinite translate accepted"); } catch (IllegalArgumentException expected) { }
        canvas.drawRect(1,0,2,1,paint); check(target.getPixel(1,0) == Color.GREEN,"failed transforms leave state intact");
        saved=canvas.save(); canvas.scale(2,2,2,2); canvas.drawRect(2,2,3,3,paint); canvas.restoreToCount(saved);
        check(target.getPixel(2,2) == Color.GREEN && target.getPixel(3,3) == Color.GREEN,"scale around pivot");
        saved=canvas.save(); check(!canvas.clipRect(2,2,1,1),"inverted clip is empty"); canvas.drawColor(Color.WHITE); canvas.restoreToCount(saved);
        check(target.getPixel(2,2) == Color.GREEN,"empty clip preserves target");
        for (int n=0;n<256;n++) canvas.save();
        try { canvas.save(); throw new AssertionError("unbounded save stack"); } catch (IllegalStateException expected) { }
        canvas.restoreToCount(1);
    }
    private static void matrices(Context context){
        Bitmap src=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888);src.setPixel(0,0,Color.RED);src.setPixel(1,0,Color.BLUE);
        ImageView view=new ImageView(context);BitmapDrawable d=new BitmapDrawable(src);d.setFilterBitmap(false);view.setImageDrawable(d);view.setScaleType(ImageView.ScaleType.MATRIX);view.setPadding(1,1,0,0);
        Matrix matrix=new Matrix();matrix.setScale(2,2);matrix.postTranslate(1,0);view.setImageMatrix(matrix);matrix.reset();Bitmap pixels=frame(view,8,5);try{row(pixels,1,0,0,Color.RED,Color.RED,Color.BLUE,Color.BLUE,0,0);check(pixels.getPixel(2,0)==0,"Matrix includes padding");}finally{pixels.recycle();}
        Matrix read=view.getImageMatrix();read.reset();pixels=frame(view,8,5);try{check(pixels.getPixel(4,1)==Color.BLUE,"Getter does not mutate stored matrix");}finally{pixels.recycle();}
        matrix.setRotate(90);matrix.postTranslate(1,0);view.setImageMatrix(matrix);pixels=frame(view,5,5);try{check(pixels.getPixel(1,1)==Color.RED&&pixels.getPixel(1,2)==Color.BLUE,"Rotated matrix image pixels");}finally{pixels.recycle();}
        matrix.setScale(0,0);view.setImageMatrix(matrix);pixels=frame(view,4,4);try{check(pixels.getPixel(1,1)==0,"Collapsed animation image draws nothing");}finally{pixels.recycle();}
        view.setImageMatrix(null);pixels=frame(view,4,4);try{check(pixels.getPixel(1,1)==Color.RED&&pixels.getPixel(2,1)==Color.BLUE,"Null resets image transform");}finally{pixels.recycle();}
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);pixels=frame(view,5,5);pixels.recycle();float[] point={0,0,2,1};view.getImageMatrix().mapPoints(point);check(point[0]==0&&point[1]==1&&point[2]==4&&point[3]==3,"Automatic fit matrix reported without padding");
        Bitmap cropTarget=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(cropTarget);Matrix turn=new Matrix();turn.setRotate(90);turn.postTranslate(2,0);c.concat(turn);c.drawBitmap(src,new Rect(1,0,2,1),new RectF(0,0,1,1),null);check(cropTarget.getPixel(1,0)==Color.BLUE&&cropTarget.getPixel(1,1)==0,"Affine bitmap crop uses selected source pixels");}finally{cropTarget.recycle();}
        Matrix perspective=new Matrix();perspective.setValues(new float[]{1,0,0,0,1,0,.1f,0,1});try{view.setImageMatrix(perspective);throw new AssertionError("Perspective silently accepted");}catch(UnsupportedOperationException expected){}
        src.recycle();
    }
    public static void check(Context context) {
        matrices(context);
        android.widget.ImageButton button=new android.widget.ImageButton(context,null,0);
        check(button.isFocusable()&&button.getAccessibilityClassName().equals("android.widget.ImageButton"),"Image button focus/accessibility role");
        final int[] buttonClicks={0};button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){buttonClicks[0]++;}});
        button.setImageDrawable(new ColorDrawable(Color.RED));Bitmap buttonFrame=frame(button,8,8);try{check(buttonFrame.getPixel(4,4)==Color.RED,"Image button renders source");}finally{buttonFrame.recycle();}
        check(button.requestFocus(),"Image button keyboard focus");button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));check(button.isPressed(),"Image button pressed feedback");button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));check(buttonClicks[0]==1&&!button.isPressed(),"Image button keyboard click");
        MotionEvent bd=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,4,4,0),bu=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,4,4,0);try{button.dispatchTouchEvent(bd);button.dispatchTouchEvent(bu);}finally{bd.recycle();bu.recycle();}check(buttonClicks[0]==2,"Image button pointer click");button.setEnabled(false);button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));check(buttonClicks[0]==2,"Disabled image button ignores activation");

        ImageView tinted=new ImageView(context);tinted.setScaleType(ImageView.ScaleType.FIT_XY);
        android.content.res.ColorStateList colors=new android.content.res.ColorStateList(new int[][]{{16842919},{}},new int[]{Color.RED,Color.GREEN});
        tinted.setImageTintList(colors);tinted.setImageTintMode(PorterDuff.Mode.SRC_IN);
        Bitmap white=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);white.eraseColor(Color.WHITE);tinted.setImageBitmap(white);
        row(frame(tinted,1,1),0,Color.GREEN);tinted.setPressed(true);row(frame(tinted,1,1),0,Color.RED);
        tinted.setImageDrawable(new ColorDrawable(Color.WHITE));row(frame(tinted,1,1),0,Color.RED);
        tinted.setColorFilter(Color.BLUE,PorterDuff.Mode.SRC_IN);row(frame(tinted,1,1),0,Color.BLUE);
        tinted.clearColorFilter();row(frame(tinted,1,1),0,Color.RED);
        tinted.setImageTintMode(null);row(frame(tinted,1,1),0,Color.WHITE);
        tinted.setImageTintMode(PorterDuff.Mode.SRC_IN);tinted.setImageTintList(null);row(frame(tinted,1,1),0,Color.WHITE);
        check(tinted.getImageTintList()==null&&tinted.getImageTintMode()==PorterDuff.Mode.SRC_IN,"image tint getters/clear");
        canvasChecks();
        Bitmap source=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888); source.setPixel(0,0,Color.RED); source.setPixel(1,0,Color.BLUE);
        BitmapDrawable drawable=new BitmapDrawable(source); drawable.setFilterBitmap(false);
        ImageView image=new ImageView(context); image.setImageDrawable(drawable);
        check(image.getDrawable() == drawable && drawable.getCallback() == image && image.getScaleType() == ImageView.ScaleType.FIT_CENTER,"image ownership/default fit");
        Bitmap pixels=frame(image,4,4); row(pixels,0,0,0,0,0); row(pixels,1,Color.RED,Color.RED,Color.BLUE,Color.BLUE); row(pixels,3,0,0,0,0);
        image.setScaleType(ImageView.ScaleType.FIT_START); pixels=frame(image,4,4); row(pixels,0,Color.RED,Color.RED,Color.BLUE,Color.BLUE); row(pixels,2,0,0,0,0);
        image.setScaleType(ImageView.ScaleType.FIT_END); pixels=frame(image,4,4); row(pixels,0,0,0,0,0); row(pixels,3,Color.RED,Color.RED,Color.BLUE,Color.BLUE);
        image.setScaleType(ImageView.ScaleType.FIT_XY); pixels=frame(image,4,4); row(pixels,0,Color.RED,Color.RED,Color.BLUE,Color.BLUE); row(pixels,3,Color.RED,Color.RED,Color.BLUE,Color.BLUE);
        image.setScaleType(ImageView.ScaleType.CENTER); pixels=frame(image,4,4); row(pixels,2,0,Color.RED,Color.BLUE,0); row(pixels,1,0,0,0,0);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE); pixels=frame(image,4,4); row(pixels,2,0,Color.RED,Color.BLUE,0);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP); pixels=frame(image,4,4); row(pixels,0,Color.RED,Color.RED,Color.BLUE,Color.BLUE); row(pixels,3,Color.RED,Color.RED,Color.BLUE,Color.BLUE);
        image.setPadding(1,1,1,1); image.setCropToPadding(false); pixels=frame(image,6,6);
        row(pixels,2,Color.RED,Color.RED,Color.RED,Color.BLUE,Color.BLUE,Color.BLUE);
        image.setCropToPadding(true); pixels=frame(image,6,6); row(pixels,2,0,Color.RED,Color.RED,Color.BLUE,Color.BLUE,0);
        check(pixels.getPixel(2,0) == 0 && pixels.getPixel(2,5) == 0,"crop padding vertical edges");
        image.setPadding(0,0,0,0); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        for (int n=0;n<10;n++) {
            pixels=frame(image,4,8); row(pixels,3,Color.RED,Color.RED,Color.BLUE,Color.BLUE); row(pixels,0,0,0,0,0);
            pixels=frame(image,8,4); row(pixels,0,Color.RED,Color.RED,Color.RED,Color.RED,Color.BLUE,Color.BLUE,Color.BLUE,Color.BLUE);
            check(image.getMeasuredWidth() == 8 && image.getMeasuredHeight() == 4,"landscape measurement");
        }
        final int[] clicks={0}; image.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { clicks[0]++; } });
        MotionEvent down=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,7,3,0), up=MotionEvent.obtain(1,2,MotionEvent.ACTION_UP,7,3,0);
        try { check(image.dispatchTouchEvent(down) && image.dispatchTouchEvent(up) && clicks[0] == 1,"touch in resized image bounds"); } finally { down.recycle(); up.recycle(); }

        check(image.getScaleType() == ImageView.ScaleType.FIT_CENTER,"unsupported scale leaves prior mode");
        if (context.getResources() != null) try { image.setImageResource(123); throw new AssertionError("missing image resource silently ignored"); } catch (android.content.res.Resources.NotFoundException expected) { }
        check(image.getDrawable() == drawable,"unsupported resource preserves image");
        image.setImageAlpha(128); pixels=frame(image,4,4); check(pixels.getPixel(0,1) == 0x80ff0000,"image alpha rendering");
        ColorDrawable replacement=new ColorDrawable(Color.GREEN); image.setImageDrawable(replacement);
        check(drawable.getCallback() == null && replacement.getAlpha() == 128,"replacement detaches and applies explicit alpha");
        image.setVisibility(View.INVISIBLE); check(!replacement.isVisible(),"image visibility");
        pixels=frame(image,4,4); row(pixels,0,0,0,0,0); image.setVisibility(View.VISIBLE);
        image.setImageResource(0); check(image.getDrawable() == null && replacement.getCallback() == null,"clear image resource");
        ImageView intrinsic=new ImageView(context); intrinsic.setImageDrawable(new BitmapDrawable(source)); intrinsic.setPadding(2,3,4,5);
        intrinsic.measure(spec(100,View.MeasureSpec.AT_MOST),spec(100,View.MeasureSpec.AT_MOST));
        check(intrinsic.getMeasuredWidth() == 8 && intrinsic.getMeasuredHeight() == 9,"intrinsic size plus padding");
        intrinsic.measure(spec(7,View.MeasureSpec.AT_MOST),spec(8,View.MeasureSpec.AT_MOST));
        check((intrinsic.getMeasuredWidthAndState() & View.MEASURED_STATE_TOO_SMALL) != 0 && (intrinsic.getMeasuredState() & (View.MEASURED_STATE_TOO_SMALL >> View.MEASURED_HEIGHT_STATE_SHIFT)) != 0,"measured too-small states");
        intrinsic.setPadding(0,0,0,0); intrinsic.setAdjustViewBounds(true);
        intrinsic.measure(spec(20,View.MeasureSpec.EXACTLY),spec(100,View.MeasureSpec.AT_MOST)); check(intrinsic.getMeasuredWidth() == 20 && intrinsic.getMeasuredHeight() == 10,"adjust height to fixed width");
        intrinsic.measure(spec(100,View.MeasureSpec.AT_MOST),spec(20,View.MeasureSpec.EXACTLY)); check(intrinsic.getMeasuredWidth() == 40 && intrinsic.getMeasuredHeight() == 20,"adjust width to fixed height");
        intrinsic.setMaxWidth(10); intrinsic.measure(spec(100,View.MeasureSpec.AT_MOST),spec(20,View.MeasureSpec.EXACTLY)); check(intrinsic.getMeasuredWidth() == 10,"adjust max width");
        intrinsic.setMaxWidth(Integer.MAX_VALUE); intrinsic.setMaxHeight(4); intrinsic.measure(spec(20,View.MeasureSpec.AT_MOST),spec(20,View.MeasureSpec.AT_MOST)); check(intrinsic.getMeasuredWidth() == 2 && intrinsic.getMeasuredHeight() == 1,"adjust retains intrinsic with loose bounds");
        intrinsic.setPadding(Integer.MAX_VALUE,0,Integer.MAX_VALUE,0); pixels=frame(intrinsic,4,4); row(pixels,0,0,0,0,0);
        ImageView negative=new ImageView(context);BitmapDrawable negativeImage=new BitmapDrawable(source);negativeImage.setFilterBitmap(false);negative.setImageDrawable(negativeImage);
        negative.setPadding(-1,0,0,0);negative.measure(spec(0,View.MeasureSpec.UNSPECIFIED),spec(0,View.MeasureSpec.UNSPECIFIED));
        check(negative.getPaddingLeft()==-1&&negative.getMeasuredWidth()==1&&negative.getMeasuredHeight()==1,"signed intrinsic padding");
        negative.setScaleType(ImageView.ScaleType.FIT_XY);pixels=frame(negative,3,1);row(pixels,0,Color.RED,Color.BLUE,Color.BLUE);
        negative.setPadding(-1,1,1,-1);negative.setCropToPadding(true);pixels=frame(negative,4,4);row(pixels,0,0,0,0,0);row(pixels,1,Color.RED,Color.BLUE,Color.BLUE,0);row(pixels,3,Color.RED,Color.BLUE,Color.BLUE,0);
        negative.setPadding(-1,0,0,0);negative.setAdjustViewBounds(true);negative.measure(spec(9,View.MeasureSpec.EXACTLY),spec(100,View.MeasureSpec.AT_MOST));
        check(negative.getMeasuredWidth()==9&&negative.getMeasuredHeight()==5,"signed padding aspect ratio");
        negative.setPadding(Integer.MIN_VALUE,0,Integer.MAX_VALUE,0);negative.setAdjustViewBounds(false);negative.measure(spec(0,View.MeasureSpec.UNSPECIFIED),spec(0,View.MeasureSpec.UNSPECIFIED));
        check(negative.getMeasuredWidth()==1,"padding sum avoids signed overflow");
        negative.setPadding(0,0,Integer.MIN_VALUE,0);negative.setImageDrawable(new ColorDrawable(Color.GREEN));negative.setCropToPadding(true);pixels=frame(negative,2,2);row(pixels,0,Color.GREEN,Color.GREEN);
        ImageView untouchedAlpha=new ImageView(context); ColorDrawable translucent=new ColorDrawable(Color.RED); translucent.setAlpha(64); int originalAlpha=translucent.getAlpha(); untouchedAlpha.setImageDrawable(translucent); check(translucent.getAlpha() == originalAlpha,"default image alpha retains drawable alpha");
        ImageView wrapped=new ImageView(context); wrapped.setImageBitmap(source);
        check(((BitmapDrawable)wrapped.getDrawable()).getBitmap() == source,"setImageBitmap retains source");
        wrapped.setImageBitmap(null); check(wrapped.getDrawable() == null,"setImageBitmap clears source");
        ImageView failing=new ImageView(context); failing.layout(0,0,4,4);
        failing.setImageDrawable(new ColorDrawable(Color.RED) { public void draw(Canvas value) { value.translate(100,100); throw new IllegalStateException("drawable draw failure"); } });
        Bitmap afterFailure=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888); Canvas restored=new Canvas(afterFailure);
        try { failing.draw(restored); throw new AssertionError("drawable failure swallowed"); } catch (IllegalStateException expected) { check("drawable draw failure".equals(expected.getMessage()),"original drawable exception"); }
        Paint green=new Paint(); green.setColor(Color.GREEN); restored.drawRect(0,0,1,1,green);
        check(restored.getSaveCount() == 1 && afterFailure.getPixel(0,0) == Color.GREEN,"drawable failure restores caller canvas");
        System.out.println("SHIRAZ_FIXTURE_IMAGEVIEW_OK");
    }
}

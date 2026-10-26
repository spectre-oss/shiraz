// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;

/** Compiled against Google's Android SDK, with no Shiraz runtime imports. */
public final class FixtureActivity extends Activity {
    private FixtureView fixtureView;
    private DialogFixture.Live dialogFixture;
    private TreeObserverFixture.Live treeLive;
    private ToastFixture toast;
    private final AsyncFixture async = new AsyncFixture();
    private final KeyboardFixture keyboard = new KeyboardFixture();
    private final InputFixture input = new InputFixture();
    private final PreferencesFixture preferences = new PreferencesFixture();
    private boolean mainTaskRan, hidden;
    private String events = "";
    private void event(String name) { events += (events.isEmpty() ? "" : ",") + name; }
    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if("1".equals(System.getenv("SHIRAZ_FIXTURE_LINGER"))){
            Thread worker=new Thread(new Runnable(){public void run(){try{Thread.sleep(60000);}catch(InterruptedException ignored){}}},"fixture-lingering-worker");
            worker.setDaemon(false);worker.start();System.out.println("SHIRAZ_FIXTURE_LINGERING_WORKER_STARTED");
        }
        AttachFixture.observe(getWindow().getDecorView());InsetsFixture.observe(getWindow().getDecorView());
        try{NinePatchFixture.check(getResources());}catch(Exception e){throw new RuntimeException(e);}GradientFixture.check();MatrixFixture.check();XfermodeFixture.check();WrapperFixture.check();RegionFixture.check();VariationFixture.check();ProgressFixture.check(this);ProgressFixture.xml(this);ShadowFixture.check();ShadowFixture.xml(this);SpacingFixture.check();SpacingFixture.xml(this);GradientFixture.xml(getResources());ObservableFixture.check();ParentAttachmentFixture.check(this);AccessibilityManagerFixture.service(this);AutofillMetadataFixture.check(this);AutofillMetadataFixture.service(this);AutofillMetadataFixture.xml(this);ScrollFixture.check(this);TranslationFixture.check(this);TranslationFixture.xml(this);CornerFixture.check();ElevationFixture.check(this);ElevationFixture.xml(this);OutlineFixture.check(this);OutlineFixture.xml(this);LayoutTransitionFixture.check(this);ViewTransitionFixture.check(this);ObjectAnimatorFixture.install();ValueAnimatorFixture.install();AlphaFixture.check(this);SelectorFixture.check(getResources());PropertyFixture.check();CompoundButtonFixture.check(this);BreakStrategyFixture.check(this);EllipsisFixture.check(this);RippleFixture.immediate(this);EditTextFixture.check(this);EditTextFixture.xml(this);EditableFixture.check();TransformationFixture.check(this);GestureFixture.immediate(this);MultiTouchFixture.check(this);ScrollerFixture.immediate(this);ViewConfigurationFixture.check(this);InterpolatorFixture.check(this);SurfaceFixture.install(getWindow().getDecorView());AccessibilityFixture.install(getWindow().getDecorView());AnimationFixture.install(getWindow().getDecorView());TitleFixture.install(this);ActivityIdentityFixture.check(this);FragmentFixture.install(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if (!FixtureApplication.created || !(getApplication() instanceof FixtureApplication) || savedState != null)
            throw new AssertionError("Application/activity startup order");
        Bundle values = new Bundle(); values.putString("message", "ready"); values.putInt("count", 7);
        if (!"ready".equals(values.getString("message")) || values.getInt("count") != 7)
            throw new AssertionError("Bundle values");
        Bitmap scratch = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scratch); canvas.drawColor(0xff0000ff);
        Paint overlay = new Paint(); overlay.setColor(0x80ff0000);
        canvas.drawRect(-10, -10, 1, 1, overlay);
        int[] copy = new int[8]; scratch.getPixels(copy, 1, 3, 0, 0, 2, 2);
        if (copy[0] != 0 || copy[1] != 0xff80007f || copy[2] != 0xff0000ff || copy[4] != 0xff0000ff || copy[5] != 0xff0000ff)
            throw new AssertionError("Canvas alpha/clip or Bitmap stride");
        try { scratch.getPixel(-1, 0); throw new AssertionError("Bitmap bounds accepted"); }
        catch (IllegalArgumentException expected) { }
        scratch.recycle();
        try { scratch.getPixel(0, 0); throw new AssertionError("Recycled bitmap accepted"); }
        catch (IllegalStateException expected) { }
        InputFixture.stateChecks(this);
        preferences.start(this);
        ColorFixture.check(); TextUtilsFixture.check(); TextFixture.check(); toast = new ToastFixture(this);
        try { async.start(); } catch (Exception error) { throw new AssertionError(error); }
        event("create");
        new android.os.Handler().post(new Runnable() { @Override public void run() {
            if (!events.equals("create,start,resume")) throw new AssertionError("Main callback ran before resume");
            mainTaskRan = true;
        } });
        if (mainTaskRan) throw new AssertionError("Handler.post ran inline");
        View view = fixtureView = new FixtureView(this); view.setId(42);
        android.view.LayoutInflater inflater=getLayoutInflater();inflater.setFactory2(new android.view.LayoutInflater.Factory2(){public View onCreateView(String name,Context context,android.util.AttributeSet attrs){return onCreateView(null,name,context,attrs);}public View onCreateView(View parent,String name,Context context,android.util.AttributeSet attrs){return name.equals("fixture_surface")?fixtureView:null;}});
        android.widget.FrameLayout frame=(android.widget.FrameLayout)inflater.inflate(R.layout.inflater_live,null,false);setContentView(frame);treeLive=new TreeObserverFixture.Live(frame);
        if (InputFixture.ENABLED) { input.attach(view); keyboard.attach(view); }
        if("1".equals(System.getenv("SHIRAZ_FIXTURE_DIALOG"))){dialogFixture=new DialogFixture.Live(this);dialogFixture.show();}
        if (findViewById(42) != view) throw new AssertionError("Content view lookup");
    }
    @Override protected void onStart() { super.onStart(); event("start"); }
    @Override protected void onResume() { super.onResume(); event("resume"); }
    @Override protected void onPause() { event("pause"); super.onPause(); }
    @Override protected void onStop() { event("stop"); super.onStop(); }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if(level!=android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN||!events.equals("create,start,resume,pause,stop")) throw new AssertionError("Activity hidden callback order");
        hidden=true;
        System.out.println("SHIRAZ_FIXTURE_ACTIVITY_HIDDEN_OK");
    }
    @Override protected void onDestroy() {
        if(!hidden) throw new AssertionError("Activity missing UI hidden notification");
        event("destroy"); super.onDestroy();
        if(dialogFixture!=null){if(dialogFixture.isShowing()||dialogFixture.clicks!=1||dialogFixture.draws<1||dialogFixture.view.isAttachedToWindow())throw new AssertionError("Live APK modal composition/input/dismissal");System.out.println("SHIRAZ_FIXTURE_DIALOG_APK_OK");}
        if (!events.equals("create,start,resume,pause,stop,destroy")) throw new AssertionError("Lifecycle: " + events);
        if (System.getenv("SHIRAZ_FIXTURE_ASYNC_FAIL") == null && System.getenv("SHIRAZ_FIXTURE_CALLBACK_FAIL") == null && (!mainTaskRan || fixtureView.draws < 2 || !fixtureView.ready))
            throw new AssertionError("Queued callbacks did not redraw the live surface");
        if (InputFixture.ENABLED && System.getenv("SHIRAZ_FIXTURE_ASYNC_FAIL") == null && System.getenv("SHIRAZ_FIXTURE_CALLBACK_FAIL") == null && System.getenv("SHIRAZ_FIXTURE_POINTER_FAIL") == null) input.verify(fixtureView);
        if (InputFixture.ENABLED && System.getenv("SHIRAZ_FIXTURE_ASYNC_FAIL") == null && System.getenv("SHIRAZ_FIXTURE_CALLBACK_FAIL") == null && System.getenv("SHIRAZ_FIXTURE_POINTER_FAIL") == null && System.getenv("SHIRAZ_FIXTURE_KEY_FAIL") == null && System.getenv("SHIRAZ_FIXTURE_TEXT_FAIL") == null && System.getenv("SHIRAZ_FIXTURE_BAD_TEXT") == null) keyboard.verify();
        if(System.getenv("SHIRAZ_FIXTURE_ASYNC_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_CALLBACK_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_POINTER_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_KEY_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_TEXT_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_BAD_TEXT")==null)treeLive.verify();
        if(System.getenv("SHIRAZ_FIXTURE_ASYNC_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_CALLBACK_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_POINTER_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_KEY_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_TEXT_FAIL")==null&&System.getenv("SHIRAZ_FIXTURE_BAD_TEXT")==null)ServiceFixture.verify(this);
        async.verify();
        preferences.verify();
        async.teardown(this);
        toast.verify();
        System.out.println("SHIRAZ_FIXTURE_LIFECYCLE_OK");
    }
    private final class FixtureView extends View {
        int draws;
        boolean ready;
        FixtureView(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            if (System.getenv("SHIRAZ_FIXTURE_CALLBACK_FAIL") != null) throw new AssertionError("SHIRAZ_FIXTURE_EXPECTED_CALLBACK_FAILURE");
            if (getParent() == null || getMeasuredWidth() != getWidth() || getMeasuredHeight() != getHeight()) throw new AssertionError("Root hierarchy must measure before drawing");
            if(getWindowSystemUiVisibility()!=0x2100)throw new AssertionError("live window UI request aggregation");
            treeLive.beforeViewPaint();
            TitleFixture.frame(FixtureActivity.this,draws);++draws;
            if (draws == 1) {
                toast.start();
                if (InputFixture.ENABLED) post(new Runnable() { @Override public void run() { InputFixture.inject(); } });
                postDelayed(new Runnable() { @Override public void run() { ready = true; invalidate(); } }, 1);
                canvas.drawColor(0xffaabbcc); return;
            }
            if (!ready) throw new AssertionError("Unexpected redraw without callback");
            if (getWidth() <= 0 || getHeight() <= 0 || canvas.getWidth() != getWidth() || canvas.getHeight() != getHeight())
                throw new AssertionError("View/Canvas geometry");
            canvas.drawColor(InputFixture.ENABLED && input.clicks != 2 ? 0xff203040 : 0xff102030);
            Paint paint = new Paint(); paint.setColor(0x80ff0000);
            canvas.drawRect(-10, -10, getWidth() / 2, getHeight() / 2, paint);
            System.out.println("SHIRAZ_FIXTURE_DRAW_OK");
        }
    }
}

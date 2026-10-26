// SPDX-License-Identifier: GPL-3.0-only
package android.view;
/** Display and app-owned window management. */
public interface WindowManager extends ViewManager {
    Display getDefaultDisplay();
    void addView(View view,ViewGroup.LayoutParams params);
    void updateViewLayout(View view,ViewGroup.LayoutParams params);
    void removeView(View view);
    void removeViewImmediate(View view);
    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_DIM_BEHIND=2,FLAG_NOT_FOCUSABLE=8,FLAG_ALT_FOCUSABLE_IM=0x20000,FLAG_NOT_TOUCHABLE=16,FLAG_KEEP_SCREEN_ON=128,FLAG_TRANSLUCENT_STATUS=0x04000000,FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS=0x80000000;
        public int x,y,gravity=Gravity.CENTER,flags,windowAnimations,softInputMode,type=2,format=-1,preferredDisplayModeId;
        public android.os.IBinder token;private CharSequence title="";
        public void setTitle(CharSequence value){title=value;}public CharSequence getTitle(){return title;}
        public float dimAmount=1f,alpha=1f;
        /** Retained per window; desktop display brightness remains user-controlled. */
        public float screenBrightness=-1f,buttonBrightness=-1f;
        public LayoutParams(){super(MATCH_PARENT,MATCH_PARENT);}
        public LayoutParams(int width,int height){super(width,height);}
        public LayoutParams(LayoutParams source){this();copyFrom(source);}
        public int copyFrom(LayoutParams source){if(source==null)throw new NullPointerException("attributes");int changes=screenBrightness==source.screenBrightness&&buttonBrightness==source.buttonBrightness&&token==source.token&&java.util.Objects.equals(title,source.title)&&width==source.width&&height==source.height&&x==source.x&&y==source.y&&gravity==source.gravity&&flags==source.flags&&windowAnimations==source.windowAnimations&&softInputMode==source.softInputMode&&type==source.type&&format==source.format&&preferredDisplayModeId==source.preferredDisplayModeId&&dimAmount==source.dimAmount&&alpha==source.alpha?0:-1;screenBrightness=source.screenBrightness;buttonBrightness=source.buttonBrightness;token=source.token;title=source.title;width=source.width;height=source.height;x=source.x;y=source.y;gravity=source.gravity;flags=source.flags;windowAnimations=source.windowAnimations;softInputMode=source.softInputMode;type=source.type;format=source.format;preferredDisplayModeId=source.preferredDisplayModeId;dimAmount=source.dimAmount;alpha=source.alpha;return changes;}
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

/**
 * Minimal action bar state. Shiraz does not draw an action bar yet: title,
 * subtitle, display flags and the custom view are kept so apps can read them
 * back, and the custom view is inflated so its children can be looked up.
 * Members that are not declared here fail with NoSuchMethodError.
 */
public abstract class ActionBar {
    public static final int DISPLAY_USE_LOGO=1,DISPLAY_SHOW_HOME=2,DISPLAY_HOME_AS_UP=4,DISPLAY_SHOW_TITLE=8,DISPLAY_SHOW_CUSTOM=16;

    public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
        public int gravity;
        public LayoutParams(int width,int height){super(width,height);}
        public LayoutParams(int width,int height,int gravity){super(width,height);this.gravity=gravity;}
    }

    public abstract void setCustomView(View view);
    public abstract void setCustomView(int resource);
    public abstract View getCustomView();
    public abstract void setTitle(CharSequence value);
    public abstract void setTitle(int resource);
    public abstract CharSequence getTitle();
    public abstract void setSubtitle(CharSequence value);
    public abstract void setSubtitle(int resource);
    public abstract CharSequence getSubtitle();
    public abstract void setDisplayOptions(int options);
    public abstract void setDisplayOptions(int options,int mask);
    public abstract int getDisplayOptions();
    public void setDisplayHomeAsUpEnabled(boolean value){setDisplayOptions(value?DISPLAY_HOME_AS_UP:0,DISPLAY_HOME_AS_UP);}
    public void setDisplayShowCustomEnabled(boolean value){setDisplayOptions(value?DISPLAY_SHOW_CUSTOM:0,DISPLAY_SHOW_CUSTOM);}
    public void setDisplayShowTitleEnabled(boolean value){setDisplayOptions(value?DISPLAY_SHOW_TITLE:0,DISPLAY_SHOW_TITLE);}
    public void setDisplayShowHomeEnabled(boolean value){setDisplayOptions(value?DISPLAY_SHOW_HOME:0,DISPLAY_SHOW_HOME);}
    public void setDisplayUseLogoEnabled(boolean value){setDisplayOptions(value?DISPLAY_USE_LOGO:0,DISPLAY_USE_LOGO);}
    public void setHomeButtonEnabled(boolean value){}
    public abstract void show();
    public abstract void hide();
    public abstract boolean isShowing();
    public int getHeight(){return 0;}


    static final class Impl extends ActionBar {
        private final Activity activity;
        private View custom;
        private CharSequence title,subtitle;
        private int options=DISPLAY_SHOW_HOME|DISPLAY_SHOW_TITLE;
        private boolean showing=true;
        private final android.widget.Toolbar bar;
        Impl(Activity activity){this.activity=activity;title=activity.getTitle();bar=activity.getWindow().shirazActionBarView();}
        @Override public void setCustomView(View view){custom=view;}
        @Override public void setCustomView(int resource){Context context=activity;custom=LayoutInflater.from(context).inflate(resource,null,false);}
        @Override public View getCustomView(){return custom;}
        @Override public void setTitle(CharSequence value){title=value;activity.setTitle(value);}
        @Override public void setTitle(int resource){setTitle(activity.getString(resource));}
        @Override public CharSequence getTitle(){return title;}
        @Override public void setSubtitle(CharSequence value){subtitle=value;if(bar!=null)bar.setSubtitle(value);}
        @Override public void setSubtitle(int resource){setSubtitle(activity.getString(resource));}
        @Override public CharSequence getSubtitle(){return subtitle;}
        @Override public void setDisplayOptions(int value){options=value;}
        @Override public void setDisplayOptions(int value,int mask){options=(options&~mask)|(value&mask);}
        @Override public int getDisplayOptions(){return options;}
        @Override public void show(){showing=true;activity.getWindow().shirazSetActionBarShown(true);}
        @Override public void hide(){showing=false;activity.getWindow().shirazSetActionBarShown(false);}
        @Override public boolean isShowing(){return showing;}
        @Override public int getHeight(){return bar==null||!showing?0:bar.getHeight();}
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

/**
 * Minimal Toolbar: navigation button, title and subtitle in a row, followed
 * by children declared in XML. No menu items or overflow are drawn. Android's
 * Toolbar extends ViewGroup; this one is a horizontal LinearLayout.
 */
public class Toolbar extends LinearLayout {
    private final ImageButton navigation;
    private final TextView title,subtitle;
    private final LinearLayout titles;

    public Toolbar(Context context){this(context,null);}
    public Toolbar(Context context,AttributeSet attrs){this(context,attrs,0);}
    public Toolbar(Context context,AttributeSet attrs,int style){this(context,attrs,style,0);}
    public Toolbar(Context context,AttributeSet attrs,int style,int resource){
        super(context,attrs,style,resource);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        float density=context.getResources().getDisplayMetrics().density;
        navigation=new ImageButton(context);navigation.setVisibility(GONE);navigation.setBackground(null);
        addView(navigation,new LinearLayout.LayoutParams((int)(56*density),(int)(56*density)));
        titles=new LinearLayout(context);titles.setOrientation(VERTICAL);titles.setPadding((int)(16*density),0,(int)(16*density),0);
        title=new TextView(context);title.setTextSize(20);title.setSingleLine(true);title.setVisibility(GONE);
        subtitle=new TextView(context);subtitle.setTextSize(14);subtitle.setSingleLine(true);subtitle.setVisibility(GONE);
        titles.addView(title);titles.addView(subtitle);
        addView(titles,new LinearLayout.LayoutParams(-2,-2));
        if(attrs!=null){
            shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs);
            if(a.has("title"))setTitle(a.text("title"));
            if(a.has("subtitle"))setSubtitle(a.text("subtitle"));
        }
    }
    public void setTitle(CharSequence value){title.setText(value);title.setVisibility(value==null||value.length()==0?GONE:VISIBLE);}
    public void setTitle(int resource){setTitle(getContext().getText(resource));}
    public CharSequence getTitle(){return title.getText();}
    public void setSubtitle(CharSequence value){subtitle.setText(value);subtitle.setVisibility(value==null||value.length()==0?GONE:VISIBLE);}
    public void setSubtitle(int resource){setSubtitle(getContext().getText(resource));}
    public CharSequence getSubtitle(){return subtitle.getText();}
    public void setTitleTextColor(int color){title.setTextColor(color);}
    public void setSubtitleTextColor(int color){subtitle.setTextColor(color);}
    public void setNavigationIcon(Drawable icon){navigation.setImageDrawable(icon);navigation.setVisibility(icon==null?GONE:VISIBLE);}
    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(android.view.MenuItem item);
    }

    public static class LayoutParams extends android.app.ActionBar.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(WRAP_CONTENT, WRAP_CONTENT);
            if (attrs != null) {
                android.view.ViewGroup.MarginLayoutParams mlp =
                        new android.view.ViewGroup.MarginLayoutParams(c, attrs);
                this.width = mlp.width;
                this.height = mlp.height;
                this.leftMargin = mlp.leftMargin;
                this.topMargin = mlp.topMargin;
                this.rightMargin = mlp.rightMargin;
                this.bottomMargin = mlp.bottomMargin;
                shiraz.runtime.XmlAttributes a = new shiraz.runtime.XmlAttributes(c, attrs);
                this.gravity = a.integer("layout_gravity", Gravity.NO_GRAVITY);
            }
        }

        public LayoutParams(int width, int height) {
            super(width, height, Gravity.START | Gravity.CENTER_VERTICAL);
        }

        public LayoutParams(int width, int height, int gravity) {
            super(width, height, gravity);
        }

        public LayoutParams(int gravity) {
            this(WRAP_CONTENT, MATCH_PARENT, gravity);
        }

        public LayoutParams(LayoutParams source) {
            super(source.width, source.height, source.gravity);
            setMargins(source.leftMargin, source.topMargin, source.rightMargin, source.bottomMargin);
        }

        public LayoutParams(android.app.ActionBar.LayoutParams source) {
            super(source.width, source.height, source.gravity);
            setMargins(source.leftMargin, source.topMargin, source.rightMargin, source.bottomMargin);
        }

        public LayoutParams(android.view.ViewGroup.MarginLayoutParams source) {
            super(source.width, source.height);
            setMargins(source.leftMargin, source.topMargin, source.rightMargin, source.bottomMargin);
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source.width, source.height);
        }
    }

    private OnMenuItemClickListener menuItemClickListener;

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        menuItemClickListener = listener;
    }

    @Override
    public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
        if (params != null && !(params instanceof LinearLayout.LayoutParams)) {
            LinearLayout.LayoutParams converted = new LinearLayout.LayoutParams(params);
            if (params instanceof android.app.ActionBar.LayoutParams) {
                converted.gravity = ((android.app.ActionBar.LayoutParams) params).gravity;
            }
            params = converted;
        }
        super.addView(child, index, params);
    }

    public void setNavigationIcon(int resource){setNavigationIcon(resource==0?null:getContext().getDrawable(resource));}
    public Drawable getNavigationIcon(){return navigation.getDrawable();}
    public void setNavigationOnClickListener(View.OnClickListener listener){navigation.setOnClickListener(listener);}
    public void setNavigationContentDescription(CharSequence value){navigation.setContentDescription(value);}
}

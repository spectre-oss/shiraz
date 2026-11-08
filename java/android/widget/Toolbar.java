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
    public void setNavigationIcon(int resource){setNavigationIcon(resource==0?null:getContext().getDrawable(resource));}
    public Drawable getNavigationIcon(){return navigation.getDrawable();}
    public void setNavigationOnClickListener(View.OnClickListener listener){navigation.setOnClickListener(listener);}
    public void setNavigationContentDescription(CharSequence value){navigation.setContentDescription(value);}
}

// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.view.*;
import android.widget.*;
public final class TableFixture {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void size(View v,int w){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.AT_MOST));v.layout(0,0,v.getMeasuredWidth(),v.getMeasuredHeight());}
    private static View cell(TableRow row,int width,int height){View v=new View(row.getContext());row.addView(v,new TableRow.LayoutParams(width,height));return v;}
    public static void check(Context c){
        TableLayout.LayoutParams params=new TableLayout.LayoutParams(17,23,1f);check(params.width==-1&&params.height==23&&params.weight==1f,"Table params force width and preserve weight");params.leftMargin=4;TableLayout.LayoutParams copy=new TableLayout.LayoutParams(params);check(copy.leftMargin==4&&copy.weight==1f,"Table params copy");
        TableLayout table=new TableLayout(c);TableRow first=new TableRow(c),second=new TableRow(c);table.addView(first);table.addView(second);
        View a=cell(first,20,10),b=cell(first,30,12),d=cell(second,40,15),e=cell(second,10,9);
        size(table,100);check(a.getWidth()==40&&b.getLeft()==40&&e.getLeft()==40&&e.getWidth()==30,"Shared column maxima");check(second.getTop()==12&&table.getHeight()==27,"Rows stack by tallest cell");
        table.setStretchAllColumns(true);size(table,100);check(a.getWidth()==55&&b.getWidth()==45&&e.getLeft()==55,"Stretch columns");size(table,140);check(a.getWidth()==75&&b.getWidth()==65,"Resize recalculates columns");
        table.setStretchAllColumns(false);table.setShrinkAllColumns(true);size(table,50);check(a.getWidth()==30&&b.getWidth()==20,"Shrink columns");
        table.setColumnCollapsed(0,true);size(table,100);check(a.getVisibility()==View.GONE&&d.getVisibility()==View.GONE&&b.getLeft()==0,"Collapsed column");table.setColumnCollapsed(0,false);size(table,100);check(a.getVisibility()==View.VISIBLE&&b.getLeft()==40,"Expanded column restores widths");
        TableRow spanRow=new TableRow(c);View span=new View(c);TableRow.LayoutParams spanning=new TableRow.LayoutParams(1,8);spanning.span=2;spanRow.addView(span,spanning);table.addView(spanRow);size(table,100);check(span.getWidth()==70,"Spanning cell uses combined columns");
        table.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);size(table,100);check(a.getLeft()==60&&b.getLeft()==30,"RTL column order");
        table.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);table.removeView(first);size(table,100);check(e.getLeft()==40&&e.getWidth()==10,"Removed row changes maxima");
        TableRow gap=new TableRow(c);View last=new View(c);TableRow.LayoutParams gp=new TableRow.LayoutParams(8,9);gp.column=2;gap.addView(last,gp);TableLayout gaps=new TableLayout(c);gaps.addView(gap);gaps.setStretchAllColumns(true);size(gaps,38);check(last.getLeft()==20&&last.getWidth()==18,"Explicit missing columns");
        gp.span=257;try{size(gaps,38);throw new AssertionError("Unbounded span");}catch(IllegalArgumentException expected){}
        System.out.println("TABLE_LAYOUT_OK");
    }
}

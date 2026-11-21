// SPDX-License-Identifier: GPL-3.0-only
import android.view.View;
import android.view.ViewGroup;

public final class MarginResolutionSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        ViewGroup.MarginLayoutParams p=new ViewGroup.MarginLayoutParams(100,40);
        p.setMargins(4,0,8,0);p.setMarginStart(12);p.setMarginEnd(20);
        p.resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        check(p.leftMargin==12&&p.rightMargin==20,"initial relative resolution");
        p.leftMargin=128;p.rightMargin=128;
        p.resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        check(p.leftMargin==128&&p.rightMargin==128,"repeated resolution overwrote direct physical margins");
        ViewGroup.MarginLayoutParams copy=new ViewGroup.MarginLayoutParams(p);
        copy.resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        check(copy.leftMargin==128&&copy.rightMargin==128,"copy lost resolved state");
        p.setMarginStart(16);p.resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        check(p.leftMargin==16&&p.rightMargin==20,"relative setter did not invalidate resolution");
        p.resolveLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        check(p.leftMargin==20&&p.rightMargin==16,"direction change did not resolve");
        p.setMargins(32,2,48,3);p.resolveLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        check(p.leftMargin==20&&p.rightMargin==16&&p.topMargin==2&&p.bottomMargin==3,"margin setter did not resolve");
        System.out.println("MARGIN_RESOLUTION_OK");
    }
}

// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;

public class AndroidValueCompatibilitySmoke {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(android.view.KeyEvent.getDeadChar(0xb4,'e')==0xe9);
        check(android.view.KeyEvent.getDeadChar('^',' ')== '^');
        check(android.view.KeyEvent.getDeadChar('^','q')==0);
        check(android.os.Process.getUidForName("1234")==1234);
        check(android.os.Process.getGidForName("1234")==1234);
        check(android.os.Process.getGidForName("shiraz_nonexistent_group_978132")==-1);
        check(android.os.Process.getUidForName("99999999999999999999")==-1);
        check(android.os.Process.getThreadPriority(0)==android.os.Process.getThreadPriority(android.os.Process.myTid()));
        String text="a\ud83d\ude00b";
        check(TextUtils.getOffsetAfter(text,1)==3);
        check(TextUtils.getOffsetBefore(text,3)==1);
        check(TextUtils.getOffsetAfter(text,4)==4);
        check(TextUtils.getOffsetBefore(text,0)==0);
        check(TextUtils.getOffsetAfter("\ud800x",0)==1);
        check(TextUtils.lastIndexOf("ababa",'a',1,3)==2);
        check(TextUtils.lastIndexOf("ababa",'a',2)==2);
        check(TextUtils.indexOf("ababa","ba",2,5)==3);
        check(TextUtils.indexOf("ababa",'a',1,2)==-1);
        check(ImageFormat.getBitsPerPixel(ImageFormat.NV21)==12);
        check(ImageFormat.getBitsPerPixel(ImageFormat.JPEG)==-1);
        CdmaCellLocation cdma=new CdmaCellLocation();
        check(cdma.isEmpty());
        cdma.setCellLocationData(3,14400,-14400,4,5);
        Bundle values=new Bundle(); cdma.fillInNotifierBundle(values);
        check(cdma.equals(new CdmaCellLocation(values)));
        check(CdmaCellLocation.convertQuartSecToDecDegrees(-14400)==-1.0);
        boolean invalid=false;
        try { CdmaCellLocation.convertQuartSecToDecDegrees(Integer.MAX_VALUE); }
        catch(IllegalArgumentException expected){ invalid=true; }
        check(invalid);
        cdma.setStateInvalid(); check(cdma.isEmpty());
        GsmCellLocation gsm=new GsmCellLocation(); check(gsm.isEmpty());
        gsm.setLacAndCid(2,7); gsm.setPsc(1); gsm.fillInNotifierBundle(values);
        check(gsm.equals(new GsmCellLocation(values)));
        System.out.println("ANDROID_VALUE_COMPATIBILITY_OK");
    }
}

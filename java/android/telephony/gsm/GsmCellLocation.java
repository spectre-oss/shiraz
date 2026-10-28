// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.gsm;
import android.os.Bundle;
import android.telephony.CellLocation;
public class GsmCellLocation extends CellLocation {
    private int lac=-1,cid=-1,psc=-1;
    public GsmCellLocation() {}
    public GsmCellLocation(Bundle values){lac=values.getInt("lac",-1);cid=values.getInt("cid",-1);psc=values.getInt("psc",-1);}
    public int getLac(){return lac;} public int getCid(){return cid;} public int getPsc(){return psc;}
    public void setLacAndCid(int area,int cell){lac=area;cid=cell;}
    public void setPsc(int value){psc=value;}
    public void setStateInvalid(){lac=cid=psc=-1;}
    public boolean isEmpty(){return lac==-1&&cid==-1&&psc==-1;}
    public void fillInNotifierBundle(Bundle values){values.putInt("lac",lac);values.putInt("cid",cid);values.putInt("psc",psc);}
    public boolean equals(Object other){if(!(other instanceof GsmCellLocation))return false;GsmCellLocation c=(GsmCellLocation)other;return lac==c.lac&&cid==c.cid&&psc==c.psc;}
    public int hashCode(){return lac^cid^psc;}
    public String toString(){return "["+lac+","+cid+","+psc+"]";}
}

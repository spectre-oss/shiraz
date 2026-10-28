// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.cdma;
import android.os.Bundle;
import android.telephony.CellLocation;
public class CdmaCellLocation extends CellLocation {
    public static final int INVALID_LAT_LONG = Integer.MAX_VALUE;
    private int station=-1, latitude=INVALID_LAT_LONG, longitude=INVALID_LAT_LONG, system=-1, network=-1;
    public CdmaCellLocation() {}
    public CdmaCellLocation(Bundle values) {
        station=values.getInt("baseStationId",-1);
        latitude=values.getInt("baseStationLatitude",INVALID_LAT_LONG);
        longitude=values.getInt("baseStationLongitude",INVALID_LAT_LONG);
        system=values.getInt("systemId",-1); network=values.getInt("networkId",-1);
    }
    public int getBaseStationId(){return station;}
    public int getBaseStationLatitude(){return latitude;}
    public int getBaseStationLongitude(){return longitude;}
    public int getSystemId(){return system;}
    public int getNetworkId(){return network;}
    public void setCellLocationData(int id,int lat,int lon){station=id;latitude=lat;longitude=lon;}
    public void setCellLocationData(int id,int lat,int lon,int sid,int nid){setCellLocationData(id,lat,lon);system=sid;network=nid;}
    public void setStateInvalid(){setCellLocationData(-1,INVALID_LAT_LONG,INVALID_LAT_LONG,-1,-1);}
    public boolean isEmpty(){return station==-1&&latitude==INVALID_LAT_LONG&&longitude==INVALID_LAT_LONG&&system==-1&&network==-1;}
    public void fillInNotifierBundle(Bundle values){values.putInt("baseStationId",station);values.putInt("baseStationLatitude",latitude);values.putInt("baseStationLongitude",longitude);values.putInt("systemId",system);values.putInt("networkId",network);}
    public static double convertQuartSecToDecDegrees(int value){if(value < -2592000 || value > 2592000)throw new IllegalArgumentException("Invalid quarter-second coordinate");return value/14400.0;}
    public boolean equals(Object other){if(!(other instanceof CdmaCellLocation))return false;CdmaCellLocation c=(CdmaCellLocation)other;return station==c.station&&latitude==c.latitude&&longitude==c.longitude&&system==c.system&&network==c.network;}
    public int hashCode(){return station^latitude^longitude^system^network;}
    public String toString(){return "["+station+","+latitude+","+longitude+","+system+","+network+"]";}
}

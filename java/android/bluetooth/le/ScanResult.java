// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;
import android.bluetooth.BluetoothDevice;
import android.os.*;
public final class ScanResult implements Parcelable {
    public static final int DATA_COMPLETE=0,DATA_TRUNCATED=2,PERIODIC_INTERVAL_NOT_PRESENT=0,PHY_UNUSED=0,SID_NOT_PRESENT=255,TX_POWER_NOT_PRESENT=127;
    private final BluetoothDevice device;private final ScanRecord record;private final int event,primary,secondary,sid,power,rssi,interval;private final long timestamp;
    public ScanResult(BluetoothDevice device,ScanRecord record,int rssi,long timestamp){this(device,17,1,0,255,127,rssi,0,record,timestamp);}
    public ScanResult(BluetoothDevice device,int event,int primary,int secondary,int sid,int power,int rssi,int interval,ScanRecord record,long timestamp){this.device=device;this.event=event;this.primary=primary;this.secondary=secondary;this.sid=sid;this.power=power;this.rssi=rssi;this.interval=interval;this.record=record;this.timestamp=timestamp;}
    public BluetoothDevice getDevice(){return device;}public ScanRecord getScanRecord(){return record;}public int getRssi(){return rssi;}public long getTimestampNanos(){return timestamp;}
    public boolean isLegacy(){return (event&16)!=0;}public boolean isConnectable(){return (event&1)!=0;}public int getDataStatus(){return (event>>>5)&3;}public int getPrimaryPhy(){return primary;}public int getSecondaryPhy(){return secondary;}public int getAdvertisingSid(){return sid;}public int getTxPower(){return power;}public int getPeriodicAdvertisingInterval(){return interval;}
    public boolean equals(Object other){if(!(other instanceof ScanResult))return false;ScanResult r=(ScanResult)other;boolean sameRecord=record==r.record||(record!=null&&r.record!=null&&java.util.Arrays.equals(record.getBytes(),r.record.getBytes()));return java.util.Objects.equals(device,r.device)&&sameRecord&&event==r.event&&primary==r.primary&&secondary==r.secondary&&sid==r.sid&&power==r.power&&rssi==r.rssi&&interval==r.interval&&timestamp==r.timestamp;}
    public int hashCode(){int h=1;int recHash=record==null?0:java.util.Arrays.hashCode(record.getBytes());for(Object value:new Object[]{device,rssi,recHash,timestamp,event,primary,secondary,sid,power,interval})h=31*h+(value==null?0:value.hashCode());return h;}
    public String toString(){return "ScanResult[device="+device+", rssi="+rssi+", timestampNanos="+timestamp+", event="+event+"]";}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel out,int flags){
        out.writeTypedObject(device,flags);
        if(record!=null){out.writeInt(1);out.writeByteArray(record.getBytes());}else{out.writeInt(0);}
        out.writeInt(rssi);out.writeLong(timestamp);out.writeInt(event);out.writeInt(primary);out.writeInt(secondary);out.writeInt(sid);out.writeInt(power);out.writeInt(interval);
    }
    public static final Parcelable.Creator<ScanResult> CREATOR=new Parcelable.Creator<ScanResult>(){
        public ScanResult createFromParcel(Parcel in){
            BluetoothDevice dev=in.readTypedObject(BluetoothDevice.CREATOR);
            ScanRecord rec=in.readInt()!=0?ScanRecord.parseFromBytes(in.createByteArray()):null;
            int rssiVal=in.readInt();long ts=in.readLong();int ev=in.readInt();int pri=in.readInt();int sec=in.readInt();int sidVal=in.readInt();int tx=in.readInt();int per=in.readInt();
            return new ScanResult(dev,ev,pri,sec,sidVal,tx,rssiVal,per,rec,ts);
        }
        public ScanResult[] newArray(int n){return new ScanResult[n];}
    };
}

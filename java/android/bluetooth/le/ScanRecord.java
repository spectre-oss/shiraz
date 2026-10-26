// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth.le;
import android.os.ParcelUuid;
import android.util.SparseArray;
import java.util.*;
/** Owned BLE advertising TLVs. Parsing data does not perform a radio scan. */
public final class ScanRecord {
    public static final int DATA_TYPE_FLAGS=1,DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL=2,DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE=3,DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL=4,DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE=5,DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL=6,DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE=7,DATA_TYPE_LOCAL_NAME_SHORT=8,DATA_TYPE_LOCAL_NAME_COMPLETE=9,DATA_TYPE_TX_POWER_LEVEL=10,DATA_TYPE_SERVICE_SOLICITATION_UUIDS_16_BIT=20,DATA_TYPE_SERVICE_SOLICITATION_UUIDS_128_BIT=21,DATA_TYPE_SERVICE_DATA_16_BIT=22,DATA_TYPE_SERVICE_SOLICITATION_UUIDS_32_BIT=31,DATA_TYPE_SERVICE_DATA_32_BIT=32,DATA_TYPE_SERVICE_DATA_128_BIT=33,DATA_TYPE_MANUFACTURER_SPECIFIC_DATA=255;
    private final byte[] bytes;private int flags=-1,power=Integer.MIN_VALUE;private String name;private boolean malformed;
    private final ArrayList<ParcelUuid> uuids=new ArrayList<ParcelUuid>(),solicitation=new ArrayList<ParcelUuid>();
    private final SparseArray<byte[]> manufacturer=new SparseArray<byte[]>();
    private final LinkedHashMap<ParcelUuid,byte[]> services=new LinkedHashMap<ParcelUuid,byte[]>();
    private final LinkedHashMap<Integer,byte[]> fields=new LinkedHashMap<Integer,byte[]>();
    private ScanRecord(byte[] bytes){this.bytes=bytes.clone();}
    private static long little(byte[] data,int at,int count){long value=0;for(int i=0;i<count;i++)value|=(data[at+i]&255L)<<(8*i);return value;}
    private static ParcelUuid uuid(byte[] data,int at,int size){return new ParcelUuid(size==16?new UUID(little(data,at+8,8),little(data,at,8)):new UUID((little(data,at,size)<<32)|0x1000L,0x800000805f9b34fbL));}
    private void list(int at,int count,int width,ArrayList<ParcelUuid> out){if(count%width!=0)throw new IllegalArgumentException("UUID field alignment");for(int i=0;i<count;i+=width)out.add(uuid(bytes,at+i,width));}
    public static ScanRecord parseFromBytes(byte[] source){
        if(source==null)return null;if(source.length>65535)throw new IllegalArgumentException("Advertising record bound");ScanRecord result=new ScanRecord(source);
        try{for(int at=0;at<source.length;){int length=result.bytes[at++]&255;if(length==0)break;if(length>source.length-at)throw new IllegalArgumentException("Truncated advertising field");int type=result.bytes[at++]&255,count=length-1,end=at+count;result.fields.put(type,Arrays.copyOfRange(result.bytes,at,end));
            if(type==1){if(count!=1)throw new IllegalArgumentException("Advertising flags");result.flags=result.bytes[at]&255;}
            else if(type>=2&&type<=7){int width=type<=3?2:type<=5?4:16;result.list(at,count,width,result.uuids);}
            else if(type==8||type==9)result.name=new String(result.bytes,at,count,java.nio.charset.StandardCharsets.UTF_8);
            else if(type==10){if(count!=1)throw new IllegalArgumentException("Advertising power");result.power=result.bytes[at];}
            else if(type==20||type==21||type==31)result.list(at,count,type==20?2:type==31?4:16,result.solicitation);
            else if(type==22||type==32||type==33){int width=type==22?2:type==32?4:16;if(count<width)throw new IllegalArgumentException("Service data UUID");result.services.put(uuid(result.bytes,at,width),Arrays.copyOfRange(result.bytes,at+width,end));}
            else if(type==255){if(count<2)throw new IllegalArgumentException("Manufacturer identifier");result.manufacturer.put((int)little(result.bytes,at,2),Arrays.copyOfRange(result.bytes,at+2,end));}
            at=end;
        }}catch(IllegalArgumentException invalid){result=new ScanRecord(result.bytes);result.malformed=true;}return result;
    }
    public boolean shirazMalformed(){return malformed;}
    public int getAdvertiseFlags(){return flags;}public int getTxPowerLevel(){return power;}public String getDeviceName(){return name;}
    public byte[] getBytes(){return bytes.clone();}
    public List<ParcelUuid> getServiceUuids(){return uuids.isEmpty()?null:new ArrayList<ParcelUuid>(uuids);}
    public List<ParcelUuid> getServiceSolicitationUuids(){return solicitation.isEmpty()?null:new ArrayList<ParcelUuid>(solicitation);}
    private static byte[] copy(byte[] value){return value==null?null:value.clone();}
    public byte[] getManufacturerSpecificData(int id){return copy(manufacturer.get(id));}
    public SparseArray<byte[]> getManufacturerSpecificData(){SparseArray<byte[]> out=new SparseArray<byte[]>(manufacturer.size());for(int i=0;i<manufacturer.size();i++)out.put(manufacturer.keyAt(i),manufacturer.valueAt(i).clone());return out;}
    public byte[] getServiceData(ParcelUuid uuid){return copy(services.get(uuid));}
    public Map<ParcelUuid,byte[]> getServiceData(){Map<ParcelUuid,byte[]> out=new LinkedHashMap<ParcelUuid,byte[]>();for(Map.Entry<ParcelUuid,byte[]> e:services.entrySet())out.put(e.getKey(),e.getValue().clone());return out;}
    public Map<Integer,byte[]> getAdvertisingDataMap(){Map<Integer,byte[]> out=new LinkedHashMap<Integer,byte[]>();for(Map.Entry<Integer,byte[]> e:fields.entrySet())out.put(e.getKey(),e.getValue().clone());return out;}
    public String toString(){return "ScanRecord[flags="+flags+", services="+uuids+", txPower="+power+", name="+name+", malformed="+malformed+"]";}
}

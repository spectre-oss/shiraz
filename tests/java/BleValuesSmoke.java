// SPDX-License-Identifier: GPL-3.0-only
import android.bluetooth.le.*;
import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import java.util.*;
public final class BleValuesSmoke {
    public static void main(String[] args){
        org.shiraz.fixture.BleValuesFixture.check();
        if(ScanRecord.parseFromBytes(null)!=null)throw new AssertionError("null record");
        for(byte[] bad:new byte[][]{{2,1},{3,3,13,0,2,1},{2,-1,1},{2,22,1},{2,3,13}}){ScanRecord record=ScanRecord.parseFromBytes(bad);if(!record.shirazMalformed()||record.getServiceUuids()!=null||record.getAdvertiseFlags()!=-1||!Arrays.equals(record.getBytes(),bad))throw new AssertionError("malformed record leaked partial values");}
        byte[] uuid128={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};byte[] ad=new byte[18];ad[0]=17;ad[1]=7;System.arraycopy(uuid128,0,ad,2,16);ScanRecord record=ScanRecord.parseFromBytes(ad);if(!record.getServiceUuids().get(0).toString().equals("0f0e0d0c-0b0a-0908-0706-050403020100"))throw new AssertionError("128-bit UUID byte order");
        byte[] ad32={5,5,0x78,0x56,0x34,0x12};if(!ScanRecord.parseFromBytes(ad32).getServiceUuids().get(0).toString().equals("12345678-0000-1000-8000-00805f9b34fb"))throw new AssertionError("32-bit UUID byte order");
        byte[] service128=new byte[19];service128[0]=18;service128[1]=33;System.arraycopy(uuid128,0,service128,2,16);service128[18]=42;
        if(ScanRecord.parseFromBytes(service128).getServiceData(record.getServiceUuids().get(0))[0]!=42)throw new AssertionError("128-bit service data");
        byte[] service32={6,32,0x78,0x56,0x34,0x12,42};ParcelUuid id32=ParcelUuid.fromString("12345678-0000-1000-8000-00805f9b34fb");if(ScanRecord.parseFromBytes(service32).getServiceData(id32)[0]!=42)throw new AssertionError("32-bit service data");
        if(!ScanRecord.parseFromBytes(new byte[]{3,20,13,24}).getServiceSolicitationUuids().get(0).equals(ParcelUuid.fromString("0000180d-0000-1000-8000-00805f9b34fb")))throw new AssertionError("solicitation UUID");
        Random random=new Random(541002);for(int n=0;n<2000;n++){byte[] raw=new byte[random.nextInt(256)];random.nextBytes(raw);ScanRecord parsed=ScanRecord.parseFromBytes(raw);if(!Arrays.equals(parsed.getBytes(),raw))throw new AssertionError("raw bytes lost");}
        try{ScanRecord.parseFromBytes(new byte[65536]);throw new AssertionError("record bound accepted");}catch(IllegalArgumentException expected){}
        BluetoothDevice device=BluetoothDevice.shirazObserved("02:00:00:00:00:01","fixture",2);if(!device.getName().equals("fixture")||device.getType()!=2||!device.equals(BluetoothDevice.shirazObserved("02:00:00:00:00:01",null,0)))throw new AssertionError("observed device identity");try{device.getBondState();throw new AssertionError("fabricated bond state");}catch(UnsupportedOperationException expected){}
        android.util.SparseArray<Object> sparse=new android.util.SparseArray<Object>();TreeMap<Integer,Object> reference=new TreeMap<Integer,Object>();
        for(int n=0;n<500;n++){int key=random.nextInt(100)-50;if(random.nextBoolean()){Object value=random.nextBoolean()?new Object():null;sparse.put(key,value);reference.put(key,value);}else{sparse.remove(key);reference.remove(key);}if(sparse.size()!=reference.size())throw new AssertionError("sparse size mismatch");int at=0;for(Map.Entry<Integer,Object> entry:reference.entrySet()){if(sparse.keyAt(at)!=entry.getKey()||sparse.valueAt(at)!=entry.getValue())throw new AssertionError("sparse reference mismatch");at++;}}
        System.out.println("BLE_ADVERTISING_BOUNDS_AND_BYTE_ORDER_OK");
    }
}

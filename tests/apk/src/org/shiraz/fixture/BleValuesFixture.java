// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.bluetooth.le.*;
import android.os.ParcelUuid;
import android.util.SparseArray;
import java.util.*;
public final class BleValuesFixture {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void check(ScanRecord record){
        check(record.getAdvertiseFlags()==6&&record.getTxPowerLevel()==-10&&"HR".equals(record.getDeviceName()),"advertising metadata");ParcelUuid heart=ParcelUuid.fromString("0000180d-0000-1000-8000-00805f9b34fb");check(record.getServiceUuids().contains(heart),"heart-rate UUID");check(Arrays.equals(record.getManufacturerSpecificData(76),new byte[]{1,2}),"manufacturer payload");check(Arrays.equals(record.getServiceData(heart),new byte[]{9}),"service payload");
        byte[] bytes=record.getBytes();bytes[0]=0;record.getManufacturerSpecificData(76)[0]=99;record.getServiceData().get(heart)[0]=99;check(record.getBytes()[0]==2&&record.getManufacturerSpecificData(76)[0]==1&&record.getServiceData(heart)[0]==9,"advertising ownership");
        ScanResult result=new ScanResult(null,record,-42,123456);check(result.getScanRecord()==record&&result.getRssi()==-42&&result.getTimestampNanos()==123456&&result.isLegacy()&&result.isConnectable()&&result.getPrimaryPhy()==1&&result.getAdvertisingSid()==255,"legacy result metadata");
        ScanResult equalResult=new ScanResult(null,record,-42,123456);check(result.equals(equalResult)&&result.hashCode()==equalResult.hashCode(),"scan result equality");
        ScanResult extended=new ScanResult(null,64,3,2,7,-12,-63,160,record,999);check(!extended.isLegacy()&&!extended.isConnectable()&&extended.getDataStatus()==ScanResult.DATA_TRUNCATED&&extended.getSecondaryPhy()==2&&extended.getTxPower()==-12&&extended.getPeriodicAdvertisingInterval()==160,"extended result metadata");
        final int[] calls={0,0,0};ScanCallback callback=new ScanCallback(){public void onScanResult(int type,ScanResult r){super.onScanResult(type,r);check(type==1&&r==result,"callback result");calls[0]++;}public void onBatchScanResults(List<ScanResult> results){super.onBatchScanResults(results);check(results.get(0)==result,"batch callback");calls[1]++;}public void onScanFailed(int error){super.onScanFailed(error);check(error==SCAN_FAILED_INTERNAL_ERROR,"failure callback");calls[2]++;}};callback.onScanResult(1,result);callback.onBatchScanResults(Collections.singletonList(result));callback.onScanFailed(3);check(Arrays.equals(calls,new int[]{1,1,1}),"overridable callback delivery");
        SparseArray<Object> sparse=new SparseArray<Object>(0);Object first=new String("one"),equal=new String("one");sparse.put(9,null);sparse.put(-3,first);sparse.append(2,equal);check(sparse.size()==3&&sparse.keyAt(0)==-3&&sparse.keyAt(1)==2&&sparse.get(9,first)==null&&sparse.get(99,first)==first,"sorted sparse keys and null values");check(sparse.indexOfValue(first)==0&&sparse.indexOfValue(new String("one"))==-1&&sparse.indexOfKey(5)==~2,"sparse identity and insertion index");SparseArray<Object> clone=sparse.clone();clone.setValueAt(0,equal);clone.removeAtRange(1,99);check(clone.size()==1&&sparse.size()==3&&sparse.valueAt(0)==first,"shallow independent sparse clone");
        check(heart.equals(new ParcelUuid(heart.getUuid()))&&heart.hashCode()==new ParcelUuid(heart.getUuid()).hashCode(),"UUID carrier value equality");
        System.out.println("SHIRAZ_FIXTURE_BLE_VALUES_OK");
    }
    public static void check(){try{byte[] data={2,1,6,3,3,13,24,3,9,72,82,2,10,-10,5,-1,76,0,1,2,4,22,13,24,9,0};ScanRecord record=(ScanRecord)ScanRecord.class.getMethod("parseFromBytes",byte[].class).invoke(null,(Object)data);check(record);}catch(Exception failure){throw new AssertionError(failure);}}
}

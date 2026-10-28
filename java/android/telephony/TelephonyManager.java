// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;
/** Desktop without a modem: country information is unavailable, never inferred. */
public class TelephonyManager {
 public static final int CALL_STATE_IDLE=0,CALL_STATE_RINGING=1,CALL_STATE_OFFHOOK=2;
 private final java.util.Map<PhoneStateListener,Object> callListeners=new java.util.IdentityHashMap<PhoneStateListener,Object>();
 public int getCallState(){return CALL_STATE_IDLE;}
 public void listen(final PhoneStateListener listener,int events){
  if(listener==null)throw new IllegalArgumentException("listener");
  if((events&~PhoneStateListener.LISTEN_CALL_STATE)!=0)throw new UnsupportedOperationException("Telephony listener events: "+events);
  final Object registration=new Object();
  synchronized(callListeners){
   if(events==PhoneStateListener.LISTEN_NONE){callListeners.remove(listener);return;}
   callListeners.put(listener,registration);
  }
  listener.handler.post(new Runnable(){public void run(){
   synchronized(callListeners){if(callListeners.get(listener)!=registration)return;}
   listener.onCallStateChanged(CALL_STATE_IDLE,"");
  }});
 }
 public static final int PHONE_TYPE_NONE=0,SIM_STATE_ABSENT=1,NETWORK_TYPE_UNKNOWN=0;
 private String configured(String operation,String key){shiraz.runtime.HostAppInstaller.recordAccess("telephony:"+operation);return shiraz.runtime.DeviceProfile.get(key,"");}
 public String getSimCountryIso(){return configured("getSimCountryIso","telephony.simCountryIso");}
 public String getSimOperator(){return configured("getSimOperator","telephony.simOperator");}
 public String getNetworkCountryIso(){return configured("getNetworkCountryIso","telephony.networkCountryIso");}
 public String getNetworkOperatorName(){return configured("getNetworkOperatorName","telephony.networkOperatorName");}
 public int getPhoneType(){return shiraz.runtime.DeviceProfile.integer("telephony.phoneType",PHONE_TYPE_NONE);}public int getSimState(){return shiraz.runtime.DeviceProfile.integer("telephony.simState",SIM_STATE_ABSENT);}
 public int getNetworkType(){return shiraz.runtime.DeviceProfile.integer("telephony.networkType",NETWORK_TYPE_UNKNOWN);}
}

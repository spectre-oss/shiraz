// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Battery property API. Host battery telemetry is not yet connected. */
public final class BatteryManager {
    public static final int BATTERY_STATUS_UNKNOWN=1,BATTERY_STATUS_CHARGING=2,BATTERY_STATUS_DISCHARGING=3,BATTERY_STATUS_NOT_CHARGING=4,BATTERY_STATUS_FULL=5;
    public static final int BATTERY_HEALTH_UNKNOWN=1,BATTERY_HEALTH_GOOD=2,BATTERY_HEALTH_OVERHEAT=3,BATTERY_HEALTH_DEAD=4,BATTERY_HEALTH_OVER_VOLTAGE=5,BATTERY_HEALTH_UNSPECIFIED_FAILURE=6,BATTERY_HEALTH_COLD=7;
    public static final int BATTERY_PLUGGED_AC=1,BATTERY_PLUGGED_USB=2,BATTERY_PLUGGED_WIRELESS=4;
    public static final int BATTERY_PROPERTY_CHARGE_COUNTER=1,BATTERY_PROPERTY_CURRENT_NOW=2,BATTERY_PROPERTY_CURRENT_AVERAGE=3,BATTERY_PROPERTY_CAPACITY=4,BATTERY_PROPERTY_ENERGY_COUNTER=5,BATTERY_PROPERTY_STATUS=6;
    public static final String EXTRA_STATUS="status",EXTRA_HEALTH="health",EXTRA_PRESENT="present",EXTRA_LEVEL="level",EXTRA_SCALE="scale",EXTRA_ICON_SMALL="icon-small",EXTRA_PLUGGED="plugged",EXTRA_VOLTAGE="voltage",EXTRA_TEMPERATURE="temperature",EXTRA_TECHNOLOGY="technology";
    public static final String ACTION_CHARGING="android.os.action.CHARGING",ACTION_DISCHARGING="android.os.action.DISCHARGING";
    private final android.content.Context context;
    public BatteryManager(){context=null;}
    private BatteryManager(android.content.Context context){this.context=java.util.Objects.requireNonNull(context);}
    public static BatteryManager shirazForContext(android.content.Context context){return new BatteryManager(context);}
    public long getLongProperty(int id){return Long.MIN_VALUE;}
    public int getIntProperty(int id){return context!=null&&context.getApplicationInfo().targetSdkVersion>=28?Integer.MIN_VALUE:0;}
    public boolean isCharging(){throw new UnsupportedOperationException("Host battery charging telemetry is not connected");}
    public long computeChargeTimeRemaining(){return -1;}
}

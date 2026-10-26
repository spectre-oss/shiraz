// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

/** Explicit simulated fix, never inferred from the host or enabled by default. */
public final class TestLocationConfig {
    public final double latitude, longitude;
    public final float accuracy;
    private TestLocationConfig(double latitude, double longitude, float accuracy) {
        this.latitude=latitude;this.longitude=longitude;this.accuracy=accuracy;
    }
    public static TestLocationConfig fromEnvironment() { return parse(System.getenv("SHIRAZ_TEST_LOCATION")); }
    public static TestLocationConfig parse(String value) {
        if(value==null)return null;
        String[] parts=value.split(",",-1);
        if(parts.length<2||parts.length>3)throw new IllegalArgumentException("SHIRAZ_TEST_LOCATION requires latitude,longitude[,accuracyMeters]");
        try {
            double lat=Double.parseDouble(parts[0]),lon=Double.parseDouble(parts[1]);
            float accuracy=parts.length==3?Float.parseFloat(parts[2]):5;
            if(!Double.isFinite(lat)||Math.abs(lat)>90||!Double.isFinite(lon)||Math.abs(lon)>180||!Float.isFinite(accuracy)||accuracy<=0)
                throw new IllegalArgumentException("Test location coordinate or accuracy range");
            return new TestLocationConfig(lat,lon,accuracy);
        } catch(NumberFormatException error) { throw new IllegalArgumentException("Invalid test location number",error); }
    }
    public boolean grants(String permission,java.util.Properties metadata) {
        if(!"android.permission.ACCESS_FINE_LOCATION".equals(permission)&&!"android.permission.ACCESS_COARSE_LOCATION".equals(permission))return false;
        int count=Integer.parseInt(metadata.getProperty("permission.count","0"));
        if(count<0||count>1024)throw new IllegalArgumentException("Permission count");
        for(int i=0;i<count;i++)if(permission.equals(metadata.getProperty("permission."+i)))return true;
        return false;
    }
}

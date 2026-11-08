// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Paint;
import java.util.ArrayList;
import java.util.Random;
import java.util.TreeSet;
import shiraz.runtime.TextGeometry;

public final class TextGeometryBoundariesSmoke {
    public static void main(String[] args) {
        Random random=new Random(426);
        for(int pass=0;pass<200;pass++) {
            final float[] points=new float[(pass%67)*5];TreeSet<Integer> expected=new TreeSet<Integer>();
            expected.add(0);expected.add(100);
            for(int i=0;i<points.length;i+=5){points[i]=random.nextInt(101);points[i+1]=random.nextInt(101);expected.add((int)points[i]);expected.add((int)points[i+1]);}
            Paint paint=new Paint(){@Override public float[] shirazTextClusters(String text){return points;}};
            String text=new String(new char[100]);TextGeometry geometry=new TextGeometry(text,paint,0);
            if(!geometry.boundaries().equals(new ArrayList<Integer>(expected)))throw new AssertionError("Cluster boundary order/dedup mismatch");
        }
        System.out.println("TEXT_GEOMETRY_BOUNDARIES_OK");
    }
}

// SPDX-License-Identifier: GPL-3.0-only
public final class SparseIntSmoke {
 public static void main(String[] args){org.shiraz.fixture.SparseIntFixture.check();android.util.SparseIntArray values=new android.util.SparseIntArray();if(values.copyKeys()!=null)throw new AssertionError("Empty key copy");values.put(2,3);int[] keys=values.copyKeys();keys[0]=7;if(values.keyAt(0)!=2)throw new AssertionError("Key copy ownership");for(int i=0;i<65536;i++)values.put(i,i);try{values.put(-1,1);throw new AssertionError("Capacity bound missing");}catch(IllegalStateException expected){}values.put(0,9);if(values.get(0)!=9)throw new AssertionError("Update at capacity");}
}

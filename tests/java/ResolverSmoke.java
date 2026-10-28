// SPDX-License-Identifier: GPL-3.0-only
package shiraz;
public final class ResolverSmoke {
 private static native int run();
 public static void main(String[] args){
  System.load(args[0]);int result=run();
  if(result!=42)throw new AssertionError("Guest resolver ABI: "+result);
  System.out.println("DIGITALIS_RESOLVER_ABI_OK");
 }
}

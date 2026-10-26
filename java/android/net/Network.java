// SPDX-License-Identifier: GPL-3.0-only
package android.net;
/** Opaque handle for the host default network. */
public class Network {
 private final long handle;
 public Network(long handle){this.handle=handle;}
 public long getNetworkHandle(){return handle;}
 @Override public boolean equals(Object other){return other instanceof Network&&((Network)other).handle==handle;}
 @Override public int hashCode(){return (int)(handle^(handle>>>32));}
 @Override public String toString(){return Long.toString(handle);}
}

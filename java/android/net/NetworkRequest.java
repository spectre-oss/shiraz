// SPDX-License-Identifier: GPL-3.0-only
package android.net;
/** Capability filter for connectivity callbacks. */
public class NetworkRequest {
 final NetworkCapabilities capabilities;
 NetworkRequest(NetworkCapabilities capabilities){this.capabilities=capabilities;}
 public boolean hasCapability(int capability){return capabilities.hasCapability(capability);}
 public boolean hasTransport(int transport){return capabilities.hasTransport(transport);}
 public static class Builder {
  private final NetworkCapabilities capabilities=new NetworkCapabilities();
  public Builder(){}
  public Builder addCapability(int capability){capabilities.addCapability(capability);return this;}
  public Builder removeCapability(int capability){capabilities.removeCapability(capability);return this;}
  public Builder addTransportType(int transport){capabilities.addTransportType(transport);return this;}
  public NetworkRequest build(){return new NetworkRequest(new NetworkCapabilities(capabilities));}
 }
}

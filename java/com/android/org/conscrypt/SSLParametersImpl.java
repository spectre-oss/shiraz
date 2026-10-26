// SPDX-License-Identifier: GPL-3.0-only
package com.android.org.conscrypt;
import javax.net.ssl.X509TrustManager;
/** Android socket-adapter trust metadata for the actual wolfSSL-backed context. */
public final class SSLParametersImpl {
    final X509TrustManager x509TrustManager;
    SSLParametersImpl(X509TrustManager trust){x509TrustManager=trust;}
}

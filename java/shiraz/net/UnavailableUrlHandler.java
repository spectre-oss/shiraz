// SPDX-License-Identifier: GPL-3.0-only
package shiraz.net;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * URL handler for a scheme whose host network stack is not available.
 *
 * libcore falls back to com.android.okhttp.Http(s)Handler for http and https and
 * wraps a missing class in AssertionError. On hosts that boot without okhttp (the
 * darwin launcher, until okhttp and wolfJSSE are ported) the darwin libcore patch
 * (patches/art-darwin-url-handlers.patch) loads these handlers from the system
 * class path instead, so URL parsing keeps working and openConnection fails with a
 * catchable IOException. No connection is attempted. java.protocol.handler.pkgs is
 * left unset, so an app can still register its own schemes through it.
 */
public abstract class UnavailableUrlHandler extends URLStreamHandler {
    private final int port;
    protected UnavailableUrlHandler(int port){this.port=port;}
    @Override protected int getDefaultPort(){return port;}
    @Override protected URLConnection openConnection(URL url)throws IOException{throw unavailable(url);}
    @Override protected URLConnection openConnection(URL url,Proxy proxy)throws IOException{
        if(url==null||proxy==null)throw new IllegalArgumentException("url and proxy are required");
        throw unavailable(url);
    }
    private static IOException unavailable(URL url){
        return new IOException(url.getProtocol()+" URL connections are unavailable on this host: okhttp and wolfJSSE are not ported to macOS");
    }
}

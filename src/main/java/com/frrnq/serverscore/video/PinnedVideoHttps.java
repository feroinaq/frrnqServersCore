package com.frrnq.serverscore.video;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Pin the TCP peer while retaining JSSE trust checks and the URL hostname for TLS. */
final class PinnedVideoHttps extends SSLSocketFactory {
    private final InetAddress address;
    private final String host;
    private final SSLSocketFactory tls = (SSLSocketFactory) SSLSocketFactory.getDefault();

    static HttpsURLConnection open(URI uri) throws IOException {
        InetAddress[] answers = InetAddress.getAllByName(uri.getHost());
        for (InetAddress address : answers) {
            if (!isPublic(address) || java.net.NetworkInterface.getByInetAddress(address) != null) { throw new VideoCache.Failure("Video domain resolved to a non-public address."); }
        }
        if (answers.length == 0) { throw new VideoCache.Failure("Video domain has no address."); }
        HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
        connection.setSSLSocketFactory(new PinnedVideoHttps(uri.getHost(), answers[0]));
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Connection", "close");
        connection.setRequestProperty("Accept-Encoding", "identity");
        return connection;
    }

    private PinnedVideoHttps(String host, InetAddress address) { this.host = host; this.address = address; }

    static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
        if (bytes.length == 4) {
            return a > 0 && a < 224 && a != 10 && a != 127
                    && !(a == 100 && b >= 64 && b <= 127) && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31)
                    && !(a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2)) || (b == 88 && c == 99)))
                    && !(a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                    && !(a == 203 && b == 0 && c == 113);
        }
        // Global unicast, excluding transition and documentation prefixes.
        return (a & 224) == 32 && !(a == 32 && b == 2)
                && !(a == 32 && b == 1 && (c <= 1 || (c == 13 && (bytes[3] & 255) == 184)))
                && !(a == 63 && b == 255);
    }

    @Override public Socket createSocket() {
        return new Socket(Proxy.NO_PROXY) {
            @Override public void connect(SocketAddress endpoint, int timeout) throws IOException {
                if (!(endpoint instanceof InetSocketAddress) || ((InetSocketAddress) endpoint).getPort() != 443) {
                    throw new IOException("Unexpected video endpoint.");
                }
                super.connect(new InetSocketAddress(address, 443), timeout);
            }
            @Override public void connect(SocketAddress endpoint) throws IOException { connect(endpoint, 5000); }
        };
    }

    @Override public Socket createSocket(Socket socket, String name, int port, boolean close) throws IOException {
        if (!host.equalsIgnoreCase(name) || !address.equals(socket.getInetAddress()) || port != 443 || socket.getPort() != 443) {
            socket.close();
            throw new IOException("Video connection peer was not validated.");
        }
        SSLSocket ssl = (SSLSocket) tls.createSocket(socket, host, port, close);
        SSLParameters parameters = ssl.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(parameters);
        return ssl;
    }

    @Override public String[] getDefaultCipherSuites() { return tls.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return tls.getSupportedCipherSuites(); }
    @Override public Socket createSocket(String name, int port) throws IOException { throw unsupported(); }
    @Override public Socket createSocket(String name, int port, InetAddress local, int localPort) throws IOException { throw unsupported(); }
    @Override public Socket createSocket(InetAddress remote, int port) throws IOException { throw unsupported(); }
    @Override public Socket createSocket(InetAddress remote, int port, InetAddress local, int localPort) throws IOException { throw unsupported(); }
    private IOException unsupported() { return new IOException("Direct TLS requires a validated video socket."); }
}

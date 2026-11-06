package com.modhub.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Finds this machine's LAN IPv4 address and, on request, its public IPv4 address. */
public final class NetInfo {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private NetInfo() {
    }

    /** Best local IPv4 address (prefers 192.168.x.x, 10.x.x.x, 172.16-31.x.x). Empty if none found. */
    public static Optional<String> lanAddress() {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return Optional.empty();
            String fallback = null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) continue;
                    String ip = addr.getHostAddress();
                    if (addr.isSiteLocalAddress()) return Optional.of(ip);
                    if (fallback == null) fallback = ip;
                }
            }
            return Optional.ofNullable(fallback);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Asks an external service (api.ipify.org) for this machine's public IPv4 address.
     * Runs in the background, so the game does not freeze. Only called when the user presses the button.
     */
    public static CompletableFuture<String> publicAddress() {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.ipify.org"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) {
                        throw new IllegalStateException("IP service returned HTTP " + resp.statusCode());
                    }
                    return resp.body().trim();
                });
    }
}

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

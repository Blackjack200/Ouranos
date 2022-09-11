package com.blackjack200.ouranos.utils;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadLocalRandom;

public class Port {
    public static int allocate() {
        return ThreadLocalRandom.current().nextInt(20000, 60000);
    }

    public static InetSocketAddress allocateAddr() {
        return new InetSocketAddress("0.0.0.0", allocate());
    }
}

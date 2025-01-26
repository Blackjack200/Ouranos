package com.blackjack200.ouranos.network.session;

import java.util.UUID;

public record AuthData(String displayName, UUID identity, String xuid) {
}

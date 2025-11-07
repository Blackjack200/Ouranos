package com.github.blackjack200.ouranos.network.session.translate;

public enum TranslationDirection {
    CLIENTBOUND,
    SERVERBOUND;

    public static TranslationDirection fromServerFlag(boolean fromServer) {
        return fromServer ? CLIENTBOUND : SERVERBOUND;
    }

    public boolean isServerbound() {
        return this == SERVERBOUND;
    }

    public boolean isClientbound() {
        return this == CLIENTBOUND;
    }
}

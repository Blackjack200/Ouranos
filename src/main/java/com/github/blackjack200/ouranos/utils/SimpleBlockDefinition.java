package com.github.blackjack200.ouranos.utils;

import lombok.Data;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;

@Data
public class SimpleBlockDefinition implements BlockDefinition {
    private final int runtimeId;

    public SimpleBlockDefinition(int runtimeId) {
        this.runtimeId = runtimeId;
    }

    @Override
    public int getRuntimeId() {
        return this.runtimeId;
    }
}

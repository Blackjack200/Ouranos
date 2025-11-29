package com.github.blackjack200.ouranos.network;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v859.Bedrock_v859;

public class Bedrock_v860 extends Bedrock_v859 {
    public static final BedrockCodec CODEC;

    static {
        CODEC = Bedrock_v859.CODEC.toBuilder().protocolVersion(860).minecraftVersion("1.21.124").build();
    }
}

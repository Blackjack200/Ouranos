package com.blackjack200.ouranos.network.convert;

import cn.hutool.core.io.IoUtil;
import com.blackjack200.ouranos.network.ProtocolInfo;

import java.util.function.BiConsumer;

public class AbstractMapping {
    protected void load(String file, BiConsumer<Integer, String> handler) {
        ProtocolInfo.getPacketCodecs().forEach((codec) -> {
            int protocolId = codec.getProtocolVersion();
            String rawData = IoUtil.readUtf8(getClass().getClassLoader().getResourceAsStream("vanilla/v" + protocolId + "/" + file));
            handler.accept(protocolId, rawData);
        });
    }
}

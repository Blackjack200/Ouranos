package com.blackjack200.ouranos.network.convert;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import com.blackjack200.ouranos.network.ProtocolInfo;

import java.util.function.BiConsumer;

public class AbstractMapping {
    protected void load(String file, BiConsumer<Integer, byte[]> handler) {
        ProtocolInfo.getPacketCodecs().forEach((codec) -> {
            int protocolId = codec.getProtocolVersion();
            for (var c : ProtocolInfo.getPacketCodecs()) {
                String name = "vanilla/v" + protocolId + "/" + file;
                if (FileUtil.exist(name)) {
                    byte[] rawData = IoUtil.readBytes(getClass().getClassLoader().getResourceAsStream(name));
                    handler.accept(protocolId, rawData);
                } else if (FileUtil.exist(file)) {
                    byte[] rawData = IoUtil.readBytes(getClass().getClassLoader().getResourceAsStream(file));
                    handler.accept(protocolId, rawData);
                } else {
                    throw new RuntimeException(file);
                }
            }
        });
    }
}

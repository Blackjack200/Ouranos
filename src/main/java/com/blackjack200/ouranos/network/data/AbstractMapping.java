package com.blackjack200.ouranos.network.data;

import cn.hutool.core.io.FileUtil;
import com.blackjack200.ouranos.network.ProtocolInfo;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.util.function.BiConsumer;

@Log4j2
public class AbstractMapping {
    protected void load(String file, BiConsumer<Integer, InputStream> handler) {
        var exists = ProtocolInfo.getPacketCodecs().stream().filter(id -> FileUtil.exist("vanilla/v" + id.getProtocolVersion() + "/" + file)).toList();

        ProtocolInfo.getPacketCodecs().forEach((codec) -> {
            int protocolId = codec.getProtocolVersion();
            String name = "vanilla/v" + protocolId + "/" + file;
            if (!FileUtil.exist(name)) {
                for (var i = exists.size() - 1; i >= 0; i--) {
                    var codecc = exists.get(i);
                    name = "vanilla/v" + codecc.getProtocolVersion() + "/" + file;
                    if (codecc.getProtocolVersion() <= codec.getProtocolVersion()) {
                        break;
                    }
                }
            }
            var rawData = getClass().getClassLoader().getResourceAsStream(name);
            handler.accept(protocolId, rawData);
        });
    }
}

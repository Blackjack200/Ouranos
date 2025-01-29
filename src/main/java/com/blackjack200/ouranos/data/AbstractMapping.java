package com.blackjack200.ouranos.data;

import cn.hutool.core.io.FileUtil;
import com.blackjack200.ouranos.network.ProtocolInfo;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.net.URL;
import java.util.function.BiConsumer;

@Log4j2
public class AbstractMapping {
    protected void load(String file, BiConsumer<Integer, InputStream> handler) {

        ProtocolInfo.getPacketCodecs().forEach((codec) -> {
            int protocolId = codec.getProtocolVersion();
            String name = lookupAvailableFile(file, protocolId);
            var rawData = getClass().getClassLoader().getResourceAsStream(name);
            //log.info("Loading packet codec {} from {}", codec.getProtocolVersion(), file);
            handler.accept(protocolId, rawData);
        });
    }

    protected static String lookupAvailableFile(String file, int protocolId) {
        var exists = ProtocolInfo.getPacketCodecs().stream().filter(id -> FileUtil.exist("vanilla/v" + id.getProtocolVersion() + "/" + file)).toList();
        String name = "vanilla/v" + protocolId + "/" + file;
        if (!FileUtil.exist(name)) {
            name = file;
        }
        if (!FileUtil.exist(name)) {
            for (var i = exists.size() - 1; i >= 0; i--) {
                var codecc = exists.get(i);
                name = "vanilla/v" + protocolId + "/" + file;
                if (codecc.getProtocolVersion() <= protocolId) {
                    break;
                }
            }
        }
        return name;
    }

    protected void loadFile(String file, BiConsumer<Integer, URL> handler) {
        var exists = ProtocolInfo.getPacketCodecs().stream().filter(id -> FileUtil.exist("vanilla/v" + id.getProtocolVersion() + "/" + file)).toList();

        ProtocolInfo.getPacketCodecs().forEach((codec) -> {
            int protocolId = codec.getProtocolVersion();
            String name = "vanilla/v" + protocolId + "/" + file;
            if (!FileUtil.exist(name)) {
                name = file;
            }
            if (!FileUtil.exist(name)) {
                for (var i = exists.size() - 1; i >= 0; i--) {
                    var codecc = exists.get(i);
                    name = "vanilla/v" + codecc.getProtocolVersion() + "/" + file;
                    if (codecc.getProtocolVersion() <= codec.getProtocolVersion()) {
                        break;
                    }
                }
            }
            var url = getClass().getClassLoader().getResource(name);
            //log.info("Loading packet codec {} from {}", codec.getProtocolVersion(), file);
            handler.accept(protocolId, url);
            // log.info("Loaded packet codec {} from {}", codec.getProtocolVersion(), file);
        });
    }
}

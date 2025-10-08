package com.github.blackjack200.ouranos.network.session;

import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.utils.EncUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.RakChannel;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakUnhandledMessagesQueue;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;
import org.cloudburstmc.protocol.bedrock.codec.v428.Bedrock_v428;
import org.cloudburstmc.protocol.bedrock.netty.codec.FrameIdCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionEncoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import javax.crypto.SecretKey;
import java.net.SocketException;
import java.util.Objects;
import java.util.stream.Stream;

@Log4j2
public class CustomPeer extends BedrockPeer {
    private final boolean packetBuffering;

    public CustomPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        super(channel, sessionFactory);
        this.packetBuffering = Ouranos.getOuranos().getConfig().packet_buffering;
        this.getChannel().pipeline().addAfter(BedrockPacketCodec.NAME, "protocol-error-handler", new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                if (!ctx.channel().isActive()) {
                    return;
                }
                var rootCause = Stream.iterate(cause, Throwable::getCause)
                        .filter(element -> element.getCause() == null)
                        .findFirst()
                        .orElse(cause);
                log.error("Exception in CustomPeer.exceptionCaught", rootCause);
                if (rootCause instanceof SocketException) {
                    ctx.close();
                }
            }
        });
        if (this.getChannel() instanceof RakChannel rakChannel) {
            if (rakChannel.rakPipeline().get(RakUnhandledMessagesQueue.class) != null) {
                rakChannel.rakPipeline().replace(RakUnhandledMessagesQueue.class, RakUnhandledMessagesQueue.NAME, new RakUnhandledMessagesQueueOverride(rakChannel));
            }
        }
    }

    @Override
    public void sendPacket(int senderClientId, int targetClientId, BedrockPacket packet) {
        if (this.packetBuffering) {
            super.sendPacket(senderClientId, targetClientId, packet);
        } else {
            super.sendPacketImmediately(senderClientId, targetClientId, packet);
        }
    }

    public void enableEncryption(SecretKey secretKey) {
        Objects.requireNonNull(secretKey, "secretKey");
        if (!secretKey.getAlgorithm().equals("AES")) {
            throw new IllegalArgumentException("Invalid key algorithm");
        }
        // Check if the codecs exist in the pipeline
        if (this.channel.pipeline().get(BedrockEncryptionEncoder.class) != null ||
                this.channel.pipeline().get(BedrockEncryptionDecoder.class) != null) {
            throw new IllegalStateException("Encryption is already enabled");
        }

        int protocolVersion = this.getCodec().getProtocolVersion();
        boolean useCtr = protocolVersion >= Bedrock_v428.CODEC.getProtocolVersion();

        this.channel.pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionEncoder.NAME,
                new BedrockEncryptionEncoder(secretKey, EncUtils.createCipher(useCtr, true, secretKey)));
        this.channel.pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionDecoder.NAME,
                new BedrockEncryptionDecoder(secretKey, EncUtils.createCipher(useCtr, false, secretKey)));

        log.debug("Encryption enabled for {}", getSocketAddress());
    }
}

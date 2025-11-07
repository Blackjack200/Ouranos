package com.github.blackjack200.ouranos.network.session.translate;

import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import com.github.blackjack200.ouranos.network.session.translate.stage.AdventureSettingsStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.BlockTranslationStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.ChunkTranslationStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.EntityMetadataStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.InventoryStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.ItemStackRequestStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.ItemTranslationStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.MovementStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.PacketFinalizerStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.PacketNormalizationStage;
import com.github.blackjack200.ouranos.network.session.translate.stage.ProtocolTranslationStage;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.cloudburstmc.math.immutable.vector.ImmutableVectorProvider;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.ChatRestrictionLevel;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.packet.*;

@SuppressWarnings("deprecation")
public final class Translate {

    private static final TranslationPipeline PIPELINE =
        TranslationPipeline.builder()
            .addStage(new PacketNormalizationStage())
            .addStage(new ProtocolTranslationStage())
            .addStage(new ItemStackRequestStage())
            .addStage(new ItemTranslationStage())
            .addStage(new BlockTranslationStage())
            .addStage(new ChunkTranslationStage())
            .addStage(new AdventureSettingsStage())
            .addStage(new EntityMetadataStage())
            .addStage(new MovementStage())
            .addStage(new InventoryStage())
            .addStage(new PacketFinalizerStage())
            .build();

    private Translate() {}

    public static Collection<BedrockPacket> translate(
        int input,
        int output,
        boolean fromServer,
        OuranosProxySession player,
        BedrockPacket packet
    ) {
        if (input == output) {
            return List.of(packet);
        }

        var direction = TranslationDirection.fromServerFlag(fromServer);
        var context = new TranslationContext(
            input,
            output,
            direction,
            player,
            null
        );
        return PIPELINE.run(context, packet);
    }

    public static void writeProtocolDefault(
        OuranosProxySession session,
        BedrockPacket packet
    ) {
        var provider = new ImmutableVectorProvider();
        if (packet instanceof StartGamePacket pk) {
            pk.setServerId(Optional.ofNullable(pk.getServerId()).orElse(""));
            pk.setWorldId(Optional.ofNullable(pk.getWorldId()).orElse(""));
            pk.setScenarioId(
                Optional.ofNullable(pk.getScenarioId()).orElse("")
            );
            pk.setChatRestrictionLevel(
                Optional.ofNullable(pk.getChatRestrictionLevel()).orElse(
                    ChatRestrictionLevel.NONE
                )
            );
            pk.setPlayerPropertyData(
                Optional.ofNullable(pk.getPlayerPropertyData()).orElse(
                    NbtMap.EMPTY
                )
            );
            pk.setWorldTemplateId(
                Optional.ofNullable(pk.getWorldTemplateId()).orElse(
                    UUID.randomUUID()
                )
            );
            pk.setOwnerId(Objects.requireNonNullElse(pk.getOwnerId(), ""));
            pk.setAuthoritativeMovementMode(
                Objects.requireNonNullElse(
                    pk.getAuthoritativeMovementMode(),
                    AuthoritativeMovementMode.SERVER_WITH_REWIND
                )
            );
        }
        if (packet instanceof PlayerAuthInputPacket pk) {
            pk.setDelta(
                Objects.requireNonNullElseGet(pk.getDelta(), () ->
                    provider.createVector3f(0, 0, 0)
                )
            );
            pk.setMotion(
                Objects.requireNonNullElseGet(pk.getMotion(), () ->
                    provider.createVector2f(0, 0)
                )
            );
            pk.setRawMoveVector(
                Objects.requireNonNullElseGet(pk.getRawMoveVector(), () ->
                    provider.createVector2f(0, 0)
                )
            );
            pk.setInputMode(
                Objects.requireNonNullElse(
                    pk.getInputMode(),
                    session.movement().inputMode
                )
            );
            pk.setPlayMode(
                Objects.requireNonNullElse(
                    pk.getPlayMode(),
                    ClientPlayMode.NORMAL
                )
            );
            pk.setInputInteractionModel(
                Objects.requireNonNullElse(
                    pk.getInputInteractionModel(),
                    InputInteractionModel.TOUCH
                )
            );
            pk.setAnalogMoveVector(
                Objects.requireNonNullElse(
                    pk.getAnalogMoveVector(),
                    provider.createVector2f(0, 0)
                )
            );
            pk.setInteractRotation(
                Objects.requireNonNullElseGet(pk.getInteractRotation(), () ->
                    provider.createVector2f(0, 0)
                )
            );
            pk.setCameraOrientation(
                Objects.requireNonNullElseGet(pk.getCameraOrientation(), () ->
                    provider.createVector3f(0, 0, 0)
                )
            );
        }
        if (packet instanceof AddPlayerPacket pk) {
            pk.setGameType(
                Optional.ofNullable(pk.getGameType()).orElse(GameType.DEFAULT)
            );
        }
        if (packet instanceof ModalFormResponsePacket pk) {
            if (pk.getFormData() == null) {
                pk.setFormData("null");
            }
        }
        if (packet instanceof ResourcePacksInfoPacket pk) {
            pk.setWorldTemplateId(
                Objects.requireNonNullElseGet(
                    pk.getWorldTemplateId(),
                    UUID::randomUUID
                )
            );
            pk.setWorldTemplateVersion(
                Objects.requireNonNullElse(
                    pk.getWorldTemplateVersion(),
                    "0.0.0"
                )
            );
        }
    }
}

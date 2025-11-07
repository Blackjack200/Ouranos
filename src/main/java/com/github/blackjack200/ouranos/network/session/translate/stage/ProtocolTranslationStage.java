package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.ProtocolInfo;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.github.blackjack200.ouranos.network.convert.biome.BiomeDefinitionRegistry;
import com.github.blackjack200.ouranos.network.session.translate.Translate;
import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import java.awt.Color;
import java.util.*;
import lombok.val;
import org.cloudburstmc.math.immutable.vector.ImmutableVectorProvider;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.v408.Bedrock_v408;
import org.cloudburstmc.protocol.bedrock.codec.v419.Bedrock_v419;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v544.Bedrock_v544;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.codec.v560.Bedrock_v560;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitions;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.*;

public final class ProtocolTranslationStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (context.protocolsAreEqual()) {
            return;
        }

        Translate.writeProtocolDefault(context.session(), packet);
        var input = context.inputProtocol();
        var output = context.outputProtocol();
        var fromServer = context.fromServer();
        var player = context.session();

        if (packet instanceof StartGamePacket pk) {
            if (output >= Bedrock_v776.CODEC.getProtocolVersion()) {
                var newPk = new ItemComponentPacket();
                List<ItemDefinition> def = ItemTypeDictionary.getInstance(
                    output
                )
                    .getEntries()
                    .entrySet()
                    .stream()
                    .map(entry -> entry.getValue().toDefinition(entry.getKey()))
                    .toList();
                newPk.getItems().addAll(def);
                packets.add(newPk);
            }
        }
        if (packet instanceof ItemStackResponsePacket pk) {
            var translated = pk
                .getEntries()
                .stream()
                .map(entry -> {
                    if (
                        input >= Bedrock_v419.CODEC.getProtocolVersion() &&
                        output < Bedrock_v419.CODEC.getProtocolVersion()
                    ) {
                        return new ItemStackResponse(
                            entry
                                .getResult()
                                .equals(ItemStackResponseStatus.OK),
                            entry.getRequestId(),
                            entry.getContainers()
                        );
                    }
                    return entry;
                })
                .toList();
            pk.getEntries().clear();
            pk.getEntries().addAll(translated);
        }
        val provider = new ImmutableVectorProvider();

        if (input < Bedrock_v729.CODEC.getProtocolVersion()) {
            if (packet instanceof TransferPacket pk) {
                pk.setReloadWorld(true);
            }
        }
        if (input < Bedrock_v712.CODEC.getProtocolVersion()) {
            if (packet instanceof InventoryTransactionPacket pk) {
                pk.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
                pk.setClientInteractPrediction(
                    ItemUseTransaction.PredictedResult.SUCCESS
                );
            } else if (packet instanceof PlayerAuthInputPacket pk) {
                pk.setRawMoveVector(provider.createVector2f(0, 0));
                var transaction = pk.getItemUseTransaction();
                if (transaction != null) {
                    transaction.setTriggerType(
                        ItemUseTransaction.TriggerType.PLAYER_INPUT
                    );
                    transaction.setClientInteractPrediction(
                        ItemUseTransaction.PredictedResult.SUCCESS
                    );
                }
            }
        }

        if (input < Bedrock_v685.CODEC.getProtocolVersion()) {
            if (packet instanceof ContainerClosePacket pk) {
                pk.setType(ContainerType.NONE);
            }
        }
        if (input < Bedrock_v671.CODEC.getProtocolVersion()) {
            if (packet instanceof ResourcePackStackPacket pk) {
                pk.setHasEditorPacks(false);
            }
        }
        if (input < Bedrock_v649.CODEC.getProtocolVersion()) {
            if (packet instanceof LevelChunkPacket pk) {
                pk.setDimension(0);
            } else if (packet instanceof PlayerListPacket pk) {
                for (var e : pk.getEntries()) {
                    e.setSubClient(false);
                }
            }
        }

        if (input < Bedrock_v589.CODEC.getProtocolVersion()) {
            if (packet instanceof EmotePacket pk) {
                pk.setXuid("");
                pk.setPlatformId("");
                pk.setEmoteDuration(20);
            }
        }
        if (input < Bedrock_v575.CODEC.getProtocolVersion()) {
            if (packet instanceof PlayerAuthInputPacket pk) {
                pk.setAnalogMoveVector(provider.createVector2f(0, 0));
            }
        }
        if (input < Bedrock_v544.CODEC.getProtocolVersion()) {
            if (packet instanceof ModalFormResponsePacket pk) {
                if (player.getLastFormId() == pk.getFormId()) {
                    packets.clear();
                }
                pk.setCancelReason(Optional.empty());
                player.setLastFormId(pk.getFormId());
            }
        }
        if (input < Bedrock_v527.CODEC.getProtocolVersion()) {
            if (packet instanceof PlayerAuthInputPacket pk) {
                pk.setInputInteractionModel(
                    Optional.ofNullable(pk.getInputInteractionModel()).orElse(
                        InputInteractionModel.CLASSIC
                    )
                );
            } else if (packet instanceof PlayerActionPacket pk) {
                pk.setResultPosition(pk.getBlockPosition());
            }
        }
        if (packet instanceof PlayerSkinPacket pk) {
            pk.setSkin(
                pk
                    .getSkin()
                    .toBuilder()
                    .geometryDataEngineVersion(
                        Objects.requireNonNull(
                            ProtocolInfo.getPacketCodec(output)
                        ).getMinecraftVersion()
                    )
                    .build()
            );
        }
        if (packet instanceof PlayerListPacket pk) {
            for (var e : pk.getEntries()) {
                e.setColor(
                    Objects.requireNonNullElse(e.getColor(), Color.WHITE)
                );
                if (e.getSkin() != null) {
                    e.setSkin(
                        e
                            .getSkin()
                            .toBuilder()
                            .geometryDataEngineVersion(
                                Objects.requireNonNull(
                                    ProtocolInfo.getPacketCodec(output)
                                ).getMinecraftVersion()
                            )
                            .build()
                    );
                }
            }
        }
        if (packet instanceof BiomeDefinitionListPacket pk) {
            if (output >= Bedrock_v800.CODEC.getProtocolVersion()) {
                if (pk.getBiomes() == null && pk.getDefinitions() != null) {
                    BiomeDefinitions defs = new BiomeDefinitions(
                        new HashMap<>()
                    );
                    pk
                        .getDefinitions()
                        .forEach((id, n) -> {
                            var def = BiomeDefinitionRegistry.getInstance(
                                input
                            ).fromStringId(id);
                            if (def != null) {
                                defs.getDefinitions().put(id, def);
                            }
                        });
                    pk.setBiomes(defs);
                }
            } else {
                if (pk.getBiomes() != null && pk.getDefinitions() == null) {
                    pk.setDefinitions(
                        downgradeBiomeDefinition(
                            output,
                            pk.getBiomes().getDefinitions()
                        )
                    );
                }
            }
        }
    }

    private static NbtMap downgradeBiomeDefinition(
        int output,
        Map<String, BiomeDefinitionData> definitions
    ) {
        var builder = NbtMap.builder();
        if (definitions.isEmpty()) {
            definitions = BiomeDefinitionRegistry.getInstance(
                output
            ).getEntries();
        }
        definitions.forEach((id, def) -> {
            var d = NbtMap.builder();
            d.putString("name_hash", id);
            d.putFloat("temperature", def.getTemperature());
            d.putFloat("downfall", def.getDownfall());
            d.putBoolean("rain", def.isRain());
            builder.putCompound(id, d.build());
        });
        return builder.build();
    }
}

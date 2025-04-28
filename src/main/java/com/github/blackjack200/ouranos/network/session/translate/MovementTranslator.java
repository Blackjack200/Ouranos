package com.github.blackjack200.ouranos.network.session.translate;

import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.codec.v388.Bedrock_v388;
import org.cloudburstmc.protocol.bedrock.codec.v389.Bedrock_v389;
import org.cloudburstmc.protocol.bedrock.codec.v390.Bedrock_v390;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.Collection;

@Log4j2
public class MovementTranslator {
    public static void rewriteMovement(int input, int output, boolean fromServer, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        if (!fromServer) {
            translateClientMovement(input, output, player, p, list);
        } else {
            hack_1_14_30_movement_bug(output, p, list);
        }
    }

    private static void hack_1_14_30_movement_bug(int output, BedrockPacket p, Collection<BedrockPacket> list) {
        if (output >= Bedrock_v389.CODEC.getProtocolVersion() && output <= Bedrock_v390.CODEC.getProtocolVersion()) {
            if (p instanceof MoveEntityAbsolutePacket pk) {
                list.clear();
                var newPk = new MovePlayerPacket();
                newPk.setRuntimeEntityId(pk.getRuntimeEntityId());
                newPk.setPosition(pk.getPosition());
                newPk.setRotation(pk.getRotation());
                newPk.setOnGround(pk.isOnGround());
                if (pk.isTeleported()) {
                    newPk.setMode(MovePlayerPacket.Mode.TELEPORT);
                } else {
                    newPk.setMode(MovePlayerPacket.Mode.NORMAL);
                }
                list.add(newPk);
            }
        }
    }

    private static void translateClientMovement(int input, int output, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        var clientAuthoritative = input < Bedrock_v388.CODEC.getProtocolVersion();
        var outputAuthoritative = output >= Bedrock_v388.CODEC.getProtocolVersion();
        if (outputAuthoritative) {
            if (p instanceof MovePlayerPacket pk) {
                player.input.position = pk.getPosition();
                player.input.rotation = pk.getRotation();
                list.clear();
                list.addAll(player.tickMovement());
            } else if (p instanceof InventoryTransactionPacket pk) {
                //log.info(pk);
            } else if (p instanceof ItemStackRequestPacket pk) {
                player.input.requests.addAll(pk.getRequests());
                list.clear();
                list.addAll(player.tickMovement());
            } else if (p instanceof PlayerActionPacket pk) {
                boolean processeed = true;
                switch (pk.getAction()) {
                    case START_SPRINT -> player.input.inputs.add(PlayerAuthInputData.START_SPRINTING);
                    case STOP_SPRINT -> player.input.inputs.add(PlayerAuthInputData.STOP_SPRINTING);
                    case START_SNEAK -> player.input.inputs.add(PlayerAuthInputData.START_SNEAKING);
                    case STOP_SNEAK -> player.input.inputs.add(PlayerAuthInputData.STOP_SNEAKING);
                    case START_SWIMMING -> player.input.inputs.add(PlayerAuthInputData.START_SWIMMING);
                    case STOP_SWIMMING -> player.input.inputs.add(PlayerAuthInputData.STOP_SWIMMING);
                    case START_GLIDE -> player.input.inputs.add(PlayerAuthInputData.START_GLIDING);
                    case STOP_GLIDE -> player.input.inputs.add(PlayerAuthInputData.STOP_GLIDING);
                    case START_CRAWLING -> player.input.inputs.add(PlayerAuthInputData.START_CRAWLING);
                    case STOP_CRAWLING -> player.input.inputs.add(PlayerAuthInputData.STOP_CRAWLING);
                    case START_FLYING -> player.input.inputs.add(PlayerAuthInputData.START_FLYING);
                    case STOP_FLYING -> player.input.inputs.add(PlayerAuthInputData.STOP_FLYING);
                    case JUMP -> player.input.inputs.add(PlayerAuthInputData.START_JUMPING);
                    default -> processeed = false;
                }
                if (processeed) {
                    list.clear();
                    list.addAll(player.tickMovement());
                }
            }

            if (input < Bedrock_v594.CODEC.getProtocolVersion()) {
                if (p instanceof LevelSoundEventPacket pk) {
                    if (pk.getSound().equals(SoundEvent.ATTACK_NODAMAGE)) {
                        player.input.inputs.add(PlayerAuthInputData.MISSED_SWING);
                        list.clear();
                        list.addAll(player.tickMovement());
                    }
                }
            }
        }

        if (p instanceof PlayerAuthInputPacket pk) {
            player.input.position = pk.getPosition();
            player.input.rotation = pk.getRotation();
            player.input.inputs.addAll(pk.getInputData());
            if (pk.getItemStackRequest() != null) {
                player.input.requests.add(pk.getItemStackRequest());
            }
            if (pk.getItemStackRequest() != null) {
                player.input.transactions.add(pk.getItemUseTransaction());
            }
            player.input.blocks.addAll(pk.getPlayerActions());
            list.clear();
            list.addAll(player.tickMovement());
        }
    }

    private static ItemUseTransaction convertItemUseTx(InventoryTransactionPacket pk) {
        var tx = new ItemUseTransaction();
        tx.setActionType(pk.getActionType());
        tx.setBlockDefinition(pk.getBlockDefinition());
        tx.setBlockFace(pk.getBlockFace());
        tx.setBlockPosition(pk.getBlockPosition());
        tx.setBlockDefinition(pk.getBlockDefinition());
        tx.setClickPosition(pk.getClickPosition());
        tx.setClientInteractPrediction(pk.getClientInteractPrediction());
        tx.setHotbarSlot(pk.getHotbarSlot());
        tx.setItemInHand(pk.getItemInHand());
        tx.setLegacyRequestId(pk.getLegacyRequestId());
        tx.setPlayerPosition(pk.getPlayerPosition());
        tx.setTriggerType(pk.getTriggerType());
        tx.setUsingNetIds(pk.isUsingNetIds());
        return tx;
    }
}

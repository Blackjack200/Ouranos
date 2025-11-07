package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import java.util.*;
import java.util.function.BiFunction;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v534.Bedrock_v534;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.AdventureSetting;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.*;

public final class AdventureSettingsStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (context.protocolsAreEqual()) {
            return;
        }
        var output = context.outputProtocol();
        var fromServer = context.fromServer();
        var player = context.session();

        handleClientSidePackets(output, fromServer, player, packet, packets);
        downgradeAbilities(output, packet, packets);
        upgradeAdventureSettings(output, fromServer, packet, packets);
    }

    private static void handleClientSidePackets(
        int output,
        boolean fromServer,
        OuranosProxySession player,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (fromServer) {
            return;
        }
        if (packet instanceof RequestAbilityPacket pk) {
            rewriteFlying(
                output,
                player,
                packets,
                pk.getAbility() == Ability.FLYING
            );
        } else if (packet instanceof AdventureSettingsPacket pk) {
            rewriteFlying(
                output,
                player,
                packets,
                pk.getSettings().contains(AdventureSetting.FLYING)
            );
        } else if (
            output < Bedrock_v618.CODEC.getProtocolVersion() &&
            packet instanceof PlayerActionPacket pk
        ) {
            if (pk.getAction() == PlayerActionType.START_FLYING) {
                rewriteFlying(output, player, packets, true);
            } else if (pk.getAction() == PlayerActionType.STOP_FLYING) {
                rewriteFlying(output, player, packets, false);
            }
        }
    }

    private static void downgradeAbilities(
        int output,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (
            output >= Bedrock_v534.CODEC.getProtocolVersion() ||
            !(packet instanceof UpdateAbilitiesPacket pk)
        ) {
            return;
        }
        var newPk = new AdventureSettingsPacket();
        newPk.setUniqueEntityId(pk.getUniqueEntityId());
        newPk.setCommandPermission(pk.getCommandPermission());
        newPk.setPlayerPermission(pk.getPlayerPermission());
        var abilities = pk.getAbilityLayers().get(0).getAbilityValues();
        BiFunction<Ability, AdventureSetting, Void> mapper = (
            ability,
            setting
        ) -> {
            if (abilities.contains(ability)) {
                newPk.getSettings().add(setting);
            }
            return null;
        };
        mapper.apply(Ability.BUILD, AdventureSetting.BUILD);
        mapper.apply(Ability.MINE, AdventureSetting.MINE);
        mapper.apply(
            Ability.DOORS_AND_SWITCHES,
            AdventureSetting.DOORS_AND_SWITCHES
        );
        mapper.apply(Ability.OPEN_CONTAINERS, AdventureSetting.OPEN_CONTAINERS);
        mapper.apply(Ability.ATTACK_PLAYERS, AdventureSetting.ATTACK_PLAYERS);
        mapper.apply(Ability.ATTACK_MOBS, AdventureSetting.ATTACK_MOBS);
        mapper.apply(Ability.OPERATOR_COMMANDS, AdventureSetting.OPERATOR);
        mapper.apply(Ability.TELEPORT, AdventureSetting.TELEPORT);
        mapper.apply(Ability.FLYING, AdventureSetting.FLYING);
        mapper.apply(Ability.MAY_FLY, AdventureSetting.MAY_FLY);
        mapper.apply(Ability.MUTED, AdventureSetting.MUTED);
        mapper.apply(Ability.WORLD_BUILDER, AdventureSetting.WORLD_BUILDER);
        mapper.apply(Ability.NO_CLIP, AdventureSetting.NO_CLIP);
        if (
            !abilities.contains(Ability.MINE) &&
            !abilities.contains(Ability.BUILD)
        ) {
            newPk.getSettings().add(AdventureSetting.WORLD_IMMUTABLE);
            newPk.getSettings().remove(AdventureSetting.BUILD);
            newPk.getSettings().remove(AdventureSetting.MINE);
        }
        packets.add(newPk);
    }

    private static void upgradeAdventureSettings(
        int output,
        boolean fromServer,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (
            output <= Bedrock_v554.CODEC.getProtocolVersion() ||
            !fromServer ||
            !(packet instanceof AdventureSettingsPacket pk)
        ) {
            return;
        }
        var newPk = new UpdateAbilitiesPacket();
        newPk.setUniqueEntityId(pk.getUniqueEntityId());
        newPk.setPlayerPermission(pk.getPlayerPermission());
        newPk.setCommandPermission(pk.getCommandPermission());
        var layer = new AbilityLayer();
        layer.setLayerType(AbilityLayer.Type.BASE);
        layer.setFlySpeed(0.05f);
        layer.setWalkSpeed(0.1f);

        var settings = pk.getSettings();
        Collections.addAll(layer.getAbilitiesSet(), Ability.values());
        newPk.setAbilityLayers(List.of(layer));
        BiFunction<AdventureSetting, Ability, Void> mapper = (
            setting,
            ability
        ) -> {
            if (settings.contains(setting)) {
                layer.getAbilityValues().add(ability);
            }
            return null;
        };
        mapper.apply(AdventureSetting.BUILD, Ability.BUILD);
        mapper.apply(AdventureSetting.MINE, Ability.MINE);
        mapper.apply(
            AdventureSetting.DOORS_AND_SWITCHES,
            Ability.DOORS_AND_SWITCHES
        );
        mapper.apply(AdventureSetting.OPEN_CONTAINERS, Ability.OPEN_CONTAINERS);
        mapper.apply(AdventureSetting.ATTACK_PLAYERS, Ability.ATTACK_PLAYERS);
        mapper.apply(AdventureSetting.ATTACK_MOBS, Ability.ATTACK_MOBS);
        mapper.apply(AdventureSetting.OPERATOR, Ability.OPERATOR_COMMANDS);
        mapper.apply(AdventureSetting.TELEPORT, Ability.TELEPORT);
        mapper.apply(AdventureSetting.FLYING, Ability.FLYING);
        mapper.apply(AdventureSetting.MAY_FLY, Ability.MAY_FLY);
        mapper.apply(AdventureSetting.MUTED, Ability.MUTED);
        mapper.apply(AdventureSetting.WORLD_BUILDER, Ability.WORLD_BUILDER);
        mapper.apply(AdventureSetting.NO_CLIP, Ability.NO_CLIP);
        packets.add(newPk);

        var statePacket = new UpdateAdventureSettingsPacket();
        statePacket.setAutoJump(settings.contains(AdventureSetting.AUTO_JUMP));
        statePacket.setImmutableWorld(
            settings.contains(AdventureSetting.WORLD_IMMUTABLE)
        );
        statePacket.setNoMvP(settings.contains(AdventureSetting.NO_MVP));
        statePacket.setNoPvM(settings.contains(AdventureSetting.NO_PVM));
        statePacket.setShowNameTags(
            settings.contains(AdventureSetting.SHOW_NAME_TAGS)
        );
        packets.add(statePacket);
    }

    private static void rewriteFlying(
        int output,
        OuranosProxySession player,
        Collection<BedrockPacket> packets,
        boolean flying
    ) {
        if (output < Bedrock_v527.CODEC.getProtocolVersion()) {
            var newPk = new AdventureSettingsPacket();
            newPk.setUniqueEntityId(player.getUniqueEntityId());
            if (flying) {
                newPk.getSettings().add(AdventureSetting.FLYING);
            }
            packets.add(newPk);
        } else {
            var newPk = new RequestAbilityPacket();
            newPk.setAbility(Ability.FLYING);
            newPk.setType(Ability.Type.BOOLEAN);
            newPk.setBoolValue(flying);
            packets.add(newPk);
        }
    }
}

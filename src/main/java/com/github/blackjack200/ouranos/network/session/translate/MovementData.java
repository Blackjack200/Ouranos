package com.github.blackjack200.ouranos.network.session.translate;

import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.ArrayDeque;
import java.util.Queue;

public class MovementData {
    public Vector3f position;
    public Vector3f rotation;

    public Queue<PlayerAuthInputData> inputs = new ArrayDeque<>(16);
    public Queue<ItemUseTransaction> transactions = new ArrayDeque<>(16);
    public Queue<ItemStackRequest> requests = new ArrayDeque<>(16);
    public Queue<PlayerBlockActionData> blocks = new ArrayDeque<>(16);
    public InputMode inputMode = InputMode.UNDEFINED;

    public PlayerAuthInputPacket tick(int upstreamProtocolId, OuranosProxySession player) {
        var input = new PlayerAuthInputPacket();
        input.setInputMode(inputMode);
        input.setPosition(position);
        input.setRotation(rotation);
        input.getInputData().addAll(inputs);
        inputs.clear();
        var tx = transactions.poll();
        if (tx != null) {
            input.getInputData().add(PlayerAuthInputData.PERFORM_ITEM_INTERACTION);
            input.setItemUseTransaction(tx);
        }
        var req = requests.poll();
        if (req != null) {
            input.getInputData().add(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST);
            input.setItemStackRequest(req);
        }
        if (!blocks.isEmpty()) {
            input.getInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
            input.getPlayerActions().addAll(blocks);
            blocks.clear();
        }
        Translate.writeProtocolDefault(player, input);
        return input;
    }
}

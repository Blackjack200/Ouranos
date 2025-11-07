package com.github.blackjack200.ouranos.network.session.translate.stage;

import com.github.blackjack200.ouranos.network.session.translate.TranslationContext;
import com.github.blackjack200.ouranos.network.session.translate.TranslationStage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import lombok.val;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.*;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;

public final class ItemStackRequestStage implements TranslationStage {

    @Override
    public void apply(
        TranslationContext context,
        BedrockPacket packet,
        Collection<BedrockPacket> packets
    ) {
        if (
            context.protocolsAreEqual() ||
            !(packet instanceof ItemStackRequestPacket pk)
        ) {
            return;
        }
        val newRequests = new ArrayList<ItemStackRequest>(
            pk.getRequests().size()
        );
        for (val req : pk.getRequests()) {
            val actions = req.getActions();
            val newActions = new ArrayList<ItemStackRequestAction>(
                actions.length
            );
            for (val action : actions) {
                if (action instanceof TakeAction a) {
                    newActions.add(
                        new TakeAction(
                            a.getCount(),
                            translateSlot(a.getSource()),
                            translateSlot(a.getDestination())
                        )
                    );
                } else if (action instanceof ConsumeAction a) {
                    newActions.add(
                        new ConsumeAction(
                            a.getCount(),
                            translateSlot(a.getSource())
                        )
                    );
                } else if (action instanceof DestroyAction a) {
                    newActions.add(
                        new DestroyAction(
                            a.getCount(),
                            translateSlot(a.getSource())
                        )
                    );
                } else if (action instanceof DropAction a) {
                    newActions.add(
                        new DropAction(
                            a.getCount(),
                            translateSlot(a.getSource()),
                            a.isRandomly()
                        )
                    );
                } else if (action instanceof PlaceAction a) {
                    newActions.add(
                        new PlaceAction(
                            a.getCount(),
                            translateSlot(a.getSource()),
                            translateSlot(a.getDestination())
                        )
                    );
                } else if (action instanceof SwapAction a) {
                    newActions.add(
                        new SwapAction(
                            translateSlot(a.getSource()),
                            translateSlot(a.getDestination())
                        )
                    );
                } else {
                    newActions.add(action);
                }
            }
            newRequests.add(
                new ItemStackRequest(
                    req.getRequestId(),
                    newActions.toArray(new ItemStackRequestAction[0]),
                    req.getFilterStrings()
                )
            );
        }
        pk.getRequests().clear();
        pk.getRequests().addAll(newRequests);
    }

    @SuppressWarnings("deprecation")
    private static ItemStackRequestSlotData translateSlot(
        ItemStackRequestSlotData data
    ) {
        return new ItemStackRequestSlotData(
            data.getContainer(),
            data.getSlot(),
            data.getStackNetworkId(),
            Optional.ofNullable(data.getContainerName()).orElse(
                new FullContainerName(data.getContainer(), 0)
            )
        );
    }
}

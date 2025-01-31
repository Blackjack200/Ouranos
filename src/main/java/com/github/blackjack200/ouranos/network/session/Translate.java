package com.github.blackjack200.ouranos.network.session;

import com.github.blackjack200.ouranos.network.convert.BlockStateDictionary;
import com.github.blackjack200.ouranos.network.convert.ChunkRewriteException;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.github.blackjack200.ouranos.network.convert.TypeConverter;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.math.immutable.vector.ImmutableVectorProvider;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Log4j2
public class Translate {

    public static BedrockPacket translate(int input, int output, OuranosPlayer player, BedrockPacket p) {
        val barrierNamespaceId = "minecraft:info_update";
        val barrier = ItemData.builder()
                .definition(new SimpleItemDefinition(barrierNamespaceId, ItemTypeDictionary.getInstance(output).fromStringId(barrierNamespaceId), false))
                .count(1)
                .blockDefinition(() -> BlockStateDictionary.getInstance(output).getFallback())
                .build();

        if (p instanceof ResourcePackStackPacket pk) {
            pk.setGameVersion("*");
        } else if (p instanceof ClientCacheStatusPacket pk) {
            //TODO forcibly disable client blob caches for security
            pk.setSupported(false);
        } else if (p instanceof InventoryContentPacket pk) {
            val contents = new ArrayList<>(pk.getContents());
            for (int i = 0; i < contents.size(); i++) {
                var item = TypeConverter.translateItemData(input, output, contents.get(i));
                if (item != null) {
                    contents.set(i, item);
                } else {
                    contents.set(i, barrier);
                }
            }
            pk.setContents(contents);
            return pk;
        } else if (p instanceof CraftingDataPacket pk) {
           /* var newCraftingData = new ArrayList<RecipeData>(pk.getCraftingData().size());
            for (var d : pk.getCraftingData()) {
                try {
                    newCraftingData.add(translateRecipeData(input, output, d));
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
           // pk.getCraftingData().clear();
           // pk.getCraftingData().addAll(newCraftingData);
           // pk.getContainerMixData().clear();
           // pk.getMaterialReducers().clear();
           // pk.getPotionMixData().clear();
             pk.setCleanRecipes(true);*/
            pk.getPotionMixData().clear();
            pk.getMaterialReducers().clear();
            pk.getCraftingData().clear();
            pk.getContainerMixData().clear();
            pk.setCleanRecipes(true);
        } else if (p instanceof CreativeContentPacket pk) {
            val contents = new ArrayList<ItemData>();
            var j = -1;
            for (val i : pk.getContents()) {
                j++;
                val item = TypeConverter.translateItemData(input, output, i);
                if (item != null) {
                    contents.add(item.toBuilder().usingNetId(true).netId(j).build());
                }
            }
            pk.setContents(contents.toArray(new ItemData[0]));
            return pk;
        } else if (p instanceof AddItemEntityPacket pk) {
            pk.setItemInHand(TypeConverter.translateItemData(input, output, pk.getItemInHand()));
        } else if (p instanceof InventorySlotPacket pk) {
            pk.setItem(TypeConverter.translateItemData(input, output, pk.getItem()));
            if (pk.getStorageItem() != null) {
                pk.setStorageItem(TypeConverter.translateItemData(input, output, pk.getStorageItem()));
            }
        }

        rewriteProtocol(input, output, player, p);
        rewriteChunk(input, output, player, p);
        rewriteBlock(input, output, player, p);

        return p;
    }

    private static RecipeData translateRecipeData(int input, int output, RecipeData d) {
        if (d instanceof ShapelessRecipeData da) {
            var newIngredients = da.getIngredients().stream().map((i) -> translateItemDescriptorWithCount(input, output, i)).filter(Objects::nonNull).toList();
            da.getIngredients().clear();
            da.getIngredients().addAll(newIngredients);
            var newResults = da.getResults().stream().map((i) -> TypeConverter.translateItemData(input, output, i)).filter(Objects::nonNull).toList();
            da.getResults().clear();
            da.getResults().addAll(newResults);
            return da;
        }
        if (d instanceof FurnaceRecipeData da) {
            return FurnaceRecipeData.of(da.getType(), da.getInputId(), da.getInputData(), da.getResult(), da.getTag());
        } else if (d instanceof MultiRecipeData da) {
            return da;
        } else if (d instanceof ShapedRecipeData da) {
            var newIngredients = da.getIngredients().stream().map((i) -> translateItemDescriptorWithCount(input, output, i)).filter(Objects::nonNull).toList();
            da.getIngredients().clear();
            da.getIngredients().addAll(newIngredients);
            var newResults = da.getResults().stream().map((i) -> TypeConverter.translateItemData(input, output, i)).filter(Objects::nonNull).toList();
            da.getResults().clear();
            da.getResults().addAll(newResults);
            return da;
        } else if (d instanceof SmithingTransformRecipeData da) {
            return SmithingTransformRecipeData.of(
                    da.getId(),
                    translateItemDescriptorWithCount(input, output, da.getTemplate()),
                    translateItemDescriptorWithCount(input, output, da.getBase()),
                    translateItemDescriptorWithCount(input, output, da.getAddition()),
                    TypeConverter.translateItemData(input, output, da.getResult()),
                    da.getTag(),
                    da.getNetId()
            );
        } else if (d instanceof SmithingTrimRecipeData da) {
            return SmithingTrimRecipeData.of(
                    da.getId(),
                    translateItemDescriptorWithCount(input, output, da.getTemplate()),
                    translateItemDescriptorWithCount(input, output, da.getBase()),
                    translateItemDescriptorWithCount(input, output, da.getAddition()),
                    da.getTag(),
                    da.getNetId()
            );
        }
        throw new RuntimeException("Unknown recipe type " + d.getType());
    }

    private static ItemDescriptorWithCount translateItemDescriptorWithCount(int input, int output, ItemDescriptorWithCount i) {
        var descriptor = TypeConverter.translateItemDescriptor(input, output, i.getDescriptor());
        if (descriptor == null) {
            return null;
        }
        return new ItemDescriptorWithCount(descriptor, i.getCount());
    }

    private static void rewriteProtocol(int input, int output, OuranosPlayer player, BedrockPacket p) {
        val provider = new ImmutableVectorProvider();
        if (input < Bedrock_v766.CODEC.getProtocolVersion()) {
            if (p instanceof ResourcePacksInfoPacket pk) {
                pk.setWorldTemplateId(UUID.randomUUID());
                pk.setWorldTemplateVersion("0.0.0");
            }
        }
        if (input < Bedrock_v748.CODEC.getProtocolVersion()) {
            if (p instanceof PlayerAuthInputPacket pk) {
                pk.setInteractRotation(provider.createVector2f(0, 0));
                pk.setCameraOrientation(provider.createVector3f(0, 0, 0));
            }
        }
        if (input < Bedrock_v729.CODEC.getProtocolVersion()) {
            if (p instanceof TransferPacket pk) {
                pk.setReloadWorld(true);
            }
        }
        if (input < Bedrock_v712.CODEC.getProtocolVersion()) {
            if (p instanceof InventoryTransactionPacket pk) {
                pk.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
                pk.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
                var newActions = new ArrayList<InventoryActionData>(pk.getActions().size());
                for (var action : pk.getActions()) {
                    newActions.add(new InventoryActionData(action.getSource(), action.getSlot(), TypeConverter.translateItemData(input, output, action.getFromItem()), TypeConverter.translateItemData(input, output, action.getToItem()), action.getStackNetworkId()));
                }
                pk.getActions().clear();
                pk.getActions().addAll(newActions);
                switch (pk.getTransactionType()) {
                    case ITEM_USE:
                        pk.setBlockDefinition(TypeConverter.translateBlockDefinition(input, output, pk.getBlockDefinition()));
                        break;
                    case ITEM_USE_ON_ENTITY:
                    case ITEM_RELEASE:
                        pk.setItemInHand(TypeConverter.translateItemData(input, output, pk.getItemInHand()));
                        break;
                }
            } else if (p instanceof MobEquipmentPacket pk) {
                pk.setItem(TypeConverter.translateItemData(input, output, pk.getItem()));
            } else if (p instanceof MobArmorEquipmentPacket pk) {
                pk.setBody(TypeConverter.translateItemData(input, output, pk.getBody()));
                pk.setChestplate(TypeConverter.translateItemData(input, output, pk.getChestplate()));
                pk.setHelmet(TypeConverter.translateItemData(input, output, pk.getHelmet()));
                pk.setBoots(TypeConverter.translateItemData(input, output, pk.getBoots()));
                pk.setLeggings(TypeConverter.translateItemData(input, output, pk.getLeggings()));
            } else if (p instanceof PlayerAuthInputPacket pk) {
                pk.setRawMoveVector(provider.createVector2f(0, 0));
                var transaction = pk.getItemUseTransaction();
                if (transaction != null) {
                    transaction.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
                    transaction.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
                }
            } else if (p instanceof ItemStackRequestPacket pk) {
                val newRequests = new ArrayList<ItemStackRequest>(pk.getRequests().size());
                for (val req : pk.getRequests()) {
                    val newActions = new ArrayList<ItemStackRequestAction>(pk.getRequests().size());
                    val actions = req.getActions();
                    for (val action : actions) {
                        if (action instanceof TakeAction a) {
                            newActions.add(new TakeAction(a.getCount(), translateItemStackRequestSlotData(a.getSource()), translateItemStackRequestSlotData(a.getDestination())));
                        } else if (action instanceof ConsumeAction a) {
                            newActions.add(new ConsumeAction(a.getCount(), translateItemStackRequestSlotData(a.getSource())));
                        } else if (action instanceof DestroyAction a) {
                            newActions.add(new DestroyAction(a.getCount(), translateItemStackRequestSlotData(a.getSource())));
                        } else if (action instanceof DropAction a) {
                            newActions.add(new DropAction(a.getCount(), translateItemStackRequestSlotData(a.getSource()), a.isRandomly()));
                        } else if (action instanceof PlaceAction a) {
                            val newAct = new PlaceAction(a.getCount(), translateItemStackRequestSlotData(a.getSource()), translateItemStackRequestSlotData(a.getDestination()));
                            // if (newAct.getSource().getContainerName().getContainer().equals(ContainerSlotType.CREATED_OUTPUT)) {
                            //     newActions.add(new CraftCreativeAction(newAct.getSource().getContainerName().getContainer().ordinal(), 1));
                            // }
                            newActions.add(newAct);
                        } else if (action instanceof SwapAction a) {
                            newActions.add(new SwapAction(translateItemStackRequestSlotData(a.getSource()), translateItemStackRequestSlotData(a.getDestination())));
                        } else {
                            newActions.add(action);
                        }
                    }
                    newRequests.add(new ItemStackRequest(req.getRequestId(), newActions.toArray(new ItemStackRequestAction[0]), req.getFilterStrings()));
                }
                pk.getRequests().clear();
                pk.getRequests().addAll(newRequests);
            }
        }
        removeNewEntityData(p, output, Bedrock_v685.CODEC, EntityDataTypes.VISIBLE_MOB_EFFECTS);
        removeNewEntityData(p, output, Bedrock_v594.CODEC,
                EntityDataTypes.COLLISION_BOX, EntityDataTypes.PLAYER_HAS_DIED, EntityDataTypes.PLAYER_LAST_DEATH_DIMENSION, EntityDataTypes.PLAYER_LAST_DEATH_POS
        );
        removeNewEntityData(p, output, Bedrock_v503.CODEC,
                EntityDataTypes.HEARTBEAT_SOUND_EVENT, EntityDataTypes.HEARTBEAT_INTERVAL_TICKS, EntityDataTypes.MOVEMENT_SOUND_DISTANCE_OFFSET
        );

        if (input < Bedrock_v685.CODEC.getProtocolVersion()) {
            if (p instanceof ContainerClosePacket pk) {
                //TODO context based value: container type
                pk.setType(ContainerType.NONE);
            }
        }
        if (input < Bedrock_v671.CODEC.getProtocolVersion()) {
            if (p instanceof ResourcePackStackPacket pk) {
                pk.setHasEditorPacks(false);
            }
        }
        if (input < Bedrock_v649.CODEC.getProtocolVersion()) {
            if (p instanceof LevelChunkPacket pk) {
                //FIXME overworld?
                pk.setDimension(0);
            } else if (p instanceof PlayerListPacket pk) {
                for (var e : pk.getEntries()) {
                    //FIXME context based value: subclient
                    e.setSubClient(false);
                }
            }
        }
        if (input < Bedrock_v589.CODEC.getProtocolVersion()) {
            if (p instanceof EmotePacket pk) {
                //FIXME? context based value: xuid platformId
                pk.setXuid("");
                pk.setPlatformId("");
                pk.setEmoteDuration(20);
            }
        }
        if (input < Bedrock_v575.CODEC.getProtocolVersion()) {
            if (p instanceof PlayerAuthInputPacket pk) {
                //FIXME? context based value: xuid platformId
                pk.setAnalogMoveVector(provider.createVector2f(0, 0));
            }
        }

        if (output < Bedrock_v594.CODEC.getProtocolVersion()) {
            if (p instanceof SetEntityDataPacket pk) {
                pk.getMetadata().remove(EntityDataTypes.COLLISION_BOX);
            }
        }
        if (output < Bedrock_v527.CODEC.getProtocolVersion()) {
            if (p instanceof SetEntityDataPacket pk) {
                pk.getMetadata().remove(EntityDataTypes.PLAYER_LAST_DEATH_POS);
                pk.getMetadata().remove(EntityDataTypes.PLAYER_LAST_DEATH_DIMENSION);
                pk.getMetadata().remove(EntityDataTypes.PLAYER_HAS_DIED);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static ItemStackRequestSlotData translateItemStackRequestSlotData(ItemStackRequestSlotData dest) {
        return new ItemStackRequestSlotData(
                dest.getContainer(),
                dest.getSlot(),
                dest.getStackNetworkId(),
                Optional.ofNullable(dest.getContainerName())
                        .orElse(new FullContainerName(dest.getContainer(), 0))
        );
    }

    private static void removeNewEntityData(BedrockPacket p, int output, BedrockCodec codec, EntityDataType<?>... types) {
        if (output < codec.getProtocolVersion()) {
            if (p instanceof SetEntityDataPacket pk) {
                for (val typ : types) {
                    pk.getMetadata().remove(typ);
                }
            } else if (p instanceof AddEntityPacket pk) {
                for (val typ : types) {
                    pk.getMetadata().remove(typ);
                }
            } else if (p instanceof AddPlayerPacket pk) {
                for (val typ : types) {
                    pk.getMetadata().remove(typ);
                }
            } else if (p instanceof AddItemEntityPacket pk) {
                for (val typ : types) {
                    pk.getMetadata().remove(typ);
                }
            }
        }
    }

    private static void rewriteBlock(int input, int output, OuranosPlayer player, BedrockPacket p) {
        if (p instanceof UpdateBlockPacket packet) {
            var runtimeId = packet.getDefinition().getRuntimeId();
            var translated = TypeConverter.translateBlockRuntimeId(input, output, runtimeId);
            packet.setDefinition(() -> translated);
        } else if (p instanceof LevelEventPacket packet) {
            var type = packet.getType();
            if (type != ParticleType.TERRAIN && type != LevelEvent.PARTICLE_DESTROY_BLOCK && type != LevelEvent.PARTICLE_CRACK_BLOCK) {
                return;
            }
            var data = packet.getData();
            var high = data & 0xFFFF0000;
            var blockID = TypeConverter.translateBlockRuntimeId(input, output, data & 0xFFFF) & 0xFFFF;
            packet.setData(high | blockID);
        } else if (p instanceof LevelSoundEventPacket pk) {
            var sound = pk.getSound();
            if (sound == SoundEvent.PLACE || sound == SoundEvent.HIT || sound == SoundEvent.ITEM_USE_ON || sound == SoundEvent.LAND || sound == SoundEvent.BREAK) {
                var runtimeId = pk.getExtraData();
                pk.setExtraData(TypeConverter.translateBlockRuntimeId(input, output, runtimeId));
            }
        } else if (p instanceof AddEntityPacket pk) {
            if (pk.getIdentifier().equals("minecraft:falling_block")) {
                var metaData = pk.getMetadata();
                int runtimeId = metaData.get(EntityDataTypes.VARIANT);
                metaData.put(EntityDataTypes.VARIANT, TypeConverter.translateBlockRuntimeId(input, output, runtimeId));
                pk.setMetadata(metaData);
            }
        } else if (p instanceof UpdateSubChunkBlocksPacket pk) {
            var newExtraBlocks = new ArrayList<BlockChangeEntry>(pk.getExtraBlocks().size());
            for (var entry : pk.getExtraBlocks()) {
                newExtraBlocks.add(new BlockChangeEntry(entry.getPosition(), TypeConverter.translateBlockDefinition(input, output, entry.getDefinition()), entry.getUpdateFlags(), entry.getMessageEntityId(), entry.getMessageType()));
            }
            pk.getExtraBlocks().clear();
            pk.getExtraBlocks().addAll(newExtraBlocks);
            var newStandardBlock = new ArrayList<BlockChangeEntry>(pk.getStandardBlocks().size());
            for (var entry : pk.getStandardBlocks()) {
                newStandardBlock.add(new BlockChangeEntry(entry.getPosition(), TypeConverter.translateBlockDefinition(input, output, entry.getDefinition()), entry.getUpdateFlags(), entry.getMessageEntityId(), entry.getMessageType()));
            }
            pk.getStandardBlocks().clear();
            pk.getStandardBlocks().addAll(newStandardBlock);
        }
    }

    private static void rewriteChunk(int input, int output, OuranosPlayer player, BedrockPacket p) {
        if (p instanceof LevelChunkPacket packet) {
            try {
                var from = packet.getData();
                var to = AbstractByteBufAllocator.DEFAULT.ioBuffer(from.readableBytes());
                TypeConverter.rewriteChunkData(input, output, from, to, packet.getSubChunksLength());
                packet.setData(to);
                ReferenceCountUtil.release(from);
            } catch (ChunkRewriteException exception) {
                log.error(exception);
                player.disconnect("Failed to rewrite chunk: " + exception.getMessage());
            }
            return;
        }
        if (p instanceof SubChunkPacket packet) {
            for (var subChunk : packet.getSubChunks()) {
                subChunk.getData().resetReaderIndex();
                if (subChunk.getData().readableBytes() > 0) {
                    try {
                        ByteBuf from = subChunk.getData();
                        var to = AbstractByteBufAllocator.DEFAULT.ioBuffer(from.readableBytes());
                        TypeConverter.rewriteChunkData(input, output, from, to, 1);
                        subChunk.setData(to);
                        ReferenceCountUtil.release(from);
                    } catch (ChunkRewriteException exception) {
                        log.error(exception);
                        player.disconnect("Failed to rewrite chunk: " + exception.getMessage());
                    }
                }
            }
        }
    }
}
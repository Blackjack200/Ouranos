package com.github.blackjack200.ouranos.network.session;

import com.github.blackjack200.ouranos.network.ProtocolInfo;
import com.github.blackjack200.ouranos.network.convert.ChunkRewriteException;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.github.blackjack200.ouranos.network.convert.TypeConverter;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.math.immutable.vector.ImmutableVectorProvider;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v544.Bedrock_v544;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
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
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
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

import java.util.*;
import java.util.function.BiFunction;

@Log4j2
public class Translate {

    public static Collection<BedrockPacket> translate(int input, int output, OuranosProxySession player, BedrockPacket p) {
        if (p instanceof ResourcePackStackPacket pk) {
            pk.setGameVersion("*");
        } else if (p instanceof ClientCacheStatusPacket pk) {
            //TODO forcibly disable client blob caches for security
            pk.setSupported(false);
        }

        var list = new HashSet<BedrockPacket>();
        list.add(p);

        rewriteItem(input, output, p, list);
        rewriteProtocol(input, output, player, p, list);
        rewriteChunk(input, output, player, p, list);
        if (p instanceof LevelChunkPacket) {
//            list.clear();
        }
        rewriteBlock(input, output, player, p, list);

        return list;
    }

    private static void rewriteItem(int input, int output, BedrockPacket p, Collection<BedrockPacket> list) {
        if (p instanceof InventoryContentPacket pk) {
            val contents = new ArrayList<>(pk.getContents());
            contents.replaceAll(itemData -> Objects.requireNonNullElse(TypeConverter.translateItemData(input, output, itemData), ItemData.AIR));
            pk.setContents(contents);
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
            val contents = new ArrayList<CreativeItemData>();
            for (int i = 0, iMax = pk.getContents().size(); i < iMax; i++) {
                var old = pk.getContents().get(i);
                var item = TypeConverter.translateCreativeItemData(input, output, old);
                contents.add(Objects.requireNonNullElseGet(item, () -> new CreativeItemData(
                        ItemData.builder().netId(old.getNetId()).count(1).damage(0).definition(new SimpleItemDefinition("minecraft:barrier", ItemTypeDictionary.getInstance(output).fromStringId("minecraft:barrier"), false)).build(),
                        old.getNetId(), old.getGroupId())));
            }
            pk.getContents().clear();
            pk.getContents().addAll(contents);
        } else if (p instanceof AddItemEntityPacket pk) {
            pk.setItemInHand(TypeConverter.translateItemData(input, output, pk.getItemInHand()));
        } else if (p instanceof InventorySlotPacket pk) {
            pk.setItem(TypeConverter.translateItemData(input, output, pk.getItem()));
            pk.setStorageItem(TypeConverter.translateItemData(input, output, pk.getStorageItem()));
        } else if (p instanceof InventoryTransactionPacket pk) {
            var newActions = new ArrayList<InventoryActionData>(pk.getActions().size());
            for (var action : pk.getActions()) {
                newActions.add(new InventoryActionData(action.getSource(), action.getSlot(), TypeConverter.translateItemData(input, output, action.getFromItem()), TypeConverter.translateItemData(input, output, action.getToItem()), action.getStackNetworkId()));
            }
            pk.getActions().clear();
            pk.getActions().addAll(newActions);

            if (pk.getBlockDefinition() != null) {
                pk.setBlockDefinition(TypeConverter.translateBlockDefinition(input, output, pk.getBlockDefinition()));
            }
            if (pk.getItemInHand() != null) {
                pk.setItemInHand(TypeConverter.translateItemData(input, output, pk.getItemInHand()));
            }
        } else if (p instanceof MobEquipmentPacket pk) {
            pk.setItem(TypeConverter.translateItemData(input, output, pk.getItem()));
        } else if (p instanceof MobArmorEquipmentPacket pk) {
            if (pk.getBody() != null) {
                pk.setBody(TypeConverter.translateItemData(input, output, pk.getBody()));
            }
            pk.setChestplate(TypeConverter.translateItemData(input, output, pk.getChestplate()));
            pk.setHelmet(TypeConverter.translateItemData(input, output, pk.getHelmet()));
            pk.setBoots(TypeConverter.translateItemData(input, output, pk.getBoots()));
            pk.setLeggings(TypeConverter.translateItemData(input, output, pk.getLeggings()));
        } else if (p instanceof AddPlayerPacket pk) {
            pk.setHand(TypeConverter.translateItemData(input, output, pk.getHand()));
        }
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

    private static void rewriteProtocol(int input, int output, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        val provider = new ImmutableVectorProvider();
        if (p instanceof StartGamePacket pk) {
            pk.setServerId(Optional.ofNullable(pk.getServerId()).orElse(""));
            pk.setWorldId(Optional.ofNullable(pk.getWorldId()).orElse(""));
            pk.setScenarioId(Optional.ofNullable(pk.getScenarioId()).orElse(""));
            pk.setChatRestrictionLevel(Optional.ofNullable(pk.getChatRestrictionLevel()).orElse(ChatRestrictionLevel.NONE));
            pk.setPlayerPropertyData(Optional.ofNullable(pk.getPlayerPropertyData()).orElse(NbtMap.EMPTY));
            pk.setWorldTemplateId(Optional.ofNullable(pk.getWorldTemplateId()).orElse(UUID.randomUUID()));
        }
        if (p instanceof AddPlayerPacket pk) {
            pk.setGameType(Optional.ofNullable(pk.getGameType()).orElse(GameType.DEFAULT));
        }
        if (input < Bedrock_v766.CODEC.getProtocolVersion()) {
            if (p instanceof ResourcePacksInfoPacket pk) {
                pk.setWorldTemplateId(UUID.randomUUID());
                pk.setWorldTemplateVersion("0.0.0");
            }
            if (p instanceof PlayerAuthInputPacket pk) {
                pk.setRawMoveVector(provider.createVector2f(0, 0));
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
        if (output < Bedrock_v554.CODEC.getProtocolVersion()) {
            if (p instanceof UpdateAbilitiesPacket pk) {
                var newPk = new AdventureSettingsPacket();
                newPk.setUniqueEntityId(pk.getUniqueEntityId());
                newPk.setCommandPermission(pk.getCommandPermission());
                newPk.setPlayerPermission(pk.getPlayerPermission());
                newPk.getSettings().clear();
                var abilities = pk.getAbilityLayers().get(0).getAbilityValues();
                BiFunction<Ability, AdventureSetting, Void> f = (Ability b1, AdventureSetting b) -> {
                    if (abilities.contains(b1)) {
                        newPk.getSettings().add(b);
                    }
                    return null;
                };
                f.apply(Ability.MINE, AdventureSetting.MINE);
                f.apply(Ability.DOORS_AND_SWITCHES, AdventureSetting.DOORS_AND_SWITCHES);
                f.apply(Ability.OPEN_CONTAINERS, AdventureSetting.OPEN_CONTAINERS);
                f.apply(Ability.ATTACK_PLAYERS, AdventureSetting.ATTACK_PLAYERS);
                f.apply(Ability.ATTACK_MOBS, AdventureSetting.ATTACK_MOBS);
                f.apply(Ability.OPERATOR_COMMANDS, AdventureSetting.OPERATOR);
                f.apply(Ability.TELEPORT, AdventureSetting.TELEPORT);
                f.apply(Ability.BUILD, AdventureSetting.BUILD);
                if (!abilities.contains(Ability.MINE) && !abilities.contains(Ability.BUILD)) {
                    newPk.getSettings().add(AdventureSetting.WORLD_IMMUTABLE);
                    newPk.getSettings().remove(AdventureSetting.BUILD);
                    newPk.getSettings().remove(AdventureSetting.MINE);
                }
                list.add(newPk);
            }
        }
        if (output > Bedrock_v554.CODEC.getProtocolVersion()) {
            if (p instanceof AdventureSettingsPacket pk) {
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
                BiFunction<AdventureSetting, Ability, Void> f = (AdventureSetting b, Ability b1) -> {
                    if (settings.contains(b)) {
                        layer.getAbilityValues().add(b1);
                    }
                    return null;
                };
                f.apply(AdventureSetting.BUILD, Ability.BUILD);
                f.apply(AdventureSetting.MINE, Ability.MINE);
                f.apply(AdventureSetting.DOORS_AND_SWITCHES, Ability.DOORS_AND_SWITCHES);
                f.apply(AdventureSetting.OPEN_CONTAINERS, Ability.OPEN_CONTAINERS);
                f.apply(AdventureSetting.ATTACK_PLAYERS, Ability.ATTACK_PLAYERS);
                f.apply(AdventureSetting.ATTACK_MOBS, Ability.ATTACK_MOBS);
                f.apply(AdventureSetting.OPERATOR, Ability.OPERATOR_COMMANDS);
                f.apply(AdventureSetting.TELEPORT, Ability.TELEPORT);
                f.apply(AdventureSetting.FLYING, Ability.FLYING);
                f.apply(AdventureSetting.MAY_FLY, Ability.MAY_FLY);
                f.apply(AdventureSetting.MUTED, Ability.MUTED);
                f.apply(AdventureSetting.WORLD_BUILDER, Ability.WORLD_BUILDER);
                list.add(newPk);

                var newPk2 = new UpdateAdventureSettingsPacket();
                newPk2.setAutoJump(settings.contains(AdventureSetting.AUTO_JUMP));
                newPk2.setImmutableWorld(settings.contains(AdventureSetting.WORLD_IMMUTABLE));
                newPk2.setNoMvP(settings.contains(AdventureSetting.NO_MVP));
                newPk2.setNoPvM(settings.contains(AdventureSetting.NO_PVM));
                newPk2.setShowNameTags(settings.contains(AdventureSetting.SHOW_NAME_TAGS));
                list.add(newPk2);
            }
        }
        removeNewEntityData(p, output, Bedrock_v685.CODEC, EntityDataTypes.VISIBLE_MOB_EFFECTS);
        removeNewEntityData(p, output, Bedrock_v594.CODEC,
                EntityDataTypes.COLLISION_BOX, EntityDataTypes.PLAYER_HAS_DIED, EntityDataTypes.PLAYER_LAST_DEATH_DIMENSION, EntityDataTypes.PLAYER_LAST_DEATH_POS
        );
        removeNewEntityData(p, output, Bedrock_v527.CODEC,
                EntityDataTypes.PLAYER_LAST_DEATH_POS, EntityDataTypes.PLAYER_LAST_DEATH_DIMENSION, EntityDataTypes.PLAYER_HAS_DIED
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
        if (input < Bedrock_v594.CODEC.getProtocolVersion()) {
            if (p instanceof LevelSoundEventPacket pk) {
                if (pk.getSound().equals(SoundEvent.ATTACK_NODAMAGE)) {
                    player.lastPunchAir = true;
                    list.clear();
                }
            }
            if (p instanceof PlayerAuthInputPacket pk) {
                if (player.lastPunchAir) {
                    pk.getInputData().add(PlayerAuthInputData.MISSED_SWING);
                    player.lastPunchAir = false;
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
        if (input < Bedrock_v544.CODEC.getProtocolVersion()) {
            if (p instanceof ModalFormResponsePacket pk) {
                if (player.lastFormId == pk.getFormId()) {
                    list.clear();
                }
                pk.setCancelReason(Optional.empty());
                player.lastFormId = pk.getFormId();
            }
        }
        if (input < Bedrock_v527.CODEC.getProtocolVersion()) {
            if (p instanceof PlayerAuthInputPacket pk) {
                pk.setInputInteractionModel(Optional.ofNullable(pk.getInputInteractionModel()).orElse(InputInteractionModel.CLASSIC));
            } else if (p instanceof PlayerActionPacket pk) {
                pk.setResultPosition(pk.getBlockPosition());
            }
        }
        if (p instanceof PlayerSkinPacket pk) {
            assert ProtocolInfo.getPacketCodec(output) != null;
            pk.setSkin(pk.getSkin().toBuilder().geometryDataEngineVersion(ProtocolInfo.getPacketCodec(output).getMinecraftVersion()).build());
        }
        if (p instanceof PlayerListPacket pk) {
            for (var e : pk.getEntries()) {
                if (e.getSkin() != null) {
                    e.setSkin(e.getSkin().toBuilder().geometryDataEngineVersion(ProtocolInfo.getPacketCodec(output).getMinecraftVersion()).build());
                }
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

    private static void rewriteBlock(int input, int output, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        if (p instanceof UpdateBlockPacket packet) {
            var runtimeId = packet.getDefinition().getRuntimeId();
            var translated = TypeConverter.translateBlockRuntimeId(input, output, runtimeId);
            packet.setDefinition(new SimpleBlockDefinition(translated));
        } else if (p instanceof LevelEventPacket packet) {
            var type = packet.getType();
            if (type != ParticleType.TERRAIN && type != LevelEvent.PARTICLE_DESTROY_BLOCK && type != LevelEvent.PARTICLE_CRACK_BLOCK) {
                return;
            }
            var data = packet.getData();
            if (data != -1) {
                data = TypeConverter.translateBlockRuntimeId(input, output, data);
            }
            packet.setData(data);
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

    private static void rewriteChunk(int input, int output, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        if (p instanceof LevelChunkPacket packet) {
            try {
                var from = packet.getData();
                var to = AbstractByteBufAllocator.DEFAULT.ioBuffer(from.readableBytes());
                TypeConverter.rewriteFullChunk(input, output, from, to, packet.getDimension(), packet.getSubChunksLength());
                packet.setData(to);
                ReferenceCountUtil.release(from);
            } catch (ChunkRewriteException exception) {
                log.error("Failed to rewrite chunk: ", exception);
                player.disconnect("Failed to rewrite chunk: " + exception.getMessage());
            }
            return;
        }
        if (p instanceof SubChunkPacket packet) {
            for (var subChunk : packet.getSubChunks()) {
                ByteBuf from = subChunk.getData();
                if (subChunk.getData().readableBytes() > 0) {
                    try {
                        var to = AbstractByteBufAllocator.DEFAULT.ioBuffer(from.readableBytes());
                        TypeConverter.rewriteSubChunk(input, output, from, to);
                        to.writeBytes(from);
                        subChunk.setData(to);
                        ReferenceCountUtil.release(from);
                    } catch (ChunkRewriteException exception) {
                        log.error("Failed to rewrite chunk: ", exception);
                        player.disconnect("Failed to rewrite chunk: " + exception.getMessage());
                    }
                }
            }
        }
    }
}
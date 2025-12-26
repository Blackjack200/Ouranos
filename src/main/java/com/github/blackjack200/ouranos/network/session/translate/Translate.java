package com.github.blackjack200.ouranos.network.session.translate;

import com.github.blackjack200.ouranos.network.ProtocolInfo;
import com.github.blackjack200.ouranos.network.convert.ChunkRewriteException;
import com.github.blackjack200.ouranos.network.convert.ItemTypeDictionary;
import com.github.blackjack200.ouranos.network.convert.TypeConverter;
import com.github.blackjack200.ouranos.network.convert.biome.BiomeDefinitionRegistry;
import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import com.github.blackjack200.ouranos.utils.EntityDataCompat;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.cloudburstmc.math.immutable.vector.ImmutableVectorProvider;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v408.Bedrock_v408;
import org.cloudburstmc.protocol.bedrock.codec.v419.Bedrock_v419;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v534.Bedrock_v534;
import org.cloudburstmc.protocol.bedrock.codec.v544.Bedrock_v544;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.codec.v560.Bedrock_v560;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
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
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;

    @SuppressWarnings("deprecation")
    @Log4j2
    public class Translate {
        public static Collection<BedrockPacket> translate(int input, int output, boolean fromServer, OuranosProxySession player, BedrockPacket p) {
            if (input == output) {
                return List.of(p);
            }

            normalizeEntityData(p);
            if (p instanceof ResourcePackStackPacket pk) {
                pk.setGameVersion("*");
            } else if (p instanceof ClientCacheStatusPacket pk) {
                //TODO forcibly disable client blob caches for security
                pk.setSupported(false);
        }

        var list = new HashSet<BedrockPacket>();
        list.add(p);

        rewriteProtocol(input, output, fromServer, player, p, list);
        rewriteItem(input, output, p, list);
        rewriteBlock(input, output, player, p, list);
        rewriteChunk(input, output, player, p, list);

        MovementTranslator.rewriteMovement(input, output, fromServer, player, p, list);
        InventoryTranslator.rewriteInventory(input, output, fromServer, player, p, list);
        if (output >= Bedrock_v554.CODEC.getProtocolVersion()) {
            list.removeIf((b) -> b instanceof AdventureSettingsPacket);
        }
        if (output > Bedrock_v408.CODEC.getProtocolVersion()) {
            list.removeIf((b) -> b instanceof EntityFallPacket);
        }
            return list;
        }

        private static void normalizeEntityData(BedrockPacket p) {
            if (p instanceof SetEntityDataPacket pk) {
                EntityDataCompat.normalizeEntityDataFlags(pk.getMetadata());
            } else if (p instanceof AddEntityPacket pk) {
                EntityDataCompat.normalizeEntityDataFlags(pk.getMetadata());
            } else if (p instanceof AddPlayerPacket pk) {
                EntityDataCompat.normalizeEntityDataFlags(pk.getMetadata());
            } else if (p instanceof AddItemEntityPacket pk) {
                EntityDataCompat.normalizeEntityDataFlags(pk.getMetadata());
            }
        }

        private static void rewriteItem(int input, int output, BedrockPacket p, Collection<BedrockPacket> list) {
            if (p instanceof InventoryContentPacket pk) {
                val contents = new ArrayList<>(pk.getContents());
                contents.replaceAll(itemData -> TypeConverter.translateItemData(input, output, itemData));
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
                contents.add(item);
            }
            pk.getContents().clear();
            if (input < output && output < Bedrock_v776.CODEC.getProtocolVersion()) {
                pk.getContents().addAll(contents);
            }
            if (input >= output) {
                pk.getContents().addAll(contents);
            }
            val groups = new ArrayList<CreativeItemGroup>();
            for (var group : pk.getGroups()) {
                groups.add(group.toBuilder().icon(TypeConverter.translateItemData(input, output, group.getIcon())).build());
            }
            pk.getGroups().clear();
            pk.getGroups().addAll(groups);
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

    private static void rewriteProtocol(int input, int output, boolean fromServer, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        writeProtocolDefault(player, p);
        if (p instanceof StartGamePacket pk) {
            if (output >= Bedrock_v776.CODEC.getProtocolVersion()) {
                var newPk = new ItemComponentPacket();

                List<ItemDefinition> def = ItemTypeDictionary.getInstance(output).getEntries().entrySet().stream().<ItemDefinition>map((e) -> e.getValue().toDefinition(e.getKey())).toList();
                newPk.getItems().addAll(def);
                list.add(newPk);
            }
        }
        if (p instanceof ItemStackResponsePacket pk) {
            var translated = pk.getEntries().stream().map((entry) -> {
                if (input >= Bedrock_v419.CODEC.getProtocolVersion() && output < Bedrock_v419.CODEC.getProtocolVersion()) {
                    return new ItemStackResponse(entry.getResult().equals(ItemStackResponseStatus.OK), entry.getRequestId(), entry.getContainers());
                }
                return entry;
            }).toList();
            pk.getEntries().clear();
            pk.getEntries().addAll(translated);
        }
        val provider = new ImmutableVectorProvider();

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
            }
        }

        if (p instanceof ItemStackRequestPacket pk) {
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

        rewriteAdventureSettings(input, output, fromServer, player, p, list);

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
                e.setColor(Objects.requireNonNullElse(e.getColor(), Color.WHITE));
                if (e.getSkin() != null) {
                    e.setSkin(e.getSkin().toBuilder().geometryDataEngineVersion(ProtocolInfo.getPacketCodec(output).getMinecraftVersion()).build());
                }
            }
        }
        if (p instanceof BiomeDefinitionListPacket pk) {
            //TODO fix biome for v800
            if (output >= Bedrock_v800.CODEC.getProtocolVersion()) {
                if (pk.getBiomes() == null && pk.getDefinitions() != null) {
                    BiomeDefinitions defs = new BiomeDefinitions(new HashMap<>());
                    pk.getDefinitions().forEach((id, n) -> {
                        var def = BiomeDefinitionRegistry.getInstance(input).fromStringId(id);
                        if (def != null) {
                            defs.getDefinitions().put(id, def);
                        }
                    });
                    pk.setBiomes(defs);
                }
            } else {
                if (pk.getBiomes() != null && pk.getDefinitions() == null) {
                    pk.setDefinitions(downgradeBiomeDefinition(output, pk.getBiomes().getDefinitions()));
                }
            }
        }
    }

    private static NbtMap downgradeBiomeDefinition(int output, Map<String, BiomeDefinitionData> definitions) {
        var builder = NbtMap.builder();
        if (definitions.isEmpty()) {
            definitions = BiomeDefinitionRegistry.getInstance(output).getEntries();
        }
        definitions.forEach((id, def) -> {
            var d = NbtMap.builder();
            d.putString("name_hash", id);
            d.putFloat("temperature", def.getTemperature());
            d.putFloat("downfall", def.getDownfall());
            d.putBoolean("rain", def.isRain());
            builder.putCompound(id, d.build());
        });
        NbtMap build = builder.build();
        return build;
    }

    public static void writeProtocolDefault(OuranosProxySession session, BedrockPacket p) {
        val provider = new ImmutableVectorProvider();
        if (p instanceof StartGamePacket pk) {
            pk.setServerId(Optional.ofNullable(pk.getServerId()).orElse(""));
            pk.setWorldId(Optional.ofNullable(pk.getWorldId()).orElse(""));
            pk.setScenarioId(Optional.ofNullable(pk.getScenarioId()).orElse(""));
            pk.setChatRestrictionLevel(Optional.ofNullable(pk.getChatRestrictionLevel()).orElse(ChatRestrictionLevel.NONE));
            pk.setPlayerPropertyData(Optional.ofNullable(pk.getPlayerPropertyData()).orElse(NbtMap.EMPTY));
            pk.setWorldTemplateId(Optional.ofNullable(pk.getWorldTemplateId()).orElse(UUID.randomUUID()));
            pk.setOwnerId(Objects.requireNonNullElse(pk.getOwnerId(), ""));
            pk.setAuthoritativeMovementMode(Objects.requireNonNullElse(pk.getAuthoritativeMovementMode(), AuthoritativeMovementMode.SERVER_WITH_REWIND));
        }
        if (p instanceof PlayerAuthInputPacket pk) {
            pk.setDelta(Objects.requireNonNullElseGet(pk.getDelta(), () -> provider.createVector3f(0, 0, 0)));
            pk.setMotion(Objects.requireNonNullElseGet(pk.getMotion(), () -> provider.createVector2f(0, 0)));
            pk.setRawMoveVector(Objects.requireNonNullElseGet(pk.getRawMoveVector(), () -> provider.createVector2f(0, 0)));
            pk.setInputMode(Objects.requireNonNullElse(pk.getInputMode(), session.movement.inputMode));
            pk.setPlayMode(Objects.requireNonNullElse(pk.getPlayMode(), ClientPlayMode.NORMAL));
            pk.setInputInteractionModel(Objects.requireNonNullElse(pk.getInputInteractionModel(), InputInteractionModel.TOUCH));
            pk.setAnalogMoveVector(Objects.requireNonNullElse(pk.getAnalogMoveVector(), provider.createVector2f(0, 0)));

            pk.setInteractRotation(Objects.requireNonNullElseGet(pk.getInteractRotation(), () -> provider.createVector2f(0, 0)));
            pk.setCameraOrientation(Objects.requireNonNullElseGet(pk.getCameraOrientation(), () -> provider.createVector3f(0, 0, 0)));
        }
        if (p instanceof AddPlayerPacket pk) {
            pk.setGameType(Optional.ofNullable(pk.getGameType()).orElse(GameType.DEFAULT));
        }
        if (p instanceof ModalFormResponsePacket pk) {
            if (pk.getFormData() == null) {
                pk.setFormData("null");
            }
        }
        if (p instanceof ResourcePacksInfoPacket pk) {
            pk.setWorldTemplateId(Objects.requireNonNullElseGet(pk.getWorldTemplateId(), UUID::randomUUID));
            pk.setWorldTemplateVersion(Objects.requireNonNullElse(pk.getWorldTemplateVersion(), "0.0.0"));
        }
    }

    private static void rewritePlayerInput(int input, int output, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {

    }

    private static void rewriteAdventureSettings(int input, int output, boolean fromServer, OuranosProxySession player, BedrockPacket p, Collection<BedrockPacket> list) {
        if (!fromServer) {
            if (p instanceof RequestAbilityPacket pk) {
                rewriteFlying(output, player, list, pk.getAbility() == Ability.FLYING);
            }
            if (p instanceof AdventureSettingsPacket pk) {
                rewriteFlying(output, player, list, pk.getSettings().contains(AdventureSetting.FLYING));
            }
            if (output < Bedrock_v618.CODEC.getProtocolVersion()) {
                if (p instanceof PlayerActionPacket packet) {
                    if (packet.getAction() == PlayerActionType.START_FLYING) {
                        rewriteFlying(output, player, list, true);
                    }
                    if (packet.getAction() == PlayerActionType.STOP_FLYING) {
                        rewriteFlying(output, player, list, false);
                    }
                }
            }
        }

        //downgrade
        if (output < Bedrock_v534.CODEC.getProtocolVersion()) {
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
                f.apply(Ability.BUILD, AdventureSetting.BUILD);
                f.apply(Ability.MINE, AdventureSetting.MINE);
                f.apply(Ability.DOORS_AND_SWITCHES, AdventureSetting.DOORS_AND_SWITCHES);
                f.apply(Ability.OPEN_CONTAINERS, AdventureSetting.OPEN_CONTAINERS);
                f.apply(Ability.ATTACK_PLAYERS, AdventureSetting.ATTACK_PLAYERS);
                f.apply(Ability.ATTACK_MOBS, AdventureSetting.ATTACK_MOBS);
                f.apply(Ability.OPERATOR_COMMANDS, AdventureSetting.OPERATOR);
                f.apply(Ability.TELEPORT, AdventureSetting.TELEPORT);
                f.apply(Ability.FLYING, AdventureSetting.FLYING);
                f.apply(Ability.MAY_FLY, AdventureSetting.MAY_FLY);
                f.apply(Ability.MUTED, AdventureSetting.MUTED);
                f.apply(Ability.WORLD_BUILDER, AdventureSetting.WORLD_BUILDER);
                f.apply(Ability.NO_CLIP, AdventureSetting.NO_CLIP);
                if (!abilities.contains(Ability.MINE) && !abilities.contains(Ability.BUILD)) {
                    newPk.getSettings().add(AdventureSetting.WORLD_IMMUTABLE);
                    newPk.getSettings().remove(AdventureSetting.BUILD);
                    newPk.getSettings().remove(AdventureSetting.MINE);
                }
                list.add(newPk);
            }
        }

        //upgrade
        if (output > Bedrock_v554.CODEC.getProtocolVersion()) {
            if (fromServer && p instanceof AdventureSettingsPacket pk) {
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
                f.apply(AdventureSetting.NO_CLIP, Ability.NO_CLIP);
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
    }

    private static void rewriteFlying(int output, OuranosProxySession player, Collection<BedrockPacket> list, boolean flying) {
        if (output < Bedrock_v527.CODEC.getProtocolVersion()) {
            var newPk = new AdventureSettingsPacket();
            newPk.setUniqueEntityId(player.uniqueEntityId);
            if (flying) {
                newPk.getSettings().add(AdventureSetting.FLYING);
            }
            list.add(newPk);
        } else {
            var newPk = new RequestAbilityPacket();
            newPk.setAbility(Ability.FLYING);
            newPk.setType(Ability.Type.BOOLEAN);
            newPk.setBoolValue(flying);
            list.add(newPk);
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
            var data = packet.getData();

            if (type == ParticleType.ICON_CRACK) {
                var newItem = TypeConverter.translateItemRuntimeId(input, output, data >> 16, data & 0xFFFF);
                data = newItem[0] << 16 | newItem[1];
            } else if (type == LevelEvent.PARTICLE_DESTROY_BLOCK) {
                data = TypeConverter.translateBlockRuntimeId(input, output, data);
            } else if (type == LevelEvent.PARTICLE_CRACK_BLOCK) {
                var face = data >> 24;
                var runtimeId = data & ~(face << 24);
                data = TypeConverter.translateBlockRuntimeId(input, output, runtimeId) | face << 24;
            }
            packet.setData(data);
        } else if (p instanceof LevelSoundEventPacket pk) {
            var sound = pk.getSound();
            var runtimeId = pk.getExtraData();
            switch (sound) {
                case DOOR_OPEN:
                case DOOR_CLOSE:
                case TRAPDOOR_OPEN:
                case TRAPDOOR_CLOSE:
                case FENCE_GATE_OPEN:
                case FENCE_GATE_CLOSE:
                    pk.setExtraData(TypeConverter.translateBlockRuntimeId(input, output, runtimeId));
                    if (output < Bedrock_v560.CODEC.getProtocolVersion()) {
                        list.clear();
                        var newPk = new LevelEventPacket();
                        newPk.setType(LevelEvent.SOUND_DOOR_OPEN);
                        newPk.setPosition(pk.getPosition());
                        list.add(newPk);
                    }
                    break;
                case PLACE:
                case BREAK_BLOCK:
                case ITEM_USE_ON:
                    pk.setExtraData(TypeConverter.translateBlockRuntimeId(input, output, runtimeId));
            }
        } else if (p instanceof EntityEventPacket pk) {
            var type = pk.getType();
            if (type == EntityEventType.EATING_ITEM) {
                var data = pk.getData();
                var newItem = TypeConverter.translateItemRuntimeId(input, output, data >> 16, data & 0xFFFF);
                pk.setData((newItem[0] << 16) | newItem[1]);
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
            var from = packet.getData();
            var to = AbstractByteBufAllocator.DEFAULT.buffer(from.readableBytes()).touch();
            try {
                var newSubChunkCount = TypeConverter.rewriteFullChunk(input, output, from, to, packet.getDimension(), packet.getSubChunksLength());
                packet.setSubChunksLength(newSubChunkCount);
                packet.setData(to.retain());
            } catch (ChunkRewriteException exception) {
                log.error("Failed to rewrite chunk: ", exception);
                player.disconnect("Failed to rewrite chunk: " + exception.getMessage());
            } finally {
                ReferenceCountUtil.release(from);
                ReferenceCountUtil.release(to);
            }
            return;
        }
        if (p instanceof SubChunkPacket packet) {
            for (var subChunk : packet.getSubChunks()) {
                if (subChunk.getData().readableBytes() > 0) {
                    var from = subChunk.getData();
                    var to = AbstractByteBufAllocator.DEFAULT.buffer(from.readableBytes());
                    try {
                        TypeConverter.rewriteSubChunk(input, output, from, to);
                        TypeConverter.rewriteBlockEntities(input, output, from, to);
                        to.writeBytes(from);
                        subChunk.setData(to.retain());
                    } catch (ChunkRewriteException exception) {
                        log.error("Failed to rewrite chunk: ", exception);
                        player.disconnect("Failed to rewrite chunk: " + exception.getMessage());
                    } finally {
                        ReferenceCountUtil.release(from);
                        ReferenceCountUtil.release(to);
                    }
                }
            }
            if (output < Bedrock_v475.CODEC.getProtocolVersion()) {
                packet.getSubChunks().subList(0, 4).clear();
            }
        }
    }
}

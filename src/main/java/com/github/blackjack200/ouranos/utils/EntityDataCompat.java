package com.github.blackjack200.ouranos.utils;

import com.github.blackjack200.ouranos.network.ProtocolInfo;
import org.cloudburstmc.protocol.bedrock.codec.BaseBedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.EntityDataTypeMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.transformer.FlagTransformer;
import org.cloudburstmc.protocol.common.util.TypeMap;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityDataCompat {
    private static final EntityMetadataSchema EMPTY_SCHEMA = new EntityMetadataSchema(null, null, null);
    private static final Map<Integer, EntityMetadataSchema> SCHEMA_BY_PROTOCOL = new ConcurrentHashMap<>();
    private static final Field ENTITY_DATA_FIELD = getField(BaseBedrockCodecHelper.class, "entityData");
    private static final Field FLAG_TYPE_MAP_FIELD = getField(FlagTransformer.class, "typeMap");

    private EntityDataCompat() {
    }

    /**
     * Cloudburst 3.0.0.Beta11 changed {@code EntityDataTypes.FLAGS}/{@code FLAGS_2} from {@code EnumSet<EntityFlag>}
     * (Beta10 behavior) to {@code EnumMap<EntityFlag, Boolean>}. This method accepts either representation and
     * normalizes it to the Beta11 representation while keeping Beta10 semantics (absence == false).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void normalizeEntityDataFlags(EntityDataMap metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        normalizeFlagsForType(metadata, (EntityDataType) EntityDataTypes.FLAGS);
        normalizeFlagsForType(metadata, (EntityDataType) EntityDataTypes.FLAGS_2);
    }

    public static void filterEntityData(int protocolVersion, EntityDataMap metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        EntityMetadataSchema schema = SCHEMA_BY_PROTOCOL.computeIfAbsent(protocolVersion, EntityDataCompat::loadSchema);
        if (schema == null || schema == EMPTY_SCHEMA || schema.dataTypeMap == null) {
            return;
        }

        metadata.keySet().removeIf(type -> schema.dataTypeMap.fromType((EntityDataType<?>) type) == null);
        filterFlags(metadata, EntityDataTypes.FLAGS, schema.flags);
        filterFlags(metadata, EntityDataTypes.FLAGS_2, schema.flags2);
    }

    /**
     * Backward-compatible setter for {@code EntityDataTypes.FLAGS}/{@code FLAGS_2}.
     * <p>
     * In Beta10, callers typically did {@code metadata.put(EntityDataTypes.FLAGS, EnumSet<EntityFlag>)}.
     * In Beta11, this throws. This method accepts the Beta10 style value and stores it in the Beta11 format.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void put(EntityDataMap metadata, EntityDataType<?> type, Object value) {
        if (metadata == null) {
            return;
        }
        if ((type == EntityDataTypes.FLAGS || type == EntityDataTypes.FLAGS_2) && value instanceof EnumSet<?> enumSet) {
            EnumMap<EntityFlag, Boolean> converted = new EnumMap<>(EntityFlag.class);
            for (Object element : enumSet) {
                if (element instanceof EntityFlag flag) {
                    converted.put(flag, Boolean.TRUE);
                }
            }
            metadata.put((EntityDataType) type, converted);
            return;
        }

        if ((type == EntityDataTypes.FLAGS || type == EntityDataTypes.FLAGS_2) && value instanceof EnumMap<?, ?> enumMap) {
            EnumMap<EntityFlag, Boolean> converted = new EnumMap<>(EntityFlag.class);
            for (Map.Entry<?, ?> entry : enumMap.entrySet()) {
                if (!(entry.getKey() instanceof EntityFlag flag)) {
                    continue;
                }
                if (Boolean.TRUE.equals(entry.getValue())) {
                    converted.put(flag, Boolean.TRUE);
                }
            }
            metadata.put((EntityDataType) type, converted);
            return;
        }

        metadata.put((EntityDataType) type, value);
    }

    private static void normalizeFlagsForType(EntityDataMap metadata, EntityDataType<?> flagsType) {
        Object raw = metadata.get(flagsType);
        if (raw == null) {
            return;
        }

        if (raw instanceof EnumSet<?> enumSet) {
            EnumMap<EntityFlag, Boolean> converted = new EnumMap<>(EntityFlag.class);
            for (Object value : enumSet) {
                if (value instanceof EntityFlag flag) {
                    converted.put(flag, Boolean.TRUE);
                }
            }
            metadata.put(flagsType, converted);
            return;
        }

        if (raw instanceof EnumMap<?, ?> enumMap) {
            EnumMap<EntityFlag, Boolean> converted = new EnumMap<>(EntityFlag.class);
            for (Map.Entry<?, ?> entry : enumMap.entrySet()) {
                if (!(entry.getKey() instanceof EntityFlag flag)) {
                    continue;
                }
                if (Boolean.TRUE.equals(entry.getValue())) {
                    converted.put(flag, Boolean.TRUE);
                }
            }
            metadata.put(flagsType, converted);
            return;
        }

        if (raw instanceof Map<?, ?> map) {
            EnumMap<EntityFlag, Boolean> converted = new EnumMap<>(EntityFlag.class);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof EntityFlag flag)) {
                    continue;
                }
                if (Boolean.TRUE.equals(entry.getValue())) {
                    converted.put(flag, Boolean.TRUE);
                }
            }
            metadata.put(flagsType, converted);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void filterFlags(EntityDataMap metadata, EntityDataType<?> flagsType, TypeMap<EntityFlag> flags) {
        if (flags == null) {
            return;
        }
        Object raw = metadata.get(flagsType);
        if (!(raw instanceof EnumMap<?, ?> enumMap)) {
            return;
        }
        enumMap.keySet().removeIf(flag -> !(flag instanceof EntityFlag) || flags.getIdUnsafe((EntityFlag) flag) == -1);
        metadata.put((EntityDataType) flagsType, enumMap);
    }

    private static EntityMetadataSchema loadSchema(int protocolVersion) {
        if (ENTITY_DATA_FIELD == null || FLAG_TYPE_MAP_FIELD == null) {
            return EMPTY_SCHEMA;
        }
        var codec = ProtocolInfo.getPacketCodec(protocolVersion);
        if (codec == null) {
            return EMPTY_SCHEMA;
        }
        try {
            var helper = (BaseBedrockCodecHelper) codec.createHelper();
            var dataTypeMap = (EntityDataTypeMap) ENTITY_DATA_FIELD.get(helper);
            var flags = extractFlags(dataTypeMap, EntityDataTypes.FLAGS);
            var flags2 = extractFlags(dataTypeMap, EntityDataTypes.FLAGS_2);
            return new EntityMetadataSchema(dataTypeMap, flags, flags2);
        } catch (IllegalAccessException ignored) {
            return EMPTY_SCHEMA;
        }
    }

    @SuppressWarnings("unchecked")
    private static TypeMap<EntityFlag> extractFlags(EntityDataTypeMap dataTypeMap, EntityDataType<?> type) {
        if (dataTypeMap == null) {
            return null;
        }
        var def = dataTypeMap.fromType((EntityDataType) type);
        if (def == null) {
            return null;
        }
        var transformer = def.getTransformer();
        if (!(transformer instanceof FlagTransformer)) {
            return null;
        }
        try {
            return (TypeMap<EntityFlag>) FLAG_TYPE_MAP_FIELD.get(transformer);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field getField(Class<?> clazz, String fieldName) {
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private record EntityMetadataSchema(EntityDataTypeMap dataTypeMap,
                                        TypeMap<EntityFlag> flags,
                                        TypeMap<EntityFlag> flags2) {
    }
}

package com.github.blackjack200.ouranos.utils;

import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class EntityDataCompat {
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
}

package com.github.blackjack200.ouranos.network.session.translate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public final class TranslationPipeline {
    private final List<StageBinding> bindings;

    private TranslationPipeline(List<StageBinding> bindings) {
        this.bindings = bindings;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Collection<BedrockPacket> run(
        TranslationContext context,
        BedrockPacket rootPacket
    ) {
        var packets = new LinkedHashSet<BedrockPacket>();
        packets.add(rootPacket);
        for (var binding : bindings) {
            if (!binding.predicate().test(context)) {
                continue;
            }
            runStage(binding.stage(), context, packets);
            if (packets.isEmpty()) {
                break;
            }
        }
        return packets;
    }

    private static void runStage(
        TranslationStage stage,
        TranslationContext context,
        Collection<BedrockPacket> packets
    ) {
        if (packets.isEmpty()) {
            return;
        }
        var snapshot = new ArrayList<>(packets);
        for (var packet : snapshot) {
            stage.apply(context, packet, packets);
        }
    }

    private record StageBinding(
        TranslationStage stage,
        Predicate<TranslationContext> predicate
    ) {
        private StageBinding {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(predicate, "predicate");
        }
    }

    public static final class Builder {
        private final List<StageBinding> bindings = new ArrayList<>();

        public Builder addStage(TranslationStage stage) {
            return addStage(stage, ctx -> true);
        }

        public Builder addStage(
            TranslationStage stage,
            Predicate<TranslationContext> predicate
        ) {
            bindings.add(new StageBinding(stage, predicate));
            return this;
        }

        public TranslationPipeline build() {
            return new TranslationPipeline(List.copyOf(bindings));
        }
    }
}

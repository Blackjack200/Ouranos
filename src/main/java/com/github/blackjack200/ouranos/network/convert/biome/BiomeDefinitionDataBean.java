package com.github.blackjack200.ouranos.network.convert.biome;

import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.common.util.index.Unindexed;
import org.cloudburstmc.protocol.common.util.index.UnindexedList;

import java.util.List;

public class BiomeDefinitionDataBean {
    public float ashDensity;
    public float blueSporeDensity;
    public float depth;
    public float downfall;
    public String id;
    public ColorData mapWaterColour;
    public boolean rain;
    public float redSporeDensity;
    public float scale;
    public List<String> tags;
    public float temperature;
    public float whiteAshDensity;

    public BiomeDefinitionData toData() {
        return new BiomeDefinitionData(this.id != null && !this.id.equals("65535") ? new Unindexed<>(this.id) : null, this.temperature, this.downfall, this.redSporeDensity, this.blueSporeDensity, this.ashDensity, this.whiteAshDensity, this.depth, this.scale, this.mapWaterColour.toColor(), this.rain, new UnindexedList<>(this.tags), null);
    }
}



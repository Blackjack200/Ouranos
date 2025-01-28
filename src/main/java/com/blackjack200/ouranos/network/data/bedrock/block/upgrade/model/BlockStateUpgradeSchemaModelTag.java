package com.blackjack200.ouranos.network.data.bedrock.block.upgrade.model;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BlockStateUpgradeSchemaModelTag {
    @SerializedName("byte")
    public int byteValue;

    @SerializedName("int")
    public int intValue;

    @SerializedName("string")
    public String stringValue;

    public BlockStateUpgradeSchemaModelTag(int byteValue, int intValue, String stringValue) {
        this.byteValue = byteValue;
        this.intValue = intValue;
        this.stringValue = stringValue;
    }

}

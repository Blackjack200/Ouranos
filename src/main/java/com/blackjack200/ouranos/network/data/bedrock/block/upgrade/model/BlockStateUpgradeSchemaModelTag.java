package com.blackjack200.ouranos.network.data.bedrock.block.upgrade.model;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class BlockStateUpgradeSchemaModelTag {
    @SerializedName("byte")
    public int byteValue;

    @SerializedName("int")
    public int intValue;

    @SerializedName("string")
    public String stringValue;

    public BlockStateUpgradeSchemaModelTag() {
    }

}

package com.github.blackjack200.ouranos.data.bedrock.block.upgrade.model;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class BlockStateUpgradeSchemaModelTag {
    @SerializedName("byte")
    public Byte byteValue;

    @SerializedName("int")
    public Integer intValue;

    @SerializedName("string")
    public String stringValue;

    public BlockStateUpgradeSchemaModelTag() {
    }

}

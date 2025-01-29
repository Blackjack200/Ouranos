package com.github.blackjack200.ouranos.data.bedrock.item.upgrade.model;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

public class ItemIdMetaUpgradeSchemaModel {
    @Getter
    public Map<String, String> renamedIds = Collections.emptyMap();
    @Getter
    public Map<String, Map<Integer,String>> remappedMetas = Collections.emptyMap();
}

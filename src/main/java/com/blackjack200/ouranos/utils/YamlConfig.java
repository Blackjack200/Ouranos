package com.blackjack200.ouranos.utils;


import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.util.LinkedHashMap;

public class YamlConfig extends Config {
    public YamlConfig(File path) {
        super(path);
    }

    public Object getRaw(String key) {
        return this.data.get(key);
    }

    public void save() {
        Yaml yaml = new Yaml();
        FileUtil.writeBytes(Convert.toPrimitiveByteArray(yaml.dump(this.data)), this.path);
    }

    @SuppressWarnings("unchecked")
    public void reload() {
        Yaml yaml = new Yaml();
        this.data = (LinkedHashMap<String, Object>) yaml.load(FileUtil.getUtf8Reader(path));
    }
}
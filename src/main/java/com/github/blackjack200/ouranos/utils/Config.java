package com.github.blackjack200.ouranos.utils;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;

public abstract class Config {
    @Getter
    protected final File path;
    @Getter
    @Setter
    protected LinkedHashMap<String, Object> data;

    public Config(File path) {
        this.path = path;
        FileUtil.touch(this.path);
        this.reload();
    }

    public abstract Object getRaw(String key);

    public <T> T get(String key, Class<T> type) {
        return Convert.convert(type, this.getRaw(key));
    }

    public String getString(String key) {
        return Convert.toStr(this.getRaw(key));
    }

    public int getInteger(String key) {
        return Convert.toInt(this.getRaw(key));
    }

    public long getLong(String key) {
        return Convert.toLong(this.getRaw(key));
    }

    public byte getByte(String key) {
        return Convert.toByte(this.getRaw(key));
    }

    public char getChar(String key) {
        return Convert.toChar(this.getRaw(key));
    }

    public short getShort(String key) {
        return Convert.toShort(this.getRaw(key));
    }

    public HashMap<String, Object> getMap(String key) {
        return (HashMap<String, Object>) Convert.toMap(String.class, Object.class, this.getRaw(key));
    }

    public abstract void save();

    public abstract void reload();
}
package com.github.blackjack200.ouranos.network.convert.biome;

import java.awt.*;

public class ColorData {
    public int a;
    public int b;
    public int g;
    public int r;

    public Color toColor() {
        return new Color(r, g, b, a);
    }
}

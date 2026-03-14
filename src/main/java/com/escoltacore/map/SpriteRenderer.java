package com.escoltacore.map;

import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.entity.Player;

import java.awt.image.BufferedImage;

/**
 * Renders a single pre-scaled 128×128 sprite onto a map.
 * contextual=false → same image for all viewers, rendered exactly once and cached.
 */
public class SpriteRenderer extends MapRenderer {

    private final BufferedImage sprite;
    private boolean rendered = false;

    public SpriteRenderer(BufferedImage sprite) {
        super(false);
        this.sprite = sprite;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) return;
        canvas.drawImage(0, 0, sprite);
        rendered = true;
    }
}

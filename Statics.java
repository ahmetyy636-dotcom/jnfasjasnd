package wentra.module.impl.render;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import wentra.event.impl.RenderEvent;
import wentra.module.Module;
import wentra.module.setting.ModuleCategory;
import wentra.utils.render.RenderUtil;
import wentra.utils.render.font.FontUtil;

public class Statics
extends Module {
    private static long startTime = System.currentTimeMillis();
    private float smoothY = 40.0f;

    public Statics() {
        super("Statics", ModuleCategory.RENDER, 0);
    }

    @Override
    public void onEnable() {
        startTime = System.currentTimeMillis();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    private String time() {
        long s = (System.currentTimeMillis() - startTime) / 1000L;
        long h = s / 3600L;
        long m = s / 60L % 60L;
        return h + "h " + m + "m";
    }

    @Subscribe
    public void onRender(RenderEvent e) {
        this.smoothY += (45.0f - this.smoothY) * 0.08f;
        int x = 10;
        int y = (int)this.smoothY;
        RenderUtil.drawRect(x - 6, y - 8, 140.0, 40.0, new Color(20, 20, 20, 150).getRGB());
        FontUtil.drawString("Time: " + this.time(), x, y + 5, Color.WHITE.getRGB(), true);
        FontUtil.drawString("discord.gg/q2d9uxZJnD", x, y + 20, Color.CYAN.getRGB(), true);
    }

    @Override
    public int getKey() {
        return this.key;
    }

    @Override
    public void setKey(int keyCode) {
        this.key = keyCode;
    }
}

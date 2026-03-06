package wentra.module.impl.render;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import wentra.event.impl.RenderEvent;
import wentra.module.Module;
import wentra.module.impl.combat.KillAura;
import wentra.module.setting.BooleanSetting;
import wentra.module.setting.ModeSetting;
import wentra.module.setting.ModuleCategory;
import wentra.module.setting.NumberSetting;
import wentra.utils.mapper.Entity;
import wentra.utils.mapper.transformers.etc.impl.helpers.FontRenderer;
import wentra.utils.render.RenderUtil;
import wentra.utils.render.animations.ContinualAnimation;
import wentra.utils.render.glutils.ColorUtil;

public class TargetHud
extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Default", "Default");
    public final NumberSetting scale = new NumberSetting("Scale", 1.0f, 0.5f, 2.0f);
    public final BooleanSetting slide = new BooleanSetting("Slide", true);
    private static final int ANIM_SPEED = 50;
    private final ContinualAnimation openAnim = new ContinualAnimation();
    private final ContinualAnimation hpAnim = new ContinualAnimation();

    public TargetHud() {
        super("Target Hud", ModuleCategory.RENDER, 0);
        this.settings.add(this.mode);
        this.settings.add(this.scale);
        this.settings.add(this.slide);
    }

    @Override
    public void onEnable() {
        this.openAnim.setValue(0.0f);
        this.hpAnim.setValue(0.0f);
        super.onEnable();
    }

    @Subscribe
    public void onRender(RenderEvent e) {
        Object target = KillAura.getTarget();
        if (target == null) {
            this.openAnim.animate(0.0f, 50);
            return;
        }
        this.openAnim.animate(1.0f, 50);
        float anim = this.openAnim.getOutput();
        if (anim <= 0.01f) {
            return;
        }
        float hp = Entity.getEntityHealth(target).floatValue();
        float hpPct = Math.min(1.0f, hp / 20.0f);
        this.hpAnim.animate(hpPct, 120);
        int sw = e.getScreenWidth();
        int sh = e.getScreenHeight();
        float s = this.scale.getNumber();
        int W = (int)(210.0f * s);
        int H = (int)(56.0f * s);
        int x = sw / 2 - W / 2;
        int y = sh - 95;
        if (this.slide.getValue()) {
            y += (int)((1.0f - anim) * 40.0f);
        }
        int alpha = (int)(anim * 255.0f);
        int bgAlpha = (int)((float)alpha * 0.75f);
        RenderUtil.drawRect(x, y, W, H, bgAlpha << 24 | 0x141414);
        RenderUtil.drawImage("wentra:textures/steve.png", x + 8, y + 8, (int)(32.0f * s), (int)(32.0f * s));
        FontRenderer.drawText(Entity.getName(target), x + 48, y + 10, alpha << 24 | 0xFFFFFF);
        FontRenderer.drawText("\u2764 " + (int)hp, x + 48, y + 24, alpha << 24 | 0xFF5555);
        int barX = x + 48;
        int barY = y + H - 14;
        int barW = W - 56;
        RenderUtil.drawRect(barX, barY, barW, 6.0, bgAlpha << 24 | 0x2A2A2A);
        int hpColor = ColorUtil.interpolateColor(new Color(220, 60, 60).getRGB(), new Color(60, 220, 120).getRGB(), this.hpAnim.getOutput());
        RenderUtil.drawRect(barX, barY, (int)((float)barW * this.hpAnim.getOutput()), 6.0, alpha << 24 | hpColor & 0xFFFFFF);
    }
}

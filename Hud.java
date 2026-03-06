package wentra.module.impl.render;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import wentra.event.impl.RenderEvent;
import wentra.module.Module;
import wentra.module.ModuleManager;
import wentra.module.setting.ModeSetting;
import wentra.module.setting.ModuleCategory;
import wentra.module.setting.Setting;
import wentra.utils.render.RenderUtil;
import wentra.utils.render.font.FontUtil;

public class Hud
extends Module {
    public static long lastModuleOpen = 0L;
    private static final Map<String, Float> animMap = new HashMap<String, Float>();
    private static final Color LIGHT_PURPLE = new Color(185, 160, 255);
    private static final Color LIGHT_BLUE = new Color(140, 200, 255);
    private final ModeSetting hudColor = new ModeSetting("Hud Color", "TitanWare", "TitanWare", "Light Rainbow", "Rainbow", "Fade", "Double Color", "Analogous");

    public Hud() {
        super("Hud", ModuleCategory.RENDER, 0);
        this.settings.add(this.hudColor);
        this.toggle();
    }

    @Subscribe
    public void onRender2D(RenderEvent e) {
        this.renderWatermark();
        this.renderArrayList(e);
        Notifications.renderNotifications(e);
        lastModuleOpen = System.currentTimeMillis();
        ModuleManager.checkForKeyBind();
    }

    private int getHudColor(int index, int alpha) {
        String mode = this.hudColor.getValue();
        long time = System.currentTimeMillis();
        switch (mode) {
            case "TitanWare": {
                float t = (float)((Math.sin((double)time / 350.0 + (double)((float)index * 0.4f)) + 1.0) / 2.0);
                return this.blend(LIGHT_PURPLE, LIGHT_BLUE, t, alpha);
            }
            case "Light Rainbow": {
                Color c = Color.getHSBColor((float)(time % 3500L) / 3500.0f + (float)index * 0.02f, 0.45f, 1.0f);
                return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha).getRGB();
            }
            case "Rainbow": {
                Color c = Color.getHSBColor((float)(time % 2500L) / 2500.0f + (float)index * 0.03f, 1.0f, 1.0f);
                return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha).getRGB();
            }
            case "Fade": {
                float t = (float)((Math.sin((double)time / 420.0 + (double)((float)index * 0.5f)) + 1.0) / 2.0);
                return this.blend(LIGHT_PURPLE, LIGHT_BLUE.darker(), t, alpha);
            }
            case "Double Color": {
                Color c = index % 2 == 0 ? LIGHT_PURPLE : LIGHT_BLUE;
                return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha).getRGB();
            }
            case "Analogous": {
                float hue = (float)(time % 4000L) / 4000.0f;
                Color c = Color.getHSBColor(hue + (float)index * 0.015f, 0.6f, 1.0f);
                return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha).getRGB();
            }
        }
        return LIGHT_PURPLE.getRGB();
    }

    private int blend(Color c1, Color c2, float t, int alpha) {
        return new Color((int)((float)c1.getRed() + (float)(c2.getRed() - c1.getRed()) * t), (int)((float)c1.getGreen() + (float)(c2.getGreen() - c1.getGreen()) * t), (int)((float)c1.getBlue() + (float)(c2.getBlue() - c1.getBlue()) * t), alpha).getRGB();
    }

    private void renderWatermark() {
        String name = "TitanWare";
        String version = "1.0";
        float scale = 2.2f;
        GL11.glPushMatrix();
        GL11.glScalef((float)scale, (float)scale, (float)1.0f);
        float x = 6.0f / scale;
        float y = 6.0f / scale;
        float width = FontUtil.getStringWidth(name);
        float height = FontUtil.getHeight();
        
        RenderUtil.drawRect(x - 2.0f, y - 2.0f, width + 4.0f, height + 4.0f, new Color(14, 14, 14, 170).getRGB());
        RenderUtil.drawRect(x - 2.0f, y - 2.0f, 2.0, height + 4.0f, this.getHudColor(0, 255));
        float off = 0.0f;
        for (int i = 0; i < name.length(); ++i) {
            String c = String.valueOf(name.charAt(i));
            long time = System.currentTimeMillis();
            Color rainbow = Color.getHSBColor((float)(time % 2500L) / 2500.0f + (float)i * 0.05f, 1.0f, 1.0f);
            FontUtil.drawString(c, x + off, y, rainbow.getRGB(), true);
            off += (float)FontUtil.getStringWidth(c);
        }
        GL11.glPushMatrix();
        GL11.glScalef((float)0.42f, (float)0.42f, (float)1.0f);
        FontUtil.drawString(version, (x + off + 2.0f) / 0.42f, (y - 1.0f) / 0.42f, Color.WHITE.getRGB(), true);
        GL11.glPopMatrix();
        GL11.glPopMatrix();
    }

    private void renderArrayList(RenderEvent e) {
        List<Module> mods = ModuleManager.enabled();
        mods.sort((a, b) -> FontUtil.getStringWidth(b.name) - FontUtil.getStringWidth(a.name));
        float y = 10.0f;
        int index = 0;
        int screenX = e.getScreenWidth();
        for (Module m : mods) {
            String mode = "";
            for (Setting s : m.settings) {
                if (!(s instanceof ModeSetting)) continue;
                mode = " " + ((ModeSetting)s).getValue();
                break;
            }
            String text = m.name + mode;
            float textWidth = FontUtil.getStringWidth(text);
            float height = FontUtil.getHeight() + 4;
            float width = textWidth + 8.0f;
            float anim = animMap.getOrDefault(m.name, Float.valueOf(0.0f)).floatValue();
            anim += (1.0f - anim) * 0.18f;
            animMap.put(m.name, Float.valueOf(anim));
            float x = (float)screenX - (width + 4.0f) * anim;
            
            // Modern, şık arka plan ve kenar çizgileri
            RenderUtil.drawRect(x - 1.0f, y, width + 1.0f, height, new Color(5, 5, 5, 180).getRGB());
            
            // Sol ince çizgi
            RenderUtil.drawRect(x - 2.0f, y, 1.0, height, this.getHudColor(index, 255));
            
            // Sağ kalın çizgi
            RenderUtil.drawRect(x + width - 2.0f, y, 2.0, height, this.getHudColor(index, 255));
            
            FontUtil.drawString(m.name, x + 3.0f, y + 2.0f, this.getHudColor(index, 255), true);
            if (!mode.isEmpty()) {
                FontUtil.drawString(mode, x + 3.0f + (float)FontUtil.getStringWidth(m.name), y + 2.0f, new Color(180, 180, 180).getRGB(), true);
            }
            y += height;
            ++index;
        }
    }
}

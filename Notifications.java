package wentra.module.impl.render;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import wentra.event.impl.RenderEvent;
import wentra.module.Module;
import wentra.module.setting.BooleanSetting;
import wentra.module.setting.ModeSetting;
import wentra.module.setting.ModuleCategory;
import wentra.module.setting.NumberSetting;
import wentra.utils.render.RenderUtil;
import wentra.utils.render.font.FontUtil;

public class Notifications
extends Module {
    public static ModeSetting mode = new ModeSetting("Mode", "TitanWare", "TitanWare", "Wentra", "SX", "Exhi", "Minimal");
    public static final BooleanSetting sounds = new BooleanSetting("Sounds", true);
    public static final NumberSetting duration = new NumberSetting("Duration", 2.5f, 1.0f, 5.0f);

    private static final List<NotifEntry> activeNotifs = new ArrayList<NotifEntry>();

    // TitanWare renk paleti
    private static final Color TITAN_PURPLE = new Color(185, 160, 255);
    private static final Color TITAN_BLUE = new Color(140, 200, 255);

    public Notifications() {
        super("Notifications", ModuleCategory.RENDER, 0);
        this.settings.add(mode);
        this.settings.add(sounds);
        this.settings.add(duration);
    }

    private static int getTitanColor(int index, int alpha) {
        long time = System.currentTimeMillis();
        float t = (float)((Math.sin((double)time / 350.0 + (double)((float)index * 0.4f)) + 1.0) / 2.0);
        return blend(TITAN_PURPLE, TITAN_BLUE, t, alpha);
    }

    private static int blend(Color c1, Color c2, float t, int alpha) {
        int r = (int)((float)c1.getRed() + (float)(c2.getRed() - c1.getRed()) * t);
        int g = (int)((float)c1.getGreen() + (float)(c2.getGreen() - c1.getGreen()) * t);
        int b = (int)((float)c1.getBlue() + (float)(c2.getBlue() - c1.getBlue()) * t);
        return new Color(r, g, b, Math.max(0, Math.min(255, alpha))).getRGB();
    }

    /**
     * Static render — Hud.java'dan cagrilir, her zaman calisir
     */
    public static void renderNotifications(RenderEvent e) {
        if (activeNotifs == null || activeNotifs.isEmpty()) return;

        int sw = e.getScreenWidth();
        int sh = e.getScreenHeight();
        long now = System.currentTimeMillis();
        int yOffset = 0;
        float dur = 2500.0f;
        try {
            dur = duration.getNumber() * 1000.0f;
        } catch (Exception ex) {
            dur = 2500.0f;
        }

        int notifIndex = 0;
        Iterator<NotifEntry> it = activeNotifs.iterator();
        while (it.hasNext()) {
            NotifEntry n = it.next();
            long elapsed = now - n.startTime;
            if (elapsed > (long)dur) {
                it.remove();
                continue;
            }
            float progress = (float)elapsed / dur;

            // Fade
            float alpha = 1.0f;
            if (progress < 0.1f) {
                alpha = progress / 0.1f;
            } else if (progress > 0.85f) {
                alpha = (1.0f - progress) / 0.15f;
            }
            alpha = Math.max(0.0f, Math.min(1.0f, alpha));
            int a = (int)(alpha * 240.0f);

            // Slide
            float slideOffset = 0.0f;
            if (progress < 0.1f) {
                slideOffset = (1.0f - progress / 0.1f) * 140.0f;
            } else if (progress > 0.85f) {
                slideOffset = ((progress - 0.85f) / 0.15f) * 140.0f;
            }

            // Boyutlar
            int notifW = 220;
            int notifH = 36;

            // Sag alt pozisyon (default)
            int nx = sw - notifW - 6 + (int)slideOffset;
            int ny = sh - 40 - yOffset;

            // Koyu arka plan
            RenderUtil.drawRect(nx, ny, notifW, notifH, new Color(8, 8, 12, (int)(alpha * 210.0f)).getRGB());

            // Ust parlak cizgi — TitanWare gradyani
            RenderUtil.drawRect(nx, ny, notifW, 1.0, getTitanColor(notifIndex, a));

            // Sol kenar — tip rengine gore
            int accentColor;
            String icon;
            switch (n.type) {
                case "SUCCESS":
                    accentColor = new Color(80, 230, 150, a).getRGB();
                    icon = "\u2714 ";
                    break;
                case "DISABLE":
                    accentColor = new Color(255, 80, 90, a).getRGB();
                    icon = "\u2716 ";
                    break;
                case "WARNING":
                    accentColor = new Color(255, 200, 60, a).getRGB();
                    icon = "\u26A0 ";
                    break;
                default:
                    accentColor = getTitanColor(notifIndex, a);
                    icon = "\u2139 ";
                    break;
            }

            // Sol kenar cizgisi
            RenderUtil.drawRect(nx, ny + 1, 2.5, notifH - 1, accentColor);

            // TitanWare basligi
            int titleColor = getTitanColor(notifIndex, a);
            FontUtil.drawString("TitanWare", nx + 8, ny + 5, titleColor, true);

            // Mesaj
            int textColor = new Color(210, 215, 225, a).getRGB();
            FontUtil.drawString(icon + n.message, nx + 8, ny + 17, textColor, true);

            // Alt ilerleme cubugu
            float barProgress = Math.min(1.0f, progress);
            int barW = (int)((float)(notifW - 3) * (1.0f - barProgress));
            int barColor = getTitanColor(notifIndex + (int)(barProgress * 10.0f), (int)(alpha * 200.0f));
            RenderUtil.drawRect(nx + 2.5, ny + notifH - 2, barW, 2.0, barColor);

            yOffset += notifH + 5;
            ++notifIndex;
        }
    }

    public static void push(String type, String message) {
        if (activeNotifs == null) return;
        activeNotifs.add(new NotifEntry(type, message, System.currentTimeMillis()));
        if (activeNotifs.size() > 8) {
            activeNotifs.remove(0);
        }
    }

    public static void pushSuccess(String message) {
        push("SUCCESS", message);
    }

    public static void pushDisable(String message) {
        push("DISABLE", message);
    }

    public static void pushWarning(String message) {
        push("WARNING", message);
    }

    public static void pushInfo(String message) {
        push("INFO", message);
    }

    private static class NotifEntry {
        final String type;
        final String message;
        final long startTime;

        NotifEntry(String type, String message, long startTime) {
            this.type = type;
            this.message = message;
            this.startTime = startTime;
        }
    }
}

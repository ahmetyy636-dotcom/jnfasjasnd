package wentra.utils.render.notification;

import java.awt.Color;
import wentra.utils.render.RenderUtil;
import wentra.utils.render.animations.Animation;
import wentra.utils.render.animations.Direction;
import wentra.utils.render.animations.impl.DecelerateAnimation;
import wentra.utils.render.font.FontUtil;
import wentra.utils.render.notification.NotificationManager;
import wentra.utils.time.TimerUtil;

public class Notification {
    private final NotificationType type;
    private final String title;
    private final String description;
    private final float time;
    private final TimerUtil timer;
    private final Animation animation;

    public Notification(NotificationType type, String title, String description) {
        this(type, title, description, NotificationManager.getToggleTime());
    }

    public Notification(NotificationType type, String title, String description, float time) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.time = time * 1000.0f;
        this.timer = new TimerUtil();
        this.animation = new DecelerateAnimation(250, 1.0, Direction.FORWARDS);
    }

    public void draw(float x, float y, float width, float height) {
        boolean success = this.type == NotificationType.SUCCESS;
        int alpha = 220;

        // Koyu premium arka plan
        RenderUtil.drawRect2(x, y, width, height, new Color(8, 8, 12, 210).getRGB());

        // Sol kenar cizgisi
        Color accent = success ? new Color(80, 230, 150) : new Color(255, 80, 90);
        RenderUtil.drawRect2(x, y + 1, 2.5, height - 1, accent.getRGB());

        // Ust TitanWare gradyan cizgisi
        long time = System.currentTimeMillis();
        float t = (float)((Math.sin((double)time / 350.0) + 1.0) / 2.0);
        Color c1 = new Color(185, 160, 255);
        Color c2 = new Color(140, 200, 255);
        int tr = (int)((float)c1.getRed() + (float)(c2.getRed() - c1.getRed()) * t);
        int tg = (int)((float)c1.getGreen() + (float)(c2.getGreen() - c1.getGreen()) * t);
        int tb = (int)((float)c1.getBlue() + (float)(c2.getBlue() - c1.getBlue()) * t);
        int titanColor = new Color(tr, tg, tb, alpha).getRGB();
        RenderUtil.drawRect2(x, y, width, 1.0, titanColor);

        // TitanWare basligi
        FontUtil.drawString("TitanWare", x + 8, y + 5, titanColor, true);

        // Ikon
        String icon = success ? "\u2714 " : "\u2716 ";

        // Mesaj
        FontUtil.drawString(icon + this.description, x + 8, y + 18, new Color(210, 215, 225).getRGB(), true);
    }

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public float getTime() {
        return this.time;
    }

    public TimerUtil getTimer() {
        return this.timer;
    }

    public Animation getAnimation() {
        return this.animation;
    }

    public static enum NotificationType {
        SUCCESS,
        DISABLE;
    }
}

package wentra.utils.render.notification;

import java.awt.Color;
import java.util.concurrent.CopyOnWriteArrayList;
import wentra.utils.render.animations.Direction;
import wentra.utils.render.font.FontUtil;
import wentra.utils.render.RenderUtil;
import wentra.utils.render.glutils.ScaledResolution;
import wentra.utils.render.notification.Notification;

public class NotificationManager {
    private static float toggleTime = 2.0f;
    private static final CopyOnWriteArrayList<Notification> notifications = new CopyOnWriteArrayList();

    // TitanWare renk paleti
    private static final Color TITAN_PURPLE = new Color(185, 160, 255);
    private static final Color TITAN_BLUE = new Color(140, 200, 255);

    public static void post(Notification.NotificationType type, String title, String description) {
        notifications.add(new Notification(type, title, description));
    }

    public static void post(Notification.NotificationType type, String title, String description, float time) {
        notifications.add(new Notification(type, title, description, time));
    }

    public static float getToggleTime() {
        return toggleTime;
    }

    public static void setToggleTime(float toggleTime) {
        NotificationManager.toggleTime = toggleTime;
    }

    private static int getTitanColor(int index, int alpha) {
        long time = System.currentTimeMillis();
        float t = (float)((Math.sin((double)time / 350.0 + (double)((float)index * 0.4f)) + 1.0) / 2.0);
        int r = (int)((float)TITAN_PURPLE.getRed() + (float)(TITAN_BLUE.getRed() - TITAN_PURPLE.getRed()) * t);
        int g = (int)((float)TITAN_PURPLE.getGreen() + (float)(TITAN_BLUE.getGreen() - TITAN_PURPLE.getGreen()) * t);
        int b = (int)((float)TITAN_PURPLE.getBlue() + (float)(TITAN_BLUE.getBlue() - TITAN_PURPLE.getBlue()) * t);
        return new Color(r, g, b, Math.max(0, Math.min(255, alpha))).getRGB();
    }

    public static void render() {
        float yOffset = 10.0f;
        float notificationHeight = 36.0f;
        ScaledResolution sr = new ScaledResolution();
        int idx = 0;
        for (Notification notification : notifications) {
            notification.getAnimation().update();
            boolean expired = notification.getTimer().hasTimeElapsed((long)notification.getTime());
            if (expired) {
                notification.getAnimation().setDirection(Direction.BACKWARDS);
            }
            if (expired && notification.getAnimation().isDone()) {
                notifications.remove(notification);
                continue;
            }

            float notificationWidth = Math.max(FontUtil.getStringWidth(notification.getTitle()), FontUtil.getStringWidth(notification.getDescription())) + 40;
            notificationWidth = Math.max(notificationWidth, 180);
            float baseX = (float)sr.getScaledWidth() - notificationWidth - 10.0f;
            float progress = notification.getAnimation().getOutput().floatValue();
            float x = baseX + (notificationWidth + 15.0f) * (1.0f - progress);
            float y = (float)sr.getScaledHeight() - yOffset - notificationHeight;

            int alpha = (int)(progress * 230.0f);

            // Koyu premium arka plan
            RenderUtil.drawRect(x, y, notificationWidth, notificationHeight, new Color(8, 8, 12, (int)(progress * 210.0f)).getRGB());

            // Ust TitanWare gradyan cizgisi
            RenderUtil.drawRect(x, y, notificationWidth, 1.0, getTitanColor(idx, alpha));

            // Sol kenar — tip rengine gore
            boolean success = notification.getTitle().contains("enabled") || notification.getDescription().contains("enabled");
            int accentColor;
            if (success) {
                accentColor = new Color(80, 230, 150, alpha).getRGB();
            } else {
                accentColor = new Color(255, 80, 90, alpha).getRGB();
            }
            RenderUtil.drawRect(x, y + 1, 2.5, notificationHeight - 1, accentColor);

            // TitanWare basligi
            FontUtil.drawString("TitanWare", x + 8, y + 5, getTitanColor(idx, alpha), true);

            // Mesaj
            FontUtil.drawString(notification.getDescription(), x + 8, y + 18, new Color(210, 215, 225, alpha).getRGB(), true);

            // Alt ilerleme cubugu
            float timeProgress = 1.0f;
            try {
                timeProgress = (float)notification.getTimer().getElapsedTime() / notification.getTime();
                timeProgress = Math.min(1.0f, timeProgress);
            } catch (Exception e) {}
            int barW = (int)((notificationWidth - 3) * (1.0f - timeProgress));
            RenderUtil.drawRect(x + 2.5, y + notificationHeight - 2, barW, 2.0, getTitanColor(idx + (int)(timeProgress * 10.0f), (int)(progress * 200.0f)));

            yOffset += notificationHeight + 6.0f;
            ++idx;
        }
    }
}

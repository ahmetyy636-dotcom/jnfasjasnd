package wentra.module;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import wentra.event.Event;
import wentra.event.EventBus;
import wentra.event.impl.EventRender3D;
import wentra.module.ModuleManager;
import wentra.module.impl.render.Notifications;
import wentra.module.setting.ModuleCategory;
import wentra.module.setting.Setting;
import wentra.utils.SoundUtils;
import wentra.utils.render.animations.Animation;
import wentra.utils.render.animations.impl.DecelerateAnimation;
import wentra.utils.render.notification.Notification;
import wentra.utils.render.notification.NotificationManager;


public abstract class Module {
    public final Animation animation = new DecelerateAnimation(250, 1.0);
    public final List<Setting> settings = new ArrayList<Setting>();
    public ModuleCategory cat;
    public String name;
    public int key;
    public boolean toggled = false;
    public boolean keyIsPressed = false;
    private String tag = "";
    private Clip clip;
    private FloatControl volume;

    public Module(String name, ModuleCategory cat, int key) {
        this.name = name;
        this.cat = cat;
        this.key = key;
    }

    public boolean isToggled() {
        return this.toggled;
    }

    public String getName() {
        return this.name;
    }

    public boolean hasMode() {
        return this.tag != null;
    }

    public Animation getAnimation() {
        return this.animation;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onRender2DEvent() {
    }

    public void onRender2DEvent(float partialTicks) {
    }

    public void onRender3DEvent(float partialTicks) {
    }

    public void onRender3DEvent(EventRender3D event) {
    }

    public void onRender2DEvent(Event event) {
    }

    public void onRenderParticlesEvent(float partialTicks) {
    }

    public void toggle() {
        this.toggled = !this.toggled;
        if (!(this.name.equals("ClickTP") || this.name.equals("UpClip") || this.name.equals("DownClip"))) {
            Notification.NotificationType type = this.toggled ? Notification.NotificationType.SUCCESS : Notification.NotificationType.DISABLE;
            try {
                SoundUtils.playSound(this.toggled ? "enable.wav" : "disable.wav", 0.3f);
                // Orijinal NotificationManager sistemi
                NotificationManager.post(type, "Module Toggled", this.name + " was " + (this.toggled ? "\u00a7aenabled" : "\u00a7cdisabled"));
                // TitanWare bildirim sistemi (Hud uzerinden render edilir)
                if (this.toggled) {
                    Notifications.pushSuccess(this.name + " was enabled");
                } else {
                    Notifications.pushDisable(this.name + " was disabled");
                }
            }
            catch (Exception e) {
                System.err.println("Error posting notification for " + this.name + ": " + e.getMessage());
            }
        }
        try {
            if (this.toggled) {
                this.onEnable();
                EventBus.subscribe(this);
            } else {
                this.onDisable();
                EventBus.unSubscribe(this);
            }
        }
        catch (Exception e) {
            System.err.println("Error toggling module " + this.name + ": " + e.getMessage());
            this.toggled = !this.toggled;
        }
    }

    public static void render2DEvent() {
        for (Module m : ModuleManager.listAllModules()) {
            try {
                m.onRender2DEvent();
            }
            catch (Exception e) {
                System.err.println("Error in render2DEvent for " + m.getName() + ": " + e.getMessage());
            }
        }
    }

    public static void preRender2DEvent(float pt) {
        for (Module m : ModuleManager.listAllModules()) {
            try {
                m.onRender2DEvent(pt);
            }
            catch (Exception e) {
                System.err.println("Error in preRender2DEvent for " + m.getName() + ": " + e.getMessage());
            }
        }
    }

    public static void renderParticlesEvent(float pt) {
        for (Module m : ModuleManager.listAllModules()) {
            try {
                m.onRender3DEvent(pt);
            }
            catch (Exception e) {
                System.err.println("Error in renderParticlesEvent for " + m.getName() + ": " + e.getMessage());
            }
        }
    }

    public int getKey() {
        return 0;
    }

    public void setKey(int keyCode) {
    }
}

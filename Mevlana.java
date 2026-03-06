package wentra.module.impl.combat;

import com.google.common.eventbus.Subscribe;
import java.lang.reflect.Constructor;
import java.util.Random;
import wentra.event.impl.TickEvent;
import wentra.module.Module;
import wentra.module.setting.BooleanSetting;
import wentra.module.setting.ModeSetting;
import wentra.module.setting.ModuleCategory;
import wentra.module.setting.NumberSetting;
import wentra.utils.mapper.Entity;
import wentra.utils.mapper.Mapper;
import wentra.utils.mapper.transformers.etc.impl.helpers.Packet;

public class Mevlana
extends Module {
    public static final ModeSetting yawMode = new ModeSetting("Yaw Mode", "Spin", "Spin", "Jitter", "Snap", "Reverse");
    public static final ModeSetting pitchMode = new ModeSetting("Pitch Mode", "Down", "Down", "Up", "Jitter", "Normal");
    public static final NumberSetting spinSpeed = new NumberSetting("Spin Speed", 30.0f, 1.0f, 180.0f);
    public static final BooleanSetting randomPitch = new BooleanSetting("Random Pitch", false);
    public static final BooleanSetting silent = new BooleanSetting("Silent", false);

    private float currentYaw = 0.0f;
    private final Random rnd = new Random();

    public Mevlana() {
        super("Mevlana", ModuleCategory.COMBAT, 0);
        this.settings.add(yawMode);
        this.settings.add(pitchMode);
        this.settings.add(spinSpeed);
        this.settings.add(randomPitch);
        this.settings.add(silent);
    }

    @Override
    public void onEnable() {
        try {
            Object self = Entity.getThePlayer();
            if (self != null) {
                this.currentYaw = Mapper.rotationYaw.getFloat(self);
            }
        } catch (Exception e) {
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Subscribe
    public void onTick(TickEvent e) {
        Object self = Entity.getThePlayer();
        if (self == null) return;

        float yaw;
        float pitch;
        try {
            yaw = Mapper.rotationYaw.getFloat(self);
            pitch = Mapper.rotationPitch.getFloat(self);
        } catch (Exception ex) {
            return;
        }

        String yawModeValue = yawMode.getMode();
        if (yawModeValue.equals("Spin")) {
            currentYaw += (float)spinSpeed.getNumber();
            yaw = currentYaw;
        } else if (yawModeValue.equals("Jitter")) {
            yaw += (rnd.nextBoolean() ? 90 : -90);
        } else if (yawModeValue.equals("Snap")) {
            yaw += 180;
        } else if (yawModeValue.equals("Reverse")) {
            yaw -= 180;
        }

        String pitchModeValue = pitchMode.getMode();
        if (pitchModeValue.equals("Down")) {
            pitch = 90.0f;
        } else if (pitchModeValue.equals("Up")) {
            pitch = -90.0f;
        } else if (pitchModeValue.equals("Jitter")) {
            pitch = 90.0f - rnd.nextFloat() * 180.0f;
        }

        if (randomPitch.isToggled()) {
            pitch += rnd.nextFloat() * 10.0f - 5.0f;
        }

        if (silent.isToggled()) {
            try {
                if (Mapper.C06PacketPlayerPosLook == null) {
                    Mapper.mapC03C05C06();
                }
                double posX = Mapper.posX.getDouble(self);
                double posY = Mapper.posY.getDouble(self);
                double posZ = Mapper.posZ.getDouble(self);
                boolean onGround = false;
                try {
                    onGround = Mapper.onGround.getBoolean(self);
                } catch (Exception ex) {
                }
                Object pkt = Mapper.newC06(posX, posY, posZ, yaw, pitch, onGround);
                if (pkt != null) {
                    Packet.addToSendQueue(pkt);
                }
            } catch (Throwable ignored) {
            }
        } else {
            try {
                Mapper.rotationYaw.setFloat(self, yaw);
                Mapper.rotationPitch.setFloat(self, pitch);
            } catch (Exception ignored) {
            }
        }
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

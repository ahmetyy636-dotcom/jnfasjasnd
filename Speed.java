package wentra.module.impl.movement;

import com.google.common.eventbus.Subscribe;
import org.lwjgl.input.Keyboard;
import wentra.event.impl.TickEvent;
import wentra.module.Module;
import wentra.module.setting.BooleanSetting;
import wentra.module.setting.ModuleCategory;
import wentra.module.setting.NumberSetting;
import wentra.utils.mapper.Entity;

public class Speed extends Module {
    private final NumberSetting speedMult = new NumberSetting("SpeedMult", 1.6f, 0.5f, 3.0f);
    private final NumberSetting accelGround = new NumberSetting("AccelGround", 0.65f, 0.1f, 1.0f);
    private final NumberSetting accelAir = new NumberSetting("AccelAir", 0.3f, 0.05f, 1.0f);
    private final BooleanSetting alwaysOn = new BooleanSetting("AlwaysOn", true);
    private final BooleanSetting autoJump = new BooleanSetting("AutoJump", true);
    private final BooleanSetting useKeyboard = new BooleanSetting("KeyboardFallback", true);
    private final BooleanSetting debug = new BooleanSetting("DebugLog", false);

    public Speed() {
        super("Speed", ModuleCategory.MOVEMENT, Keyboard.KEY_X);
        this.settings.add(this.speedMult);
        this.settings.add(this.accelGround);
        this.settings.add(this.accelAir);
        this.settings.add(this.alwaysOn);
        this.settings.add(this.autoJump);
        this.settings.add(this.useKeyboard);
        this.settings.add(this.debug);
    }

    @Override
    public void onEnable() {
        // Reset state if needed
    }

    @Subscribe
    public void onTick(TickEvent e) {
        Object player = Entity.getThePlayer();
        if (player == null) return;

        boolean onGround = Entity.isOnGround(player);
        if (!this.alwaysOn.isToggled() && !onGround) return;

        double forward = 0.0;
        double strafe = 0.0;
        
        try {
            forward = Entity.getMoveForward();
            strafe = Entity.getMoveStrafe();
        } catch (Throwable ignored) {}

        // Fallback to keyboard if move inputs are zero
        if (this.useKeyboard.isToggled() && Math.abs(forward) < 0.001 && Math.abs(strafe) < 0.001) {
            if (Keyboard.isKeyDown(Keyboard.KEY_W)) forward += 1.0;
            if (Keyboard.isKeyDown(Keyboard.KEY_S)) forward -= 1.0;
            if (Keyboard.isKeyDown(Keyboard.KEY_D)) strafe += 1.0;
            if (Keyboard.isKeyDown(Keyboard.KEY_A)) strafe -= 1.0;
        }

        double lenSq = forward * forward + strafe * strafe;
        if (lenSq < 1.0E-6) return;

        double inv = 1.0 / Math.sqrt(lenSq);
        forward *= inv;
        strafe *= inv;

        float yaw = this.safeYaw(player);
        double rad = Math.toRadians(yaw);
        
        // Directional Math
        double dirX = forward * -Math.sin(rad) + strafe * Math.cos(rad);
        double dirZ = forward * Math.cos(rad) + strafe * Math.sin(rad);
        
        double targetSpeed = 0.2873 * (double)this.speedMult.getNumber();
        double targetX = dirX * targetSpeed;
        double targetZ = dirZ * targetSpeed;
        
        double curX = this.safeMotionX(player);
        double curZ = this.safeMotionZ(player);
        
        double accel = clamp01(onGround ? (double)this.accelGround.getNumber() : (double)this.accelAir.getNumber());
        
        double newX = curX + (targetX - curX) * accel;
        double newZ = curZ + (targetZ - curZ) * accel;

        Entity.setMotionX(newX);
        Entity.setMotionZ(newZ);

        if (this.autoJump.isToggled() && onGround && (Math.abs(forward) > 0.001 || Math.abs(strafe) > 0.001)) {
            Entity.setMotionY(0.405f);
        }

        if (this.debug.isToggled()) {
            double spd = Math.sqrt(newX * newX + newZ * newZ);
            Entity.addChatMessage(String.format("[Speed] yaw=%.1f onG=%s spd=%.3f", yaw, onGround, spd));
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

    private float safeYaw(Object p) {
        try { return Entity.getRotationYaw(p); }
        catch (Throwable t1) {
            try { return Entity.getRotationYaw(); }
            catch (Throwable t2) { return 0.0f; }
        }
    }

    private double safeMotionX(Object p) {
        try { return Entity.getMotionX2(p); }
        catch (Throwable t1) {
            try { return Entity.getMotionX(); }
            catch (Throwable t2) { return 0.0; }
        }
    }

    private double safeMotionZ(Object p) {
        try { return Entity.getMotionZ2(p); }
        catch (Throwable t1) {
            try { return Entity.getMotionZ(); }
            catch (Throwable t2) { return 0.0; }
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}


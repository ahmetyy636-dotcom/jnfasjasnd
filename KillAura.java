package wentra.module.impl.combat;

import com.google.common.eventbus.Subscribe;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;
import wentra.event.impl.RenderEvent;
import wentra.event.impl.TickEvent;
import wentra.module.Module;
import wentra.module.setting.BooleanSetting;
import wentra.module.setting.ModeSetting;
import wentra.module.setting.ModuleCategory;
import wentra.module.setting.NumberSetting;
import wentra.utils.mapper.Entity;
import wentra.utils.mapper.Mapper;
import wentra.utils.mapper.transformers.etc.C02Helper;
import wentra.utils.mapper.transformers.etc.impl.helpers.Packet;
import wentra.utils.render.animations.ContinualAnimation;
import wentra.utils.time.TimerUtil;

public class KillAura
extends Module {
    public static final ModeSetting mode = new ModeSetting("Mode", "Normal", "Normal", "Legit");
    public static final NumberSetting max = new NumberSetting("MaxCPS", 15.0f, 2.0f, 20.0f);
    public static final NumberSetting min = new NumberSetting("MinCPS", 12.0f, 1.0f, 20.0f);
    public static final NumberSetting rng = new NumberSetting("Range", 4.0f, 3.0f, 6.0f);
    public static final BooleanSetting silentAim = new BooleanSetting("Silent Aim", true);
    public static final BooleanSetting antiAim = new BooleanSetting("Anti-Aim", false);
    public static final ModeSetting targetMode = new ModeSetting("Target Mode", "Normal", "Normal", "Guide TP");
    public static final BooleanSetting advancedAntiBot = new BooleanSetting("Advanced AntiBot", true);
    public static final NumberSetting legitMaxCPS = new NumberSetting("Legit MaxCPS", 11.0f, 1.0f, 14.0f);
    public static final NumberSetting legitMinCPS = new NumberSetting("Legit MinCPS", 8.0f, 1.0f, 14.0f);
    public static final NumberSetting legitRange = new NumberSetting("Legit Range", 3.0f, 2.5f, 3.5f);
    public static final NumberSetting legitAimSpeed = new NumberSetting("Aim Speed", 50.0f, 10.0f, 100.0f);
    public static final BooleanSetting legitSprintReset = new BooleanSetting("Sprint Reset", true);
    private static final ContinualAnimation anim = new ContinualAnimation();
    private static final ContinualAnimation healthAnim = new ContinualAnimation();
    private static final ContinualAnimation targetOpacityAnim = new ContinualAnimation();
    private final TimerUtil cpsT = new TimerUtil();
    private final TimerUtil atkT = new TimerUtil();
    private final TimerUtil tpTimer = new TimerUtil();
    private final Random r = new Random();
    private int delay;
    private static Object tgt;
    private float[] serverRotations = new float[2];
    private float realYaw = 0.0f;
    private float realPitch = 0.0f;
    private boolean isTPing = false;
    private double tpStartX;
    private double tpStartY;
    private double tpStartZ;
    private float smoothYaw = 0.0f;
    private float smoothPitch = 0.0f;
    private boolean smoothInitialized = false;

    public KillAura() {
        super("KillAura", ModuleCategory.COMBAT, 19);
        this.settings.add(mode);
        this.settings.add(max);
        this.settings.add(min);
        this.settings.add(rng);
        this.settings.add(silentAim);
        this.settings.add(antiAim);
        this.settings.add(targetMode);
        this.settings.add(advancedAntiBot);
        this.settings.add(legitMaxCPS);
        this.settings.add(legitMinCPS);
        this.settings.add(legitRange);
        this.settings.add(legitAimSpeed);
        this.settings.add(legitSprintReset);
    }

    @Override
    public void onEnable() {
        this.reset();
        this.cpsT.reset();
        this.atkT.reset();
        this.tpTimer.reset();
        tgt = null;
        this.isTPing = false;
        this.serverRotations[0] = 0.0f;
        this.serverRotations[1] = 0.0f;
        this.smoothInitialized = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        tgt = null;
        this.isTPing = false;
        try {
            Object player = Entity.getThePlayer();
            if (player != null && antiAim.isToggled() && mode.getMode().equals("Normal")) {
                Mapper.rotationYaw.setFloat(player, this.realYaw);
                Mapper.rotationPitch.setFloat(player, this.realPitch);
            }
        }
        catch (Exception exception) {
        }
        super.onDisable();
    }

    private void reset() {
        if (mode.getMode().equals("Legit")) {
            int minCPS = (int)legitMinCPS.getNumber();
            int maxCPS = (int)legitMaxCPS.getNumber();
            if (minCPS > maxCPS) minCPS = maxCPS;
            int randomCPS = minCPS + this.r.nextInt(Math.max(1, maxCPS - minCPS + 1));
            int baseDelay = 1000 / Math.max(1, randomCPS);
            int jitter = this.r.nextInt(Math.max(1, baseDelay / 5)) - baseDelay / 10;
            this.delay = Math.max(70, baseDelay + jitter);
        } else {
            int minCPS = (int)min.getNumber();
            int maxCPS = (int)max.getNumber();
            int randomCPS = minCPS + this.r.nextInt(Math.max(1, maxCPS - minCPS + 1));
            this.delay = 1000 / randomCPS;
        }
    }

    private float dist(Object e) {
        try {
            return e == null ? Float.MAX_VALUE : ((Float)Mapper.getDistanceToEntity.invoke(Entity.getThePlayer(), e)).floatValue();
        }
        catch (Exception ex) {
            return Float.MAX_VALUE;
        }
    }

    private float[] getRotations(Object entity) {
        try {
            Object player = Entity.getThePlayer();
            double playerX = Mapper.posX.getDouble(player);
            double playerY = Mapper.posY.getDouble(player) + (Double)Mapper.getEyeHeight.invoke(player, new Object[0]);
            double playerZ = Mapper.posZ.getDouble(player);
            double targetX = Mapper.posX.getDouble(entity);
            double targetY = Mapper.posY.getDouble(entity) + (Double)Mapper.getEyeHeight.invoke(entity, new Object[0]) * 0.9;
            double targetZ = Mapper.posZ.getDouble(entity);
            double deltaX = targetX - playerX;
            double deltaY = targetY - playerY;
            double deltaZ = targetZ - playerZ;
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float yaw = (float)(Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
            float pitch = (float)(-(Math.atan2(deltaY, distance) * 180.0 / Math.PI));
            return new float[]{yaw, pitch};
        }
        catch (Exception ex) {
            return new float[]{0.0f, 0.0f};
        }
    }

    private float[] getLegitRotations(Object entity) {
        try {
            Object player = Entity.getThePlayer();
            double playerX = Mapper.posX.getDouble(player);
            double playerY = Mapper.posY.getDouble(player) + (Double)Mapper.getEyeHeight.invoke(player, new Object[0]);
            double playerZ = Mapper.posZ.getDouble(player);
            double targetX = Mapper.posX.getDouble(entity);
            double entityHeight = (Double)Mapper.getEyeHeight.invoke(entity, new Object[0]);
            double bodyOffset = entityHeight * (0.35 + this.r.nextDouble() * 0.45);
            double targetY = Mapper.posY.getDouble(entity) + bodyOffset;
            double targetZ = Mapper.posZ.getDouble(entity);
            double jitterX = this.r.nextGaussian() * 0.015;
            double jitterZ = this.r.nextGaussian() * 0.015;
            double deltaX = targetX - playerX + jitterX;
            double deltaY = targetY - playerY;
            double deltaZ = targetZ - playerZ + jitterZ;
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float yaw = (float)(Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
            float pitch = (float)(-(Math.atan2(deltaY, distance) * 180.0 / Math.PI));
            return new float[]{yaw, pitch};
        }
        catch (Exception ex) {
            return new float[]{0.0f, 0.0f};
        }
    }

    private float applyGCD(float current, float target) {
        float delta = target - current;
        float gcd = 0.03125f;
        delta -= delta % gcd;
        return current + delta;
    }

    private float smoothRotation(float current, float target, float speed) {
        float delta = ((target - current) % 360.0f + 540.0f) % 360.0f - 180.0f;
        float absDelta = Math.abs(delta);
        float dynamicSpeed;
        if (absDelta > 50.0f) {
            dynamicSpeed = speed * (0.5f + this.r.nextFloat() * 0.2f);
        } else if (absDelta > 15.0f) {
            dynamicSpeed = speed * (0.6f + this.r.nextFloat() * 0.25f);
        } else {
            dynamicSpeed = speed * (0.3f + this.r.nextFloat() * 0.2f);
        }
        float microJitter = (float)(this.r.nextGaussian() * 0.25);
        if (absDelta <= dynamicSpeed) {
            return applyGCD(current, target + microJitter);
        }
        float step = Math.signum(delta) * dynamicSpeed;
        return applyGCD(current, current + step + microJitter);
    }

    private boolean isValidTarget(Object entity) {
        try {
            Object player = Entity.getThePlayer();
            if (entity == null || entity == player) {
                return false;
            }
            try {
                String className = entity.getClass().getSimpleName();
                if (className.contains("ArmorStand") || className.contains("EntityArmorStand")) {
                    return false;
                }
            }
            catch (Exception className) {
            }
            String entityName = Entity.getName(entity);
            if (entityName == null || entityName.isEmpty()) {
                return false;
            }
            if (entityName.startsWith("\u00a7")) {
                return false;
            }
            if (entityName.startsWith("[CR]") || entityName.startsWith("[NPC]")) {
                return false;
            }
            if (!entityName.contains(" ") && entityName.length() < 3) {
                return false;
            }
            float hp = Entity.getEntityHealth(entity).floatValue();
            int id = Entity.getId(entity);
            if (hp <= 0.0f || hp == 1.0f && id != 8) {
                return false;
            }
            float distance = this.dist(entity);
            float maxRange = mode.getMode().equals("Legit") ? (float)legitRange.getNumber() : (float)rng.getNumber();
            if (distance >= maxRange) {
                return false;
            }
            if (advancedAntiBot.isToggled()) {
                if (id < 0 || id > 1000000) {
                    return false;
                }
                try {
                    double posX = Mapper.posX.getDouble(entity);
                    double posY = Mapper.posY.getDouble(entity);
                    double posZ = Mapper.posZ.getDouble(entity);
                    if (posX == Math.floor(posX) && posY == Math.floor(posY) && posZ == Math.floor(posZ)) {
                        return false;
                    }
                }
                catch (Exception exception) {
                }
            }
            return true;
        }
        catch (Exception ex) {
            return false;
        }
    }

    private void performGuideTP(Object target) {
        try {
            Object player = Entity.getThePlayer();
            if (player == null || target == null) {
                return;
            }
            if (!this.isTPing) {
                this.tpStartX = Mapper.posX.getDouble(player);
                this.tpStartY = Mapper.posY.getDouble(player);
                this.tpStartZ = Mapper.posZ.getDouble(player);
                this.isTPing = true;
            }
            double targetX = Mapper.posX.getDouble(target);
            double targetY = Mapper.posY.getDouble(target);
            double targetZ = Mapper.posZ.getDouble(target);
            float targetYaw = Mapper.rotationYaw.getFloat(target);
            double radYaw = Math.toRadians(targetYaw);
            double offsetX = -Math.sin(radYaw) * 0.5;
            double offsetZ = Math.cos(radYaw) * 0.5;
            double tpX = targetX + offsetX;
            double tpY = targetY;
            double tpZ = targetZ + offsetZ;
            Mapper.posX.setDouble(player, tpX);
            Mapper.posY.setDouble(player, tpY);
            Mapper.posZ.setDouble(player, tpZ);
            int id = Entity.getId(target);
            float[] rotations = this.getRotations(target);
            this.serverRotations[0] = rotations[0];
            this.serverRotations[1] = rotations[1];
            Entity.setSwingProgress(player, true);
            Object attackPacket = C02Helper.createAttackPacket(id);
            if (attackPacket != null) {
                Packet.addToSendQueue(attackPacket);
                Entity.setSwingProgress(player, true);
                this.swing();
            }
            if (this.tpTimer.hasTimeElapsed(50L, false)) {
                Mapper.posX.setDouble(player, this.tpStartX);
                Mapper.posY.setDouble(player, this.tpStartY);
                Mapper.posZ.setDouble(player, this.tpStartZ);
                this.isTPing = false;
                this.tpTimer.reset();
            }
        }
        catch (Exception ex) {
            this.isTPing = false;
        }
    }

    @Subscribe
    public void render(RenderEvent e) throws InvocationTargetException, IllegalAccessException {
        if (mode.getMode().equals("Normal") && antiAim.isToggled()) {
            this.applyAntiAim();
        }
        if (tgt == null) {
            targetOpacityAnim.animate(0.0f, 20);
            return;
        }
        if (!this.isValidTarget(tgt)) {
            tgt = null;
            return;
        }
        targetOpacityAnim.animate(255.0f, 20);
        float opacity = targetOpacityAnim.getOutput() / 255.0f;
        try {
            Object player = Entity.getThePlayer();
            double targetX = Mapper.posX.getDouble(tgt);
            double targetY = Mapper.posY.getDouble(tgt) + (Double)Mapper.getEyeHeight.invoke(tgt, new Object[0]) + 0.5;
            double targetZ = Mapper.posZ.getDouble(tgt);
            double renderX = targetX - Mapper.posX.getDouble(player);
            double renderY = targetY - Mapper.posY.getDouble(player) - (Double)Mapper.getEyeHeight.invoke(player, new Object[0]);
            double renderZ = targetZ - Mapper.posZ.getDouble(player);
            float yaw = Mapper.rotationYaw.getFloat(player);
            float pitch = Mapper.rotationPitch.getFloat(player);
            double distance = Math.sqrt(renderX * renderX + renderZ * renderZ);
            if (distance < 0.1) {
                return;
            }
            double angleToTarget = Math.toDegrees(Math.atan2(renderZ, renderX)) - 90.0;
            double angleDiff = (angleToTarget - (double)yaw + 180.0 + 360.0) % 360.0 - 180.0;
            if (Math.abs(angleDiff) > 90.0) {
                return;
            }
            int sw = e.getScreenWidth();
            int sh = e.getScreenHeight();
            int screenX = (int)((double)(sw / 2) + angleDiff * (double)sw / 100.0);
            int n = (int)((double)(sh / 2) - renderY * (double)sh / 10.0 / distance);
        }
        catch (Exception ex) {
            int sw = e.getScreenWidth();
            int n = e.getScreenHeight();
        }
    }

    private void applyAntiAim() {
        try {
            Object player = Entity.getThePlayer();
            if (player == null) {
                return;
            }
            this.realYaw = Mapper.rotationYaw.getFloat(player);
            this.realPitch = Mapper.rotationPitch.getFloat(player);
            float time = (float)(System.currentTimeMillis() % 1500L) / 1500.0f;
            float spinYaw = this.realYaw + time * 360.0f;
            float downPitch = 90.0f;
            this.serverRotations[0] = spinYaw;
            this.serverRotations[1] = downPitch;
        }
        catch (Exception exception) {
        }
    }

    @Subscribe
    public void onTick(TickEvent e) {
        Object player = Entity.getThePlayer();
        if (player == null) {
            return;
        }
        if (mode.getMode().equals("Legit")) {
            this.onTickLegit(player);
        } else {
            this.onTickNormal(player);
        }
    }

    private void onTickLegit(Object player) {
        Object bestTarget = null;
        float bestDist = Float.MAX_VALUE;
        for (Object p : Entity.getPlayerEntitiesInWorld()) {
            float distance;
            if (!this.isValidTarget(p) || !((distance = this.dist(p)) < bestDist)) continue;
            bestDist = distance;
            bestTarget = p;
        }

        if (bestTarget != null) {
            tgt = bestTarget;
            float[] targetRots = this.getLegitRotations(bestTarget);
            float aimSpd = (float)legitAimSpeed.getNumber() * 0.35f;
            float distFactor = Math.min(1.0f, bestDist / 3.0f);
            aimSpd *= (0.7f + distFactor * 0.3f);

            if (!this.smoothInitialized) {
                try {
                    this.smoothYaw = Mapper.rotationYaw.getFloat(player);
                    this.smoothPitch = Mapper.rotationPitch.getFloat(player);
                } catch (Exception ex) {
                    this.smoothYaw = 0.0f;
                    this.smoothPitch = 0.0f;
                }
                this.smoothInitialized = true;
            }
            this.smoothYaw = this.smoothRotation(this.smoothYaw, targetRots[0], aimSpd);
            this.smoothPitch = this.smoothRotation(this.smoothPitch, targetRots[1], aimSpd * 0.55f);
            this.smoothPitch = Math.max(-90.0f, Math.min(90.0f, this.smoothPitch));

            try {
                Mapper.rotationYaw.setFloat(player, this.smoothYaw);
                Mapper.rotationPitch.setFloat(player, this.smoothPitch);
            } catch (Exception ex) {
            }

            float yawDiff = Math.abs(((targetRots[0] - this.smoothYaw) % 360.0f + 540.0f) % 360.0f - 180.0f);
            float pitchDiff = Math.abs(targetRots[1] - this.smoothPitch);

            float aimThresholdYaw = 12.0f + this.r.nextFloat() * 6.0f;
            float aimThresholdPitch = 10.0f + this.r.nextFloat() * 5.0f;

            if (yawDiff < aimThresholdYaw && pitchDiff < aimThresholdPitch) {
                if (this.atkT.hasTimeElapsed(this.delay, false)) {
                    try {
                        int id = Entity.getId(bestTarget);
                        Entity.setSwingProgress(player, true);
                        Object attackPacket = C02Helper.createAttackPacket(id);
                        if (attackPacket != null) {
                            Entity.setSwingProgress(player, true);
                            Packet.addToSendQueue(attackPacket);
                            this.swing();
                        }
                        if (legitSprintReset.isToggled() && this.r.nextInt(100) < 70) {
                            try {
                                Mapper.setSprinting.invoke(player, false);
                            } catch (Exception ex) {
                            }
                        }
                    }
                    catch (Exception ex) {
                    }
                    this.reset();
                    this.atkT.reset();
                }
            }
        } else {
            tgt = null;
            this.smoothInitialized = false;
        }
    }

    private void onTickNormal(Object player) {
        Object bestTarget = null;
        float bestDist = Float.MAX_VALUE;
        for (Object p : Entity.getPlayerEntitiesInWorld()) {
            float distance;
            if (!this.isValidTarget(p) || !((distance = this.dist(p)) < bestDist)) continue;
            bestDist = distance;
            bestTarget = p;
        }
        if (bestTarget != null && this.atkT.hasTimeElapsed(this.delay, false)) {
            try {
                tgt = bestTarget;
                if (targetMode.getMode().equals("Guide TP")) {
                    this.performGuideTP(bestTarget);
                } else {
                    int id = Entity.getId(bestTarget);
                    if (silentAim.isToggled()) {
                        float[] rotations = this.getRotations(bestTarget);
                        this.serverRotations[0] = rotations[0];
                        this.serverRotations[1] = rotations[1];
                    }
                    Entity.setSwingProgress(player, true);
                    Object attackPacket = C02Helper.createAttackPacket(id);
                    if (attackPacket != null) {
                        Entity.setSwingProgress(player, true);
                        Packet.addToSendQueue(attackPacket);
                        this.swing();
                    }
                }
                this.reset();
                this.atkT.reset();
            }
            catch (Exception ex) {
                Mapper.log((Object)("Attack error: " + ex.getMessage()));
            }
        } else if (bestTarget == null) {
            tgt = null;
        }
    }

    public static Object getTarget() {
        return tgt;
    }

    private void swing() {
        try {
            Constructor<?> swingCtor = Mapper.C0APacketAnimation.getDeclaredConstructor(new Class[0]);
            swingCtor.setAccessible(true);
            Object swingPacket = swingCtor.newInstance(new Object[0]);
            Packet.addToSendQueue(swingPacket);
        }
        catch (Exception e) {
            Mapper.log((Object)("KillAura Swing Error: " + e.getMessage()));
        }
    }
}

package wentra.module.impl.misc;

import com.google.common.eventbus.Subscribe;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Random;
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

public class TPAura extends Module {
    private final BooleanSetting faceTarget = new BooleanSetting("FaceTarget", true);
    private final BooleanSetting lockTarget = new BooleanSetting("LockTarget", true);
    private final BooleanSetting forceClient = new BooleanSetting("ForceClientPos", true);
    private final BooleanSetting autoAttack = new BooleanSetting("AutoAttack", true);
    private final ModeSetting swingMode = new ModeSetting("SwingMode", "Normal", "Normal", "Reverse", "Random");
    private final NumberSetting targetRange = new NumberSetting("TargetRange", 15.0f, 1.0f, 50.0f);
    private final NumberSetting behindDist = new NumberSetting("BehindDist", 1.5f, 0.5f, 5.0f);
    private final NumberSetting minCPS = new NumberSetting("MinCPS", 8.0f, 1.0f, 20.0f);
    private final NumberSetting maxCPS = new NumberSetting("MaxCPS", 12.0f, 1.0f, 20.0f);

    private Object lockedTarget = null;
    private final Random rnd = new Random();
    private Constructor<?> C0A;
    private long lastAttackTime = 0;
    private int currentDelay = 100;

    public TPAura() {
        super("TPAura", ModuleCategory.MISC, 21);
        this.settings.add(this.faceTarget);
        this.settings.add(this.lockTarget);
        this.settings.add(this.forceClient);
        this.settings.add(this.autoAttack);
        this.settings.add(this.swingMode);
        this.settings.add(this.targetRange);
        this.settings.add(this.behindDist);
        this.settings.add(this.minCPS);
        this.settings.add(this.maxCPS);
    }

    @Override
    public void onEnable() {
        try {
            if (Mapper.C06PacketPlayerPosLook == null) {
                Mapper.mapC03C05C06();
            }
            if (Mapper.C0APacketAnimation != null) {
                this.C0A = Mapper.C0APacketAnimation.getConstructor();
            }
        } catch (Throwable ignored) {}
        this.lockedTarget = null;
        updateCPSDelay();
    }

    @Override
    public void onDisable() {
        this.lockedTarget = null;
    }

    @Subscribe
    public void onTick(TickEvent e) {
        Object self = Entity.getThePlayer();
        if (self == null) return;

        Object target = this.selectTarget(self);
        if (target == null) return;

        // Calculate position behind target using Minecraft's yaw system
        float targetYaw = Entity.getRotationYaw(target);
        double radYaw = Math.toRadians(targetYaw);
        
        double dist = (double)this.behindDist.getValue();
        double tx = Entity.getPosX(target) + Math.sin(radYaw) * dist;
        double ty = Entity.getPosY(target);
        double tz = Entity.getPosZ(target) - Math.cos(radYaw) * dist;

        float[] rot;
        if (this.faceTarget.isToggled()) {
            rot = this.calculateRotations(tx, ty + Entity.getEyeHeight(self), tz, 
                                          Entity.getPosX(target), 
                                          Entity.getPosY(target) + Entity.getEyeHeight(target) / 2.0, 
                                          Entity.getPosZ(target));
        } else {
            rot = new float[]{Entity.getRotationYaw(self), Entity.getRotationPitch(self)};
        }

        // Send PosLook packet (teleport)
        try {
            Object pkt = Mapper.newC06(tx, ty, tz, rot[0], rot[1], Entity.isOnGround(self));
            if (pkt != null) {
                Packet.addToSendQueue(pkt);
            }
        } catch (Throwable ignored) {}

        // Force client position if enabled
        if (this.forceClient.isToggled()) {
            Entity.setPositionAndRotation(tx, ty, tz, rot[0], rot[1]);
            Entity.resetMotion(true);
        }

        // Auto Attack logic with CPS-based delay
        if (this.autoAttack.isToggled()) {
            if (System.currentTimeMillis() - lastAttackTime >= currentDelay) {
                int targetId = Entity.getId(target);
                if (targetId > 0) {
                    this.performSwing(self);
                    try {
                        Packet.addToSendQueue(new C02Helper(targetId).getPacket());
                    } catch (Throwable ignored) {}
                    
                    this.updateCPSDelay();
                    lastAttackTime = System.currentTimeMillis();
                }
            }
        }
    }

    private void updateCPSDelay() {
        float min = (float) minCPS.getNumber();
        float max = (float) maxCPS.getNumber();
        if (min > max) min = max;
        float cps = min + rnd.nextFloat() * (max - min);
        if (cps <= 0.1f) cps = 1.0f;
        this.currentDelay = (int) (1000.0 / cps);
    }

    private void performSwing(Object self) {
        try {
            String mode = this.swingMode.getValue();
            boolean isReverse = mode.equalsIgnoreCase("Reverse");
            if (mode.equalsIgnoreCase("Random")) {
                isReverse = rnd.nextBoolean();
            }

            if (!isReverse) {
                // Normal Swing (Client + Server)
                Entity.setSwingProgress(self, true);
                if (this.rnd.nextInt(100) < 30 && this.C0A != null) {
                    Packet.addToSendQueue(this.C0A.newInstance());
                }
            } else {
                // Animation only / Server-side swing logic
                if (this.C0A != null) {
                    Packet.addToSendQueue(this.C0A.newInstance());
                }
                if (this.rnd.nextInt(100) < 50) {
                    Entity.setSwingProgress(self, true);
                }
            }
        } catch (Throwable ignored) {}
    }

    private Object selectTarget(Object self) {
        if (this.lockTarget.isToggled() && this.lockedTarget != null && this.isValidTarget(self, this.lockedTarget)) {
            return this.lockedTarget;
        }
        Object nearest = this.getNearestTarget(self);
        if (this.lockTarget.isToggled()) {
            this.lockedTarget = nearest;
        }
        return nearest;
    }

    private boolean isValidTarget(Object self, Object target) {
        if (target == null || target == self) return false;
        
        try {
            // Check health
            float health = Entity.getEntityHealth(target).floatValue();
            if (health <= 0) return false;
            
            // Basic anti-bot checks by name
            String name = Entity.getName(target);
            if (name == null || name.isEmpty() || name.contains("[NPC]") || name.contains(" ")) return false;
        } catch (Exception e) {
            return false;
        }

        // 3D Distance check
        double dx = Entity.getPosX(target) - Entity.getPosX(self);
        double dy = Entity.getPosY(target) - Entity.getPosY(self);
        double dz = Entity.getPosZ(target) - Entity.getPosZ(self);
        double distSq = dx * dx + dy * dy + dz * dz;
        double range = (double) this.targetRange.getNumber();
        
        return distSq <= range * range;
    }

    private Object getNearestTarget(Object self) {
        List<Object> players = Entity.getPlayerEntitiesInWorld();
        if (players == null || players.isEmpty()) return null;

        double selfX = Entity.getPosX(self);
        double selfY = Entity.getPosY(self);
        double selfZ = Entity.getPosZ(self);
        double bestDistSq = Double.MAX_VALUE;
        Object bestTarget = null;

        for (Object player : players) {
            if (!isValidTarget(self, player)) continue;
            
            double dx = Entity.getPosX(player) - selfX;
            double dy = Entity.getPosY(player) - selfY;
            double dz = Entity.getPosZ(player) - selfZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestTarget = player;
            }
        }
        return bestTarget;
    }

    private float[] calculateRotations(double startX, double startY, double startZ, double targetX, double targetY, double targetZ) {
        double diffX = targetX - startX;
        double diffY = targetY - startY;
        double diffZ = targetZ - startZ;
        double distXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, distXZ));
        
        return new float[]{wrapAngleTo180(yaw), wrapAngleTo180(pitch)};
    }

    private float wrapAngleTo180(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }
}


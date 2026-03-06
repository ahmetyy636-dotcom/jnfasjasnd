package wentra.module.impl.combat;

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
import com.google.common.eventbus.Subscribe;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Random;

public class KillAura extends Module {
   public static final NumberSetting max = new NumberSetting("MaxCPS", 15.0F, 2.0F, 20.0F);
   public static final NumberSetting min = new NumberSetting("MinCPS", 12.0F, 1.0F, 20.0F);
   public static final NumberSetting rng = new NumberSetting("Range", 4.0F, 3.0F, 6.0F);
   public static final BooleanSetting silentAim = new BooleanSetting("Silent Aim", true);
   public static final BooleanSetting antiAim = new BooleanSetting("Anti-Aim", false);
   public static final ModeSetting targetMode = new ModeSetting("Target Mode", "Normal", "Normal", "Guide TP");
   public static final BooleanSetting advancedAntiBot = new BooleanSetting("Advanced AntiBot", true);
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
   private float realYaw = 0.0F;
   private float realPitch = 0.0F;
   private boolean isTPing = false;
   private double tpStartX;
   private double tpStartY;
   private double tpStartZ;

   public KillAura() {
      super("KillAura", ModuleCategory.COMBAT, 19);
      this.settings.add(max);
      this.settings.add(min);
      this.settings.add(rng);
      this.settings.add(silentAim);
      this.settings.add(antiAim);
      this.settings.add(targetMode);
      this.settings.add(advancedAntiBot);
   }

   public void onEnable() {
      this.reset();
      this.cpsT.reset();
      this.atkT.reset();
      this.tpTimer.reset();
      tgt = null;
      this.isTPing = false;
      this.serverRotations[0] = 0.0F;
      this.serverRotations[1] = 0.0F;
      super.onEnable();
   }

   public void onDisable() {
      tgt = null;
      this.isTPing = false;

      try {
         Object player = Entity.getThePlayer();
         if (player != null && antiAim.isToggled()) {
            Mapper.rotationYaw.setFloat(player, this.realYaw);
            Mapper.rotationPitch.setFloat(player, this.realPitch);
         }
      } catch (Exception var2) {
      }

      super.onDisable();
   }

   private void reset() {
      int minCPS = (int)min.getNumber();
      int maxCPS = (int)max.getNumber();
      int randomCPS = minCPS + this.r.nextInt(Math.max(1, maxCPS - minCPS + 1));
      this.delay = 1000 / randomCPS;
   }

   private float dist(Object e) {
      try {
         return e == null ? Float.MAX_VALUE : (Float)Mapper.getDistanceToEntity.invoke(Entity.getThePlayer(), e);
      } catch (Exception var3) {
         return Float.MAX_VALUE;
      }
   }

   private float[] getRotations(Object entity) {
      try {
         Object player = Entity.getThePlayer();
         double playerX = Mapper.posX.getDouble(player);
         double playerY = Mapper.posY.getDouble(player) + (Double)Mapper.getEyeHeight.invoke(player);
         double playerZ = Mapper.posZ.getDouble(player);
         double targetX = Mapper.posX.getDouble(entity);
         double targetY = Mapper.posY.getDouble(entity) + (Double)Mapper.getEyeHeight.invoke(entity) * 0.9D;
         double targetZ = Mapper.posZ.getDouble(entity);
         double deltaX = targetX - playerX;
         double deltaY = targetY - playerY;
         double deltaZ = targetZ - playerZ;
         double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
         float yaw = (float)(Math.atan2(deltaZ, deltaX) * 180.0D / 3.141592653589793D) - 90.0F;
         float pitch = (float)(-(Math.atan2(deltaY, distance) * 180.0D / 3.141592653589793D));
         return new float[]{yaw, pitch};
      } catch (Exception var25) {
         return new float[]{0.0F, 0.0F};
      }
   }

   private boolean isValidTarget(Object entity) {
      try {
         Object player = Entity.getThePlayer();
         if (entity != null && entity != player) {
            String entityName;
            try {
               entityName = entity.getClass().getSimpleName();
               if (entityName.contains("ArmorStand") || entityName.contains("EntityArmorStand")) {
                  return false;
               }
            } catch (Exception var14) {
            }

            entityName = Entity.getName(entity);
            if (entityName != null && !entityName.isEmpty()) {
               if (entityName.startsWith("§")) {
                  return false;
               } else if (!entityName.startsWith("[CR]") && !entityName.startsWith("[NPC]")) {
                  if (!entityName.contains(" ") && entityName.length() < 3) {
                     return false;
                  } else {
                     float hp = Entity.getEntityHealth(entity);
                     int id = Entity.getId(entity);
                     if (hp <= 0.0F || hp == 1.0F && id != 8) {
                        return false;
                     } else {
                        float distance = this.dist(entity);
                        if (distance >= (float)rng.getNumber()) {
                           return false;
                        } else {
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
                              } catch (Exception var13) {
                              }
                           }

                           return true;
                        }
                     }
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } catch (Exception var15) {
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
         double radYaw = Math.toRadians((double)targetYaw);
         double offsetX = -Math.sin(radYaw) * 0.5D;
         double offsetZ = Math.cos(radYaw) * 0.5D;
         double tpX = targetX + offsetX;
         double tpZ = targetZ + offsetZ;
         Mapper.posX.setDouble(player, tpX);
         Mapper.posY.setDouble(player, targetY);
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
      } catch (Exception var25) {
         this.isTPing = false;
      }

   }

   @Subscribe
   public void render(RenderEvent e) throws InvocationTargetException, IllegalAccessException {
      if (antiAim.isToggled()) {
         this.applyAntiAim();
      }

      if (tgt == null) {
         targetOpacityAnim.animate(0.0F, 20);
      } else if (!this.isValidTarget(tgt)) {
         tgt = null;
      } else {
         targetOpacityAnim.animate(255.0F, 20);
         float var2 = targetOpacityAnim.getOutput() / 255.0F;

         try {
            Object player = Entity.getThePlayer();
            double targetX = Mapper.posX.getDouble(tgt);
            double targetY = Mapper.posY.getDouble(tgt) + (Double)Mapper.getEyeHeight.invoke(tgt) + 0.5D;
            double targetZ = Mapper.posZ.getDouble(tgt);
            double renderX = targetX - Mapper.posX.getDouble(player);
            double renderY = targetY - Mapper.posY.getDouble(player) - (Double)Mapper.getEyeHeight.invoke(player);
            double renderZ = targetZ - Mapper.posZ.getDouble(player);
            float yaw = Mapper.rotationYaw.getFloat(player);
            float pitch = Mapper.rotationPitch.getFloat(player);
            double distance = Math.sqrt(renderX * renderX + renderZ * renderZ);
            if (distance < 0.1D) {
               return;
            }

            double angleToTarget = Math.toDegrees(Math.atan2(renderZ, renderX)) - 90.0D;
            double angleDiff = (angleToTarget - (double)yaw + 180.0D + 360.0D) % 360.0D - 180.0D;
            if (Math.abs(angleDiff) > 90.0D) {
               return;
            }

            int sw = e.getScreenWidth();
            int sh = e.getScreenHeight();
            int screenX = (int)((double)(sw / 2) + angleDiff * (double)sw / 100.0D);
            int var27 = (int)((double)(sh / 2) - renderY * (double)sh / 10.0D / distance);
         } catch (Exception var28) {
            int sw = e.getScreenWidth();
            int var5 = e.getScreenHeight();
         }

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
         float time = (float)(System.currentTimeMillis() % 1500L) / 1500.0F;
         float spinYaw = this.realYaw + time * 360.0F;
         float downPitch = 90.0F;
         this.serverRotations[0] = spinYaw;
         this.serverRotations[1] = downPitch;
      } catch (Exception var5) {
      }

   }

   @Subscribe
   public void onTick(TickEvent e) {
      Object player = Entity.getThePlayer();
      if (player != null) {
         Object bestTarget = null;
         float bestDist = Float.MAX_VALUE;
         Iterator var5 = Entity.getPlayerEntitiesInWorld().iterator();

         Object attackPacket;
         while(var5.hasNext()) {
            attackPacket = var5.next();
            if (this.isValidTarget(attackPacket)) {
               float distance = this.dist(attackPacket);
               if (distance < bestDist) {
                  bestDist = distance;
                  bestTarget = attackPacket;
               }
            }
         }

         if (bestTarget != null && this.atkT.hasTimeElapsed((long)this.delay, false)) {
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
                  attackPacket = C02Helper.createAttackPacket(id);
                  if (attackPacket != null) {
                     Entity.setSwingProgress(player, true);
                     Packet.addToSendQueue(attackPacket);
                     this.swing();
                  }
               }

               this.reset();
               this.atkT.reset();
            } catch (Exception var8) {
               Mapper.log((Object)("Attack error: " + var8.getMessage()));
            }
         } else if (bestTarget == null) {
            tgt = null;
         }

      }
   }

   public static Object getTarget() {
      return tgt;
   }

   private void swing() {
      try {
         Constructor swingCtor = Mapper.C0APacketAnimation.getDeclaredConstructor();
         swingCtor.setAccessible(true);
         Object swingPacket = swingCtor.newInstance();
         Packet.addToSendQueue(swingPacket);
      } catch (Exception var3) {
         Mapper.log((Object)("KillAura Swing Error: " + var3.getMessage()));
      }

   }
}

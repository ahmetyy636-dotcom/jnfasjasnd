package wentra.module.impl.exploit;

import com.google.common.eventbus.Subscribe;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import wentra.Main;
import wentra.event.impl.PacketReceiveEvent;
import wentra.event.impl.PacketSendEvent;
import wentra.event.impl.TickEvent;
import wentra.module.Module;
import wentra.module.setting.BooleanSetting;
import wentra.module.setting.ModeSetting;
import wentra.module.setting.ModuleCategory;
import wentra.module.setting.NumberSetting;
import wentra.utils.mapper.Entity;
import wentra.utils.mapper.Mapper;
import wentra.utils.time.TimerUtil;

public class Disabler
extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "Ping Abuser", "Ping Abuser", "Blink", "Watchdog", "Old Matrix", "Ghostly", "RAC", "Verus", "Vulcan", "Matrix", "Grim");
    public NumberSetting abuseVL = new NumberSetting("Abuse VL", 100.0f, 100.0f, 1000.0f);
    public BooleanSetting c0fAbuse = new BooleanSetting("C0F Abuse", true);
    public BooleanSetting c00Abuse = new BooleanSetting("C00 Abuse", true);
    public BooleanSetting blockS08 = new BooleanSetting("Block S08 (Geri İtme)", true);
    private final List<Object> packetAbuseList = new CopyOnWriteArrayList<Object>();
    private final Deque<Object> packetsWD = new ArrayDeque<Object>();
    private final ArrayList<Object> packets = new ArrayList<Object>();
    private final TimerUtil timer1 = new TimerUtil();
    private final TimerUtil timer2 = new TimerUtil();
    private final TimerUtil abuseTimer = new TimerUtil();
    private final TimerUtil timer = new TimerUtil();
    private boolean cancel = false;
    private int tickCounter = 0;
    private int c03Counter = 0;
    private int grimVL = 0;

    public Disabler() {
        super("Disabler", ModuleCategory.MISC, 0);
        this.settings.add(this.mode);
        this.settings.add(this.abuseVL);
        this.settings.add(this.c0fAbuse);
        this.settings.add(this.c00Abuse);
        this.settings.add(this.blockS08);
    }

    @Override
    public void onEnable() {
        this.packetAbuseList.clear();
        this.packetsWD.clear();
        this.packets.clear();
        this.cancel = false;
        this.tickCounter = 0;
        this.c03Counter = 0;
        this.grimVL = 0;
        this.timer1.reset();
        this.timer2.reset();
        this.abuseTimer.reset();
        Mapper.log((Object)("[Disabler] Enabled - Mode: " + this.mode.getValue()));
        Entity.addChatMessage("§a[Disabler] §7" + this.mode.getValue() + " aktif", false);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.releaseAllPackets();
        this.packetAbuseList.clear();
        this.packetsWD.clear();
        this.packets.clear();
        this.cancel = false;
        Mapper.log((Object)"[Disabler] Disabled");
        Entity.addChatMessage("§c[Disabler] §7Kapatıldı", false);
        super.onDisable();
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (Entity.getThePlayer() == null) {
            return;
        }
        ++this.tickCounter;
        String modeValue = this.mode.getValue();
        if ("Watchdog".equals(modeValue) && this.timer1.hasTimeElapsed(10000L, true)) {
            this.cancel = true;
            this.timer2.reset();
        }
        if ("Ping Abuser".equals(modeValue) && this.abuseTimer.hasTimeElapsed(this.abuseVL.getNumber(), false)) {
            if (!this.packetAbuseList.isEmpty()) {
                for (Object packet : new ArrayList<Object>(this.packetAbuseList)) {
                    this.sendPacketNoEvent(packet);
                    this.packetAbuseList.remove(packet);
                }
            }
            this.abuseTimer.reset();
        }
        if ("Ghostly".equals(modeValue) && this.tickCounter % 15 == 0 && !this.packetAbuseList.isEmpty()) {
            for (Object packet : new ArrayList<Object>(this.packetAbuseList)) {
                this.sendPacketNoEvent(packet);
                this.packetAbuseList.remove(packet);
            }
        }
        if (("Verus".equals(modeValue) || "Matrix".equals(modeValue) || "Vulcan".equals(modeValue) || "Grim".equals(modeValue))) {
            if (!this.packetAbuseList.isEmpty() && this.tickCounter % 4 == 0) {
                Object packet = this.packetAbuseList.get(0);
                this.sendPacketNoEvent(packet);
                this.packetAbuseList.remove(0);
            }
        }
    }

    @Subscribe
    public void onPacketReceive(PacketReceiveEvent e) {
        if (e.getPacket() == null) {
            return;
        }
        String packetName = e.getPacket().getClass().getSimpleName();
        String modeValue = this.mode.getValue();
        if ("RAC".equals(modeValue)) {
            this.handleRACReceive(e, packetName);
        } else if ("Grim".equals(modeValue)) {
            if (packetName.contains("S08")) {
                this.grimVL++;
                if (this.grimVL > 3) {
                    Mapper.log("[Disabler] Grim: Detected excessive setbacks");
                }
            }
        }

        if (this.blockS08.getValue()) {
            boolean isSetback = packetName.equals("řš") || packetName.contains("S08") || packetName.contains("SetPlayerPosition") || packetName.contains("PlayerPosLook");
            if (!isSetback) {
                try {
                    Field[] fields = e.getPacket().getClass().getDeclaredFields();
                    int doubleCount = 0;
                    int floatCount = 0;
                    for (Field field : fields) {
                        if (field.getType() == Double.TYPE) {
                            ++doubleCount;
                        }
                        if (field.getType() != Float.TYPE) continue;
                        ++floatCount;
                    }
                    if (doubleCount >= 3 && floatCount >= 2) {
                        isSetback = true;
                    }
                }
                catch (Exception exception) {
                    Mapper.log((Object)("[Disabler] Field check error: " + exception.getMessage()));
                }
            }
            if (isSetback) {
                e.cancel();
                if (this.tickCounter % 100 == 0) {
                    Entity.addChatMessage("§e[Disabler] §7Setback engelleniyor", false);
                }
            }
        }
    }

    private void handleRACReceive(PacketReceiveEvent e, String packetName) {
        try {
            Object packet = e.getPacket();
            if (packetName.contains("cV")) {
                this.c03Counter = -15;
            }
            if (packetName.contains("jS") || packetName.contains("Transaction")) {
                try {
                    int ticksExisted = Entity.getTicksExisted();
                    for (Field field : packet.getClass().getDeclaredFields()) {
                        if (field.getType() != Short.TYPE && field.getType() != Short.class) continue;
                        field.setAccessible(true);
                        if (ticksExisted % 2 == 0) {
                            field.setShort(packet, (short)Short.MIN_VALUE);
                        } else {
                            field.setShort(packet, (short)Short.MAX_VALUE);
                        }
                        break;
                    }
                    this.sendPacketNoEvent(packet);
                    e.cancel();
                }
                catch (Exception ex) {
                }
            }
            if (packetName.contains("C03") || packetName.contains("C04") || packetName.contains("a") || packetName.contains("c")) {
                ++this.c03Counter;
                if (this.c03Counter >= 6) {
                    this.sendDiggingPacket(false);
                    this.c03Counter = 0;
                } else if (this.c03Counter == 4) {
                    this.sendDiggingPacket(true);
                }
            }
        }
        catch (Exception ex) {
        }
    }

    private void sendDiggingPacket(boolean start) {
        try {
            Class<?> c07Class = null;
            for (Class<?> clazz : Main.jclasses) {
                String name = clazz.getSimpleName();
                if (!name.contains("C07") && !name.contains("PlayerDigging") && !name.contains("kh")) continue;
                c07Class = clazz;
                break;
            }
            if (c07Class == null) {
                return;
            }
        }
        catch (Exception ex) {
        }
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent e) {
        if (e.getPacket() == null) {
            return;
        }
        String modeValue = this.mode.getValue();
        Object packet = e.getPacket();
        String packetName = packet.getClass().getSimpleName();
        try {
            switch (modeValue) {
                case "Ping Abuser": {
                    this.handlePingAbuser(e, packet, packetName);
                    break;
                }
                case "Blink": {
                    this.handleBlink(e, packet, packetName);
                    break;
                }
                case "Watchdog": {
                    this.handleWatchdog(e, packet, packetName);
                    break;
                }
                case "Old Matrix": {
                    this.handleOldMatrix(e, packet, packetName);
                    break;
                }
                case "Ghostly": {
                    this.handleGhostly(e, packet, packetName);
                    break;
                }
                case "Verus": {
                    this.handleVerus(e, packet, packetName);
                    break;
                }
                case "Vulcan": {
                    this.handleVulcan(e, packet, packetName);
                    break;
                }
                case "Matrix": {
                    this.handleMatrix(e, packet, packetName);
                    break;
                }
                case "Grim": {
                    this.handleGrim(e, packet, packetName);
                    break;
                }
            }
        }
        catch (Exception ex) {
        }
    }

    private void handlePingAbuser(PacketSendEvent e, Object packet, String packetName) {
        if (this.c0fAbuse.getValue() && packetName.contains("C0F")) {
            try {
                Field windowIdField = packet.getClass().getDeclaredField("windowId");
                windowIdField.setAccessible(true);
                int windowId = windowIdField.getInt(packet);
                if (windowId > 100) {
                    this.packetAbuseList.add(packet);
                    e.cancel();
                }
            }
            catch (Exception exception) {}
        }
        if (this.c00Abuse.getValue() && packetName.contains("C00")) {
            this.packetAbuseList.add(packet);
            e.cancel();
        }
    }

    private void handleBlink(PacketSendEvent e, Object packet, String packetName) {
        if ((packetName.contains("C03") || packetName.contains("C04") || packetName.contains("C05") || packetName.contains("C06")) && this.tickCounter % 3 == 0) {
            e.cancel();
        }
    }

    private void handleWatchdog(PacketSendEvent e, Object packet, String packetName) {
        if (this.tickCounter < 50 && (packetName.contains("C03") || packetName.contains("C04") || packetName.contains("C05") || packetName.contains("C06"))) {
            e.cancel();
            return;
        }
        if (packetName.contains("C03") && this.cancel) {
            if (!this.timer2.hasTimeElapsed(400L, false)) {
                e.cancel();
                this.packets.add(packet);
            } else {
                for (Object p : new ArrayList<Object>(this.packets)) {
                    this.sendPacketNoEvent(p);
                }
                this.packets.clear();
                this.cancel = false;
            }
        }
    }

    private void handleOldMatrix(PacketSendEvent e, Object packet, String packetName) {
        if (packetName.contains("C03")) {
            e.cancel();
        }
    }

    private void handleGhostly(PacketSendEvent e, Object packet, String packetName) {
        if (packetName.contains("C0F")) {
            try {
                Field windowIdField = packet.getClass().getDeclaredField("windowId");
                windowIdField.setAccessible(true);
                int windowId = windowIdField.getInt(packet);
                if (windowId > 100) {
                    this.packetAbuseList.add(packet);
                    e.cancel();
                }
            }
            catch (Exception exception) {
            }
        }
        if (packetName.contains("C00")) {
            this.packetAbuseList.add(packet);
            e.cancel();
        }
        if ((packetName.contains("C03") || packetName.contains("C04")) && this.tickCounter % 10 == 0) {
            e.cancel();
        }
    }

    private void handleVerus(PacketSendEvent e, Object packet, String packetName) {
        if (packetName.contains("C0F") || packetName.contains("C00")) {
            this.packetAbuseList.add(packet);
            e.cancel();
        } else if (packetName.contains("C0B")) {
            e.cancel();
        }
        if (packetName.contains("C03") && this.tickCounter % 15 == 0) {
            this.packetAbuseList.add(packet);
            e.cancel();
        }
    }

    private void handleVulcan(PacketSendEvent e, Object packet, String packetName) {
        if (packetName.contains("C0F") || packetName.contains("C00")) {
            this.packetAbuseList.add(packet);
            e.cancel();
        }
        if (packetName.contains("C0B")) {
            e.cancel();
        }
    }

    private void handleMatrix(PacketSendEvent e, Object packet, String packetName) {
        if (packetName.contains("C03") || packetName.contains("C04") || packetName.contains("C05") || packetName.contains("C06")) {
            if (this.tickCounter % 5 == 0) {
                e.cancel();
            }
        }
        if (packetName.contains("C00")) {
            this.packetAbuseList.add(packet);
            e.cancel();
        }
    }

    private void handleGrim(PacketSendEvent e, Object packet, String packetName) {
        if (packetName.contains("C0F") || packetName.contains("C00")) {
            this.packetAbuseList.add(packet);
            e.cancel();
        }
        if (packetName.contains("C0B")) {
            e.cancel();
        }
    }

    private void sendPacketNoEvent(Object packet) {
        try {
            Object netHandler;
            Object player;
            if (Mapper.sendPacket != null && Mapper.sendQueue != null && (player = Entity.getThePlayer()) != null && (netHandler = Mapper.sendQueue.get(player)) != null) {
                Mapper.sendPacket.invoke(netHandler, packet);
            }
        }
        catch (Exception ex) {
        }
    }

    private void releaseAllPackets() {
        try {
            for (Object packet : new ArrayList<Object>(this.packetAbuseList)) {
                this.sendPacketNoEvent(packet);
            }
            for (Object packet : new ArrayList<Object>(this.packetsWD)) {
                this.sendPacketNoEvent(packet);
            }
            for (Object packet : new ArrayList<Object>(this.packets)) {
                this.sendPacketNoEvent(packet);
            }
            this.packetAbuseList.clear();
        }
        catch (Exception ex) {
        }
    }
}

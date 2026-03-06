package wentra.module.impl.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import wentra.module.Module;
import wentra.module.setting.ModuleCategory;
import wentra.utils.mapper.Entity;
import wentra.utils.mapper.Mapper;

public class AntiBot
extends Module {
    private static final Set<Object> detectedBots = Collections.newSetFromMap(new WeakHashMap());
    private static final Map<Object, Long> recentSpawns = new WeakHashMap<Object, Long>();
    private static final Map<Object, double[]> lastPositions = new WeakHashMap<Object, double[]>();
    private static final Map<Object, Integer> staticTicks = new WeakHashMap<Object, Integer>();
    private static final Pattern BOT_NAME_PATTERNS = Pattern.compile(".*(\\[?CR\\]?|Bot|NPC|Fake|TestBot|_Bot_\\d+|\\d{8,}|Player\\d+|[A-Z]{1,3}\\d{2,4}).*", 2);
    private static final Pattern INVALID_NAME = Pattern.compile("^.{0,2}$|[^a-zA-Z0-9_].*|.*[^a-zA-Z0-9_].*");
    private static final Pattern UUID_LIKE = Pattern.compile("^[0-9a-fA-F\\-]{32,36}$");
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9]{3,16}$");

    public AntiBot() {
        super("AntiBot", ModuleCategory.WORLD, 0);
        this.toggle();
    }

    @Override
    public void onEnable() {
        this.clearAll();
    }

    @Override
    public void onDisable() {
        this.clearAll();
    }

    private void clearAll() {
        detectedBots.clear();
        recentSpawns.clear();
        lastPositions.clear();
        staticTicks.clear();
    }

    public static boolean isBot(Object entity) {
        try {
            if (entity == null || entity == Entity.getThePlayer()) {
                return false;
            }
            String id = String.valueOf(Entity.getId(entity));
            if (id.length() == 8) {
                return true;
            }
            String name = AntiBot.safe(() -> Entity.getName(entity), "");
            if (name == null || !VALID_USERNAME.matcher(name).matches()) {
                return true;
            }
            if (INVALID_NAME.matcher(name).matches()) {
                return true;
            }
            if (name.length() > 20 || BOT_NAME_PATTERNS.matcher(name).matches()) {
                return true;
            }
            if (UUID_LIKE.matcher(name).matches()) {
                return true;
            }
            if (AntiBot.safe(() -> Entity.getLocationSkin(entity), null) == null) {
                return true;
            }
            float hp = AntiBot.safe(() -> Entity.getEntityHealth(entity), Float.valueOf(20.0f)).floatValue();
            if (hp <= 0.0f || hp > 40.0f) {
                return true;
            }
            long now = System.currentTimeMillis();
            if (!recentSpawns.containsKey(entity)) {
                recentSpawns.put(entity, now);
            } else if (now - recentSpawns.get(entity) < 750L) {
                return true;
            }
            double x = AntiBot.safe(() -> Entity.getPosX(entity), 0.0);
            double z = AntiBot.safe(() -> Entity.getPosZ(entity), 0.0);
            double[] last = lastPositions.get(entity);
            if (last == null) {
                lastPositions.put(entity, new double[]{x, z});
                staticTicks.put(entity, 0);
            } else if (Math.abs(last[0] - x) < 0.01 && Math.abs(last[1] - z) < 0.01) {
                int ticks = staticTicks.getOrDefault(entity, 0) + 1;
                staticTicks.put(entity, ticks);
                if (ticks > 40) {
                    return true;
                }
            } else {
                lastPositions.put(entity, new double[]{x, z});
                staticTicks.put(entity, 0);
            }
            String cls = entity.getClass().getSimpleName().toLowerCase();
            return cls.contains("npc") || cls.contains("bot") || cls.contains("fake") || cls.contains("crentity");
        }
        catch (Exception e) {
            return true;
        }
    }

    public static void removeEntity(Object entity) {
        if (entity == null) {
            return;
        }
        try {
            Mapper.invokeWorldRemove(Entity.getTheWorld(), entity, 5L);
            Mapper.log((Object)("[AntiBot] Removed: " + AntiBot.safe(() -> Entity.getName(entity), "?")));
        }
        catch (Throwable t) {
            Mapper.log((Object)("[-] RemoveEntity failed: " + t.getMessage()));
        }
    }

    public static List<Object> getRealPlayers() {
        ArrayList<Object> real = new ArrayList<Object>();
        detectedBots.clear();
        for (Object e : AntiBot.safe(Entity::getPlayerEntitiesInWorld, Collections.emptyList())) {
            if (!AntiBot.isBot(e)) {
                real.add(e);
                continue;
            }
            detectedBots.add(e);
            AntiBot.removeEntity(e);
        }
        return real;
    }

    public static void tickCleanup() {
        long now = System.currentTimeMillis();
        recentSpawns.entrySet().removeIf(e -> now - (Long)e.getValue() > 10000L);
        lastPositions.keySet().removeIf(e -> !recentSpawns.containsKey(e));
        staticTicks.keySet().removeIf(e -> !recentSpawns.containsKey(e));
    }

    private static <T> T safe(Supplier<T> s, T def) {
        try {
            return s.get();
        }
        catch (Throwable t) {
            return def;
        }
    }
}

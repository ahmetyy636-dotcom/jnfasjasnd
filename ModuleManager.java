package wentra.module;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.lwjgl.input.Keyboard;
import wentra.utils.mapper.Entity;

public class ModuleManager {
    private static final ModuleManager moduleManager = new ModuleManager();
    public static final List<Module> MODULES = new ArrayList<Module>();

    public static void init() {
        String[] packages = {"wentra.module.impl", "matrix.module"};
        for (String pkg : packages) {
            try {
                List<Class<?>> classes = getClasses(pkg);
                for (Class<?> clazz : classes) {
                    if (Module.class.isAssignableFrom(clazz)
                            && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        try {
                            Module module = (Module) clazz.getDeclaredConstructor().newInstance();
                            addMod(module);
                            System.out.println("[ModuleManager] Loaded: " + module.getName());
                        } catch (Exception e) {
                            System.err.println("[ModuleManager] Failed to load: " + clazz.getName());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[ModuleManager] Scan failed for " + pkg + ": " + e.getMessage());
            }
        }
        System.out.println("[ModuleManager] Total modules: " + MODULES.size());
    }

    private static List<Class<?>> getClasses(String packageName) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ModuleManager.class.getClassLoader();
        }
        Enumeration<URL> resources = classLoader.getResources(path);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();
            if ("file".equals(protocol)) {
                findClassesInDirectory(new File(resource.toURI()), packageName, classes);
            } else if ("jar".equals(protocol)) {
                String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
                findClassesInJar(new JarFile(jarPath), path, classes);
            }
        }
        return classes;
    }

    private static void findClassesInDirectory(File directory, String packageName, List<Class<?>> classes) {
        if (!directory.exists()) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                findClassesInDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                try {
                    classes.add(Class.forName(className));
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
    }

    private static void findClassesInJar(JarFile jar, String path, List<Class<?>> classes) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(path) && name.endsWith(".class") && !name.contains("$")) {
                String className = name.replace('/', '.').substring(0, name.length() - 6);
                try {
                    classes.add(Class.forName(className));
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
    }

    public static void addMod(Module kys) {
        MODULES.add(kys);
    }

    public static List<Module> enabled() {
        return MODULES.stream().filter(Module::isToggled).collect(Collectors.toList());
    }

    public static List<Module> listAllModules() {
        return MODULES;
    }

    public static boolean isEnabled(String name) {
        return MODULES.stream().filter(Module::isToggled).anyMatch(it -> it.name.equalsIgnoreCase(name));
    }

    public static ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static void checkForKeyBind() {
        for (Module module : MODULES) {
            boolean isKeyDown = Keyboard.isKeyDown((int)module.key);
            if (Keyboard.isKeyDown((int)51) || Keyboard.isKeyDown((int)52)) {
                return;
            }
            if (!Entity.getCurrentScreen().equals("null")) continue;
            if (isKeyDown && !module.keyIsPressed) {
                module.toggle();
                module.keyIsPressed = true;
                continue;
            }
            if (isKeyDown) continue;
            module.keyIsPressed = false;
        }
    }

    public static boolean getAllModules() {
        return false;
    }
}

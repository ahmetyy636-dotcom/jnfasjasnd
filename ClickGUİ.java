package matrix.module;

import matrix.ui.ClickGui;
import org.lwjgl.input.Mouse;
import wentra.module.Module;
import wentra.module.setting.ModuleCategory;

public class ClickGUI
extends Module {
    private static ClickGui clickGui;

    public ClickGUI() {
        super("ClickGUI", ModuleCategory.RENDER, 54);
    }

    @Override
    public void toggle() {
        if (clickGui == null) {
            clickGui = new ClickGui();
        }
        if (clickGui.isOpen()) {
            clickGui.close();
            Mouse.setGrabbed((boolean)true);
        } else {
            clickGui.open();
            Mouse.setGrabbed((boolean)false);
        }
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    public static ClickGui getClickGui() {
        if (clickGui == null) {
            clickGui = new ClickGui();
        }
        return clickGui;
    }

    public static void renderClickGui(int mouseX, int mouseY, float partialTicks) {
        if (clickGui != null && clickGui.isOpen()) {
            try {
                clickGui.drawScreen(mouseX, mouseY, partialTicks);
            }
            catch (Exception e) {
                System.err.println("Error rendering ClickGUI: " + e.getMessage());
            }
        }
    }

    public static void handleMouseClick(int mouseX, int mouseY, int mouseButton) {
        if (clickGui != null && clickGui.isOpen()) {
            try {
                clickGui.mouseClicked(mouseX, mouseY, mouseButton);
            }
            catch (Exception e) {
                System.err.println("Error handling mouse click: " + e.getMessage());
            }
        }
    }

    public static void handleMouseRelease(int mouseX, int mouseY, int state) {
        if (clickGui != null && clickGui.isOpen()) {
            try {
                clickGui.mouseReleased(mouseX, mouseY, state);
            }
            catch (Exception e) {
                System.err.println("Error handling mouse release: " + e.getMessage());
            }
        }
    }

    public static void handleKeyTyped(char typedChar, int keyCode) {
        if (clickGui != null && clickGui.isOpen()) {
            if (keyCode == 1) {
                clickGui.close();
                Mouse.setGrabbed((boolean)true);
            } else {
                try {
                    clickGui.keyTyped(typedChar, keyCode);
                }
                catch (Exception e) {
                    System.err.println("Error handling key typed: " + e.getMessage());
                }
            }
        }
    }

    public static boolean isGuiOpen() {
        return clickGui != null && clickGui.isOpen();
    }
}

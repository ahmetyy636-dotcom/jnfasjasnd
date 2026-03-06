package matrix.ui;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import matrix.ui.components.CategoryPanel;
import wentra.module.Module;
import wentra.module.ModuleManager;
import wentra.module.setting.ModuleCategory;
import wentra.utils.render.RenderUtil;
import wentra.utils.render.glutils.ScaledResolution;

public class ClickGui {
    private final List<CategoryPanel> panels = new ArrayList<CategoryPanel>();
    private boolean isOpen = false;
    private int mouseX;
    private int mouseY;
    private boolean mouseClicked = false;
    private boolean rightClicked = false;

    public ClickGui() {
        int x = 20;
        int y = 60;
        for (ModuleCategory category : ModuleCategory.values()) {
            ArrayList<Module> modulesInCategory = new ArrayList<Module>();
            for (Module module : ModuleManager.listAllModules()) {
                if (module.cat != category) continue;
                modulesInCategory.add(module);
            }
            if (modulesInCategory.isEmpty()) continue;
            CategoryPanel panel = new CategoryPanel(category, modulesInCategory, x, y);
            this.panels.add(panel);
            if ((x += 120) <= 500) continue;
            x = 20;
            y += 300;
        }
    }

    public static int getRainbow(long offset, float speed, float saturation, float brightness) {
        float hue = (float)(System.nanoTime() + offset) / 1.0E10f * speed % 1.0f;
        return Color.getHSBColor(hue, saturation, brightness).getRGB();
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!this.isOpen) {
            return;
        }
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        ScaledResolution sr = new ScaledResolution();
        
        // Flat Box Background Theme
        RenderUtil.drawRect(0.0, 0.0, sr.getScaledWidth(), sr.getScaledHeight(), new Color(10, 10, 10, 200).getRGB());
        
        // RGB TitanWare Logo
        wentra.utils.render.font.FontUtil.drawString("TITANWARE", 20, 20, getRainbow(0L, 0.5f, 1.0f, 1.0f), true);
        wentra.utils.render.font.FontUtil.drawString("v1.0", 85, 20, new Color(200, 200, 200).getRGB(), true);

        for (CategoryPanel panel : this.panels) {
            panel.render(mouseX, mouseY);
        }
        this.mouseClicked = false;
        this.rightClicked = false;
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!this.isOpen) {
            return;
        }
        for (CategoryPanel panel : this.panels) {
            panel.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    public void mouseReleased(int mx, int my, int state) {
        if (!this.isOpen) {
            return;
        }
        for (CategoryPanel panel : this.panels) {
            panel.mouseReleased(mx, my, state);
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (!this.isOpen) {
            return;
        }
        for (CategoryPanel panel : this.panels) {
            panel.keyTyped(typedChar, keyCode);
        }
    }

    public void onGuiClosed() {
        this.isOpen = false;
    }

    public boolean isOpen() {
        return this.isOpen;
    }

    public void toggle() {
        this.isOpen = !this.isOpen;
    }

    public void open() {
        this.isOpen = true;
    }

    public void close() {
        this.isOpen = false;
    }
}

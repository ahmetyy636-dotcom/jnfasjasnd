package matrix.ui.components;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import matrix.ui.components.ModuleButton;
import org.lwjgl.input.Mouse;
import wentra.module.Module;
import wentra.module.setting.ModuleCategory;
import wentra.utils.mapper.transformers.etc.impl.helpers.FontRenderer;
import wentra.utils.render.RenderUtil;

public class CategoryPanel {
    private final ModuleCategory category;
    private final List<ModuleButton> moduleButtons = new ArrayList<ModuleButton>();
    private int x;
    private int y;
    private int dragX;
    private int dragY;
    private final int width = 125;
    private final int headerHeight = 22;
    private int height = 22;
    private boolean dragging = false;
    private boolean expanded = true;
    private static final Color PURPLE = new Color(190, 140, 255);
    private static final Color BLUE = new Color(120, 180, 255);
    private static final Color BG_OVERLAY = new Color(120, 80, 255, 40);
    private static final Color BG_GRADIENT_START = new Color(22, 22, 28, 240);
    private static final Color BG_GRADIENT_END = new Color(30, 30, 40, 240);

    public CategoryPanel(ModuleCategory category, List<Module> modules, int x, int y) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.updateButtonList(modules);
    }

    private void updateButtonList(List<Module> modules) {
        this.moduleButtons.clear();
        int off = 22;
        for (Module m : modules) {
            this.moduleButtons.add(new ModuleButton(m, this.x + 6, this.y + off, 113));
        }
    }

    private void updateLayout() {
        this.height = 22;
        if (this.expanded) {
            int off = 22;
            for (ModuleButton b : this.moduleButtons) {
                b.setPosition(this.x + 6, this.y + off);
                int buttonHeight = b.getTotalHeight();
                this.height += buttonHeight;
                off += buttonHeight;
            }
        }
    }

    public void render(int mx, int my) {
        if (this.dragging) {
            if (Mouse.isButtonDown((int)0)) {
                this.x = mx - this.dragX;
                this.y = my - this.dragY;
            } else {
                this.dragging = false;
            }
        }
        this.updateLayout();
        // Flat Body
        RenderUtil.drawRect(this.x, this.y + 22, 125.0, this.height - 22, new Color(15, 15, 15, 230).getRGB());
        // Flat Header
        RenderUtil.drawRect(this.x, this.y, 125.0, 22.0, new Color(25, 25, 25, 255).getRGB());
        // Outline
        RenderUtil.drawRect(this.x, this.y, 125.0, 1.0, new Color(50, 50, 50).getRGB());
        
        String title = this.category.name().substring(0, 1).toUpperCase() + this.category.name().substring(1).toLowerCase();
        FontRenderer.drawText(title, this.x + 8, this.y + 6, new Color(220, 220, 220).getRGB());
        if (this.expanded) {
            this.moduleButtons.forEach(b -> b.render(mx, my));
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        boolean isOverHeader;
        boolean bl = isOverHeader = mx >= this.x && mx <= this.x + 125 && my >= this.y && my <= this.y + 22;
        if (isOverHeader) {
            if (button == 0) {
                this.dragging = true;
                this.dragX = mx - this.x;
                this.dragY = my - this.y;
                return true;
            }
            if (button == 1) {
                this.expanded = !this.expanded;
                return true;
            }
        }
        if (this.expanded && mx >= this.x && mx <= this.x + 125 && my >= this.y + 22 && my <= this.y + this.height) {
            this.moduleButtons.forEach(b -> b.mouseClicked(mx, my, button));
            return true;
        }
        return false;
    }

    public void mouseReleased(int mx, int my, int state) {
        this.dragging = false;
        if (this.expanded) {
            this.moduleButtons.forEach(b -> b.mouseReleased(mx, my, state));
        }
    }

    public void keyTyped(char c, int k) {
        if (this.expanded) {
            this.moduleButtons.forEach(b -> b.keyTyped(c, k));
        }
    }
}

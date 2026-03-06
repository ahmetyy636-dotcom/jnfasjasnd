package matrix.ui.components;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import matrix.ui.components.settings.BooleanSettingComponent;
import matrix.ui.components.settings.DoubleSettingComponent;
import matrix.ui.components.settings.ModeSettingComponent;
import matrix.ui.components.settings.NumberSettingComponent;
import matrix.ui.components.settings.SettingComponent;
import wentra.module.Module;
import wentra.module.setting.BooleanSetting;
import wentra.module.setting.DoubleSetting;
import wentra.module.setting.ModeSetting;
import wentra.module.setting.NumberSetting;
import wentra.module.setting.Setting;
import wentra.utils.mapper.transformers.etc.impl.helpers.FontRenderer;
import wentra.utils.render.RenderUtil;

public class ModuleButton {
    private final Module modul;
    private int x;
    private int y;
    private int width;
    private final int height = 20;
    private boolean expanded = false;
    private boolean binding = false;
    private float anim = 0.0f;
    private final List<SettingComponent> settings = new ArrayList<SettingComponent>();
    private static final Color PURPLE = new Color(190, 140, 255);
    private static final Color BLUE = new Color(120, 180, 255);

    public ModuleButton(Module m, int x, int y, int w) {
        this.modul = m;
        this.x = x;
        this.y = y;
        this.width = w;
        for (Setting s : m.settings) {
            SettingComponent c = this.create(s);
            if (c == null) continue;
            this.settings.add(c);
        }
    }

    private SettingComponent create(Setting s) {
        if (s instanceof BooleanSetting) {
            return new BooleanSettingComponent((BooleanSetting)s, this.x, this.y, this.width);
        }
        if (s instanceof ModeSetting) {
            return new ModeSettingComponent((ModeSetting)s, this.x, this.y, this.width);
        }
        if (s instanceof NumberSetting) {
            return new NumberSettingComponent((NumberSetting)s, this.x, this.y, this.width);
        }
        if (s instanceof DoubleSetting) {
            return new DoubleSettingComponent((DoubleSetting)s, this.x, this.y, this.width);
        }
        return null;
    }

    public void render(int mx, int my) {
        boolean hover = mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + 20;
        boolean on = this.modul.isToggled();
        this.anim += (float)(this.expanded ? 1 : -1) * 0.2f;
        this.anim = Math.max(0.0f, Math.min(1.0f, this.anim));

        if (on) {
            RenderUtil.drawRect(this.x, this.y, this.width, 20.0, new Color(40, 120, 200, 255).getRGB());
        } else {
            RenderUtil.drawRect(this.x, this.y, this.width, 20.0, new Color(20, 20, 20, 255).getRGB());
        }
        
        if (hover) {
            RenderUtil.drawRect(this.x, this.y, this.width, 20.0, new Color(255, 255, 255, 20).getRGB());
        }
        
        FontRenderer.drawText(this.modul.getName(), this.x + 8, this.y + 6, on ? -1 : new Color(180, 180, 180).getRGB());
        if (!this.settings.isEmpty()) {
            FontRenderer.drawText(this.expanded ? "\u02c5" : ">", this.x + this.width - 12, this.y + 6, new Color(150, 150, 150).getRGB());
        }
        
        if (this.anim > 0.0f) {
            int off = 20;
            for (SettingComponent s : this.settings) {
                s.setPosition(this.x, this.y + off);
                s.render(mx, my);
                off = (int)((float)off + (float)s.getHeight() * this.anim);
            }
        }
    }

    public void mouseClicked(int mx, int my, int b) {
        if (mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + 20) {
            if (b == 0) {
                this.modul.toggle();
            } else if (b == 1) {
                this.expanded = !this.expanded;
            }
            return;
        }
        if (this.expanded) {
            this.settings.forEach(s -> s.mouseClicked(mx, my, b));
        }
    }

    public void mouseReleased(int mx, int my, int s) {
        if (this.expanded) {
            this.settings.forEach(st -> st.mouseReleased(mx, my, s));
        }
    }

    public void keyTyped(char c, int k) {
        if (!this.binding) {
            return;
        }
        if (k == 1) {
            this.binding = false;
            return;
        }
        this.modul.key = k;
        this.binding = false;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getHeight() {
        return 20;
    }

    public int getTotalHeight() {
        int h = 20;
        if (this.expanded) {
            for (SettingComponent s : this.settings) {
                h += s.getHeight();
            }
        }
        return h;
    }
}

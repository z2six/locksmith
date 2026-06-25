// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksEditorScreen.java
package org.z2six.locksmith.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.z2six.locksmith.network.ReloadLockableBlocksEditorPayload;
import org.z2six.locksmith.network.SaveLockableBlocksPayload;
import org.z2six.locksmith.render.profile.LockTargetType;
import org.z2six.locksmith.render.profile.LockTransform;
import org.z2six.locksmith.render.profile.LockableBlockEntry;
import org.z2six.locksmith.render.profile.LockableBlockValidationResult;
import org.z2six.locksmith.render.profile.LockableBlockValidationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LockableBlocksEditorScreen extends Screen {
    private static final int BG = 0xF0101216;
    private static final int PANEL = 0xF01A1D24;
    private static final int PANEL_2 = 0xF0222630;
    private static final int LINE = 0xFF3A414D;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF9EA7B3;
    private static final int ACCENT = 0xFF67C7D4;
    private static final int ERROR = 0xFFE05B5B;
    private static final int OK = 0xFF68C274;

    private static LockableBlocksEditorScreen ACTIVE;

    private final LockableBlocksEditorState state;
    private final ArrayList<Rect> buttons = new ArrayList<>();
    private Focus focus = Focus.NONE;
    private boolean rotatingPreview = false;
    private boolean panningPreview = false;
    private int blockIdTextIndex = -1;
    private String blockIdText = "";
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private float yaw = 35.0F;
    private float pitch = 25.0F;
    private float zoom = 1.0F;
    private float panX = 0.0F;
    private float panY = 0.0F;
    private boolean multiblockPreview = false;
    private boolean showHelp = false;

    public LockableBlocksEditorScreen(List<LockableBlockEntry> entries, List<LockableBlockValidationResult> validation) {
        super(Component.literal("Locksmith Lockable Blocks"));
        this.state = new LockableBlocksEditorState(entries, validation);
        ACTIVE = this;
    }

    public static void applySaveResult(boolean success, String message, List<LockableBlockValidationResult> validation) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof LockableBlocksEditorScreen screen) {
            screen.state.applySaveResult(success, message, validation);
        } else if (ACTIVE != null) {
            ACTIVE.state.applySaveResult(success, message, validation);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (ACTIVE == this) ACTIVE = null;
        super.removed();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx, mouseX, mouseY, partialTick);
        buttons.clear();

        // Draw the entire custom editor after the vanilla background/blur pass
        // and at high Z, so menu blur never overlays the editor controls.
        gfx.pose().pushPose();
        gfx.pose().translate(0.0F, 0.0F, 1000.0F);
        gfx.fill(0, 0, this.width, this.height, BG);

        int leftW = 230;
        int rightW = 280;
        int bottomH = 44;
        int centerX = leftW;
        int centerW = this.width - leftW - rightW;
        int centerH = this.height - bottomH;
        previewX = centerX + 12;
        previewY = 42;
        previewW = Math.max(80, centerW - 24);
        previewH = Math.max(80, centerH - 54);

        drawLeftPanel(gfx, mouseX, mouseY, leftW, centerH);
        drawCenterPanel(gfx, mouseX, mouseY, centerX, centerW, centerH);
        drawRightPanel(gfx, mouseX, mouseY, this.width - rightW, rightW, centerH);
        drawBottomBar(gfx, mouseX, mouseY, leftW, centerW, rightW, bottomH);
        if (showHelp) {
            drawHelpOverlay(gfx, mouseX, mouseY);
        }
        gfx.pose().popPose();
    }

    private void drawLeftPanel(GuiGraphics gfx, int mouseX, int mouseY, int w, int h) {
        gfx.fill(0, 0, w, h, PANEL);
        gfx.fill(w - 1, 0, w, h, LINE);
        gfx.drawString(this.font, "Lockable Blocks", 12, 12, TEXT, false);
        drawTextField(gfx, 12, 31, w - 24, 20, state.filterText, "Filter", focus == Focus.FILTER);

        int y = 60;
        List<Integer> visible = state.visibleIndices();
        for (int visibleRow = 0; visibleRow < visible.size(); visibleRow++) {
            int index = visible.get(visibleRow);
            if (y + 24 > h - 74) break;
            LockableBlockEntry entry = state.workingEntries().get(index);
            LockableBlockValidationResult result = index < state.validationResults().size() ? state.validationResults().get(index) : null;
            boolean selected = index == state.selectedIndex;
            int rowColor = selected ? 0xFF2A4650 : (visibleRow % 2 == 0 ? 0x00111111 : 0x22111111);
            gfx.fill(8, y, w - 8, y + 22, rowColor);
            int statusColor = statusColor(result);
            gfx.fill(14, y + 6, 20, y + 16, statusColor);
            String id = entry == null || entry.blockId() == null ? "<invalid block id>" : entry.blockId().toString();
            gfx.drawString(this.font, trim(id, 28), 26, y + 7, selected ? 0xFFFFFFFF : TEXT, false);
            addButton("select:" + index, 8, y, w - 16, 22);
            y += 24;
        }

        int by = h - 64;
        drawButton(gfx, "Add", "add", 12, by, 62, 22, mouseX, mouseY);
        drawButton(gfx, "Dup", "dup", 82, by, 62, 22, mouseX, mouseY);
        drawButton(gfx, "Remove", "remove", 152, by, 66, 22, mouseX, mouseY);
        drawButton(gfx, "Reset", "reset", 12, by + 28, 98, 22, mouseX, mouseY);
        drawButton(gfx, "Discard", "discard", 120, by + 28, 98, 22, mouseX, mouseY);
    }

    private void drawCenterPanel(GuiGraphics gfx, int mouseX, int mouseY, int x, int w, int h) {
        gfx.fill(x, 0, x + w, h, 0xF013151A);
        gfx.drawString(this.font, "Preview", x + 12, 14, TEXT, false);
        gfx.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF0B0D11);
        gfx.fill(previewX, previewY, previewX + previewW, previewY + 1, LINE);
        gfx.fill(previewX, previewY + previewH - 1, previewX + previewW, previewY + previewH, LINE);
        gfx.fill(previewX, previewY, previewX + 1, previewY + previewH, LINE);
        gfx.fill(previewX + previewW - 1, previewY, previewX + previewW, previewY + previewH, LINE);

        LockableBlocksPreviewRenderer.render(gfx, previewX, previewY, previewW, previewH, state.selectedEntry(), yaw, pitch, zoom, panX, panY, multiblockPreview);
        gfx.drawString(this.font, "LMB rotate   MMB move   SCROLL zoom", previewX + 10, previewY + previewH - 18, MUTED, false);
        drawButton(gfx, "Reset View", "resetView", previewX + previewW - 88, previewY + previewH - 26, 76, 18, mouseX, mouseY);
        LockableBlockEntry selected = state.selectedEntry();
        if (selected != null && selected.type() == LockTargetType.CHEST) {
            drawButton(gfx, multiblockPreview ? "Double Chest" : "Single Chest", "toggleMultiblock", previewX + 10, previewY + 10, 104, 20, mouseX, mouseY);
        }
    }

    private void drawRightPanel(GuiGraphics gfx, int mouseX, int mouseY, int x, int w, int h) {
        gfx.fill(x, 0, x + w, h, PANEL);
        gfx.fill(x, 0, x + 1, h, LINE);
        gfx.drawString(this.font, "Entry", x + 14, 12, TEXT, false);

        LockableBlockEntry entry = state.selectedEntry();
        if (entry == null) {
            gfx.drawString(this.font, "No entry selected", x + 14, 38, MUTED, false);
            return;
        }
        ensureBlockIdDraft();

        int y = 34;
        gfx.drawString(this.font, "Block", x + 14, y, MUTED, false);
        drawTextField(gfx, x + 14, y + 12, w - 28, 20, blockIdText, "minecraft:chest", focus == Focus.BLOCK_ID);
        y += 42;

        drawButton(gfx, "Type: " + entry.type().name().toLowerCase(Locale.ROOT), "cycleType", x + 14, y, 118, 22, mouseX, mouseY);
        drawButton(gfx, "?", "help", x + 138, y, 22, 22, mouseX, mouseY);
        drawButton(gfx, entry.enabled() ? "Enabled" : "Disabled", "toggleEnabled", x + 168, y, 86, 22, mouseX, mouseY);
        y += 34;

        LockTransform t = entry.transform();
        y = drawNudge(gfx, mouseX, mouseY, x, y, "offsetX", t.offsetX(), 0.025D);
        y = drawNudge(gfx, mouseX, mouseY, x, y, "offsetY", t.offsetY(), 0.025D);
        y = drawNudge(gfx, mouseX, mouseY, x, y, "offsetZ", t.offsetZ(), 0.025D);
        y += 6;
        y = drawNudge(gfx, mouseX, mouseY, x, y, "rotX", t.rotX(), 5.0D);
        y = drawNudge(gfx, mouseX, mouseY, x, y, "rotY", t.rotY(), 5.0D);
        y = drawNudge(gfx, mouseX, mouseY, x, y, "rotZ", t.rotZ(), 5.0D);
        y += 6;
        y = drawNudge(gfx, mouseX, mouseY, x, y, "scale", t.scale(), 0.05D);
        if (entry.type() == LockTargetType.CHEST) {
            y = drawNudge(gfx, mouseX, mouseY, x, y, "doubleNudgeX", t.doubleNudgeX(), 0.025D);
        } else if (entry.type() == LockTargetType.DOOR) {
            y = drawNudge(gfx, mouseX, mouseY, x, y, "hingeNudgeLeft", t.hingeNudgeLeft(), 0.025D);
            y = drawNudge(gfx, mouseX, mouseY, x, y, "hingeNudgeRight", t.hingeNudgeRight(), 0.025D);
        }

        drawValidationBox(gfx, x, w, h, y + 12, state.selectedValidation());
    }

    private void drawValidationBox(GuiGraphics gfx, int panelX, int panelW, int panelH, int preferredY, LockableBlockValidationResult result) {
        int innerW = panelW - 44;
        List<String> lines = wrap(result == null ? "" : result.message(), Math.max(10, innerW / 6));
        int boxH = Math.max(50, 42 + lines.size() * 11);
        int boxY = Math.max(34, Math.min(panelH - boxH - 12, preferredY));

        gfx.fill(panelX + 14, boxY, panelX + panelW - 14, boxY + boxH, PANEL_2);
        gfx.drawString(this.font, result == null ? "UNKNOWN" : result.status().name(), panelX + 22, boxY + 10, statusColor(result), false);

        int lineY = boxY + 29;
        for (String line : lines) {
            gfx.drawString(this.font, line, panelX + 22, lineY, MUTED, false);
            lineY += 11;
        }
    }

    private int drawNudge(GuiGraphics gfx, int mouseX, int mouseY, int panelX, int y, String field, double value, double step) {
        int x = panelX + 14;
        gfx.drawString(this.font, field, x, y + 6, MUTED, false);
        gfx.drawString(this.font, String.format(Locale.ROOT, "%.3f", value), x + 116, y + 6, TEXT, false);
        drawButton(gfx, "-", "nudge:" + field + ":" + (-step), x + 198, y, 24, 20, mouseX, mouseY);
        drawButton(gfx, "+", "nudge:" + field + ":" + step, x + 226, y, 24, 20, mouseX, mouseY);
        return y + 24;
    }

    private void drawBottomBar(GuiGraphics gfx, int mouseX, int mouseY, int leftW, int centerW, int rightW, int h) {
        int y = this.height - h;
        gfx.fill(0, y, this.width, this.height, PANEL_2);
        gfx.fill(0, y, this.width, y + 1, LINE);
        drawButton(gfx, "Save", "save", this.width - 268, y + 11, 58, 22, mouseX, mouseY);
        drawButton(gfx, "Reload", "reload", this.width - 202, y + 11, 66, 22, mouseX, mouseY);
        drawButton(gfx, "Close", "close", this.width - 128, y + 11, 58, 22, mouseX, mouseY);
        String dirty = state.dirty ? "Unsaved changes" : "No unsaved changes";
        gfx.drawString(this.font, dirty, 14, y + 16, state.dirty ? ACCENT : MUTED, false);
        gfx.drawString(this.font, trim(state.statusMessage, 78), 128, y + 16, MUTED, false);
    }

    private void drawHelpOverlay(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.fill(0, 0, this.width, this.height, 0xAA000000);

        int w = Math.min(520, this.width - 48);
        int h = 246;
        int x = (this.width - w) / 2;
        int y = Math.max(24, (this.height - h) / 2);

        gfx.fill(x, y, x + w, y + h, 0xFA171B22);
        gfx.fill(x, y, x + w, y + 1, LINE);
        gfx.fill(x, y + h - 1, x + w, y + h, LINE);
        gfx.fill(x, y, x + 1, y + h, LINE);
        gfx.fill(x + w - 1, y, x + w, y + h, LINE);

        gfx.drawString(this.font, "Lock Target Types", x + 18, y + 16, TEXT, false);
        drawButton(gfx, "Close", "helpClose", x + w - 74, y + 12, 56, 20, mouseX, mouseY);

        int yy = y + 48;
        yy = drawHelpSection(gfx, x + 18, yy, "Door", "Two-block DoorBlock targets. Uses the lower block as the saved position, renders the full door preview, and applies facing plus hinge-side nudges.");
        yy = drawHelpSection(gfx, x + 18, yy + 10, "Chest", "ChestBlock targets. Uses chest-facing placement and can preview single or double chests. Double chests use a shared normalized lock position.");
        drawHelpSection(gfx, x + 18, yy + 10, "Other", "Single interactable block targets. No door hinge, no double chest, no multiblock logic. The lock belongs to exactly the clicked block position.");
    }

    private int drawHelpSection(GuiGraphics gfx, int x, int y, String title, String body) {
        gfx.drawString(this.font, title, x, y, ACCENT, false);
        int yy = y + 13;
        for (String line : wrap(body, 74)) {
            gfx.drawString(this.font, line, x, yy, MUTED, false);
            yy += 11;
        }
        return yy;
    }

    private void drawTextField(GuiGraphics gfx, int x, int y, int w, int h, String value, String placeholder, boolean active) {
        gfx.fill(x, y, x + w, y + h, 0xFF0D1015);
        gfx.fill(x, y, x + w, y + 1, active ? ACCENT : LINE);
        gfx.fill(x, y + h - 1, x + w, y + h, active ? ACCENT : LINE);
        gfx.fill(x, y, x + 1, y + h, active ? ACCENT : LINE);
        gfx.fill(x + w - 1, y, x + w, y + h, active ? ACCENT : LINE);
        String text = value == null || value.isEmpty() ? placeholder : value;
        gfx.drawString(this.font, trim(text, Math.max(6, w / 6)), x + 6, y + 6, value == null || value.isEmpty() ? 0xFF68717D : TEXT, false);
        addButton(active ? "fieldActive" : "field:" + (placeholder.equals("Filter") ? "filter" : "block"), x, y, w, h);
    }

    private void drawButton(GuiGraphics gfx, String label, String action, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hover = contains(x, y, w, h, mouseX, mouseY);
        gfx.fill(x, y, x + w, y + h, hover ? 0xFF33404C : 0xFF252B34);
        gfx.fill(x, y, x + w, y + 1, LINE);
        gfx.fill(x, y + h - 1, x + w, y + h, LINE);
        gfx.fill(x, y, x + 1, y + h, LINE);
        gfx.fill(x + w - 1, y, x + w, y + h, LINE);
        gfx.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, hover ? 0xFFFFFFFF : TEXT);
        addButton(action, x, y, w, h);
    }

    private void addButton(String action, int x, int y, int w, int h) {
        buttons.add(new Rect(action, x, y, w, h));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showHelp) {
            for (int i = buttons.size() - 1; i >= 0; i--) {
                Rect rect = buttons.get(i);
                if (rect.contains(mouseX, mouseY) && rect.action.equals("helpClose")) {
                    showHelp = false;
                    return true;
                }
            }
            return true;
        }

        for (Rect rect : buttons) {
            if (rect.contains(mouseX, mouseY)) {
                handleAction(rect.action);
                return true;
            }
        }

        if ((button == 0 || button == 2) && contains(previewX, previewY, previewW, previewH, (int) mouseX, (int) mouseY)) {
            rotatingPreview = button == 0;
            panningPreview = button == 2;
            focus = Focus.NONE;
            return true;
        }
        focus = Focus.NONE;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (rotatingPreview && button == 0) {
            yaw += (float) dragX * 0.6F;
            pitch += (float) dragY * 0.6F;
            if (pitch < -80.0F) pitch = -80.0F;
            if (pitch > 80.0F) pitch = 80.0F;
            return true;
        }
        if (panningPreview && button == 2) {
            float panSpeed = 0.01F / Math.max(0.25F, zoom);
            panX += (float) dragX * panSpeed;
            panY -= (float) dragY * panSpeed;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        rotatingPreview = false;
        panningPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (contains(previewX, previewY, previewW, previewH, (int) mouseX, (int) mouseY)) {
            zoom += (float) scrollY * 0.08F;
            if (zoom < 0.45F) zoom = 0.45F;
            if (zoom > 2.5F) zoom = 2.5F;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focus == Focus.FILTER) {
            state.filterText += codePoint;
            return true;
        }
        if (focus == Focus.BLOCK_ID) {
            ensureBlockIdDraft();
            blockIdText += codePoint;
            state.updateSelectedBlockId(blockIdText);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (showHelp) {
                showHelp = false;
                return true;
            }
            onClose();
            return true;
        }
        if (keyCode == 259) {
            if (focus == Focus.FILTER && !state.filterText.isEmpty()) {
                state.filterText = state.filterText.substring(0, state.filterText.length() - 1);
                return true;
            }
            if (focus == Focus.BLOCK_ID) {
                ensureBlockIdDraft();
                if (!blockIdText.isEmpty()) {
                    blockIdText = blockIdText.substring(0, blockIdText.length() - 1);
                    state.updateSelectedBlockId(blockIdText);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void handleAction(String action) {
        if (action == null || action.equals("fieldActive")) return;
        if (action.startsWith("select:")) {
            state.select(Integer.parseInt(action.substring("select:".length())));
            resetBlockIdDraft();
            focus = Focus.NONE;
            return;
        }
        if (action.equals("field:filter")) {
            focus = Focus.FILTER;
            return;
        }
        if (action.equals("field:block")) {
            ensureBlockIdDraft();
            focus = Focus.BLOCK_ID;
            return;
        }
        if (action.startsWith("nudge:")) {
            String[] parts = action.split(":");
            if (parts.length == 3) {
                double delta = Double.parseDouble(parts[2]);
                if (hasShiftDown()) {
                    delta *= 0.2D;
                }
                state.nudgeTransform(parts[1], delta);
            }
            return;
        }
        switch (action) {
            case "add" -> state.addEntry();
            case "dup" -> state.duplicateSelected();
            case "remove" -> state.removeSelected();
            case "reset" -> state.resetSelectedTransform();
            case "discard" -> state.discard();
            case "cycleType" -> state.cycleSelectedType();
            case "help" -> showHelp = true;
            case "helpClose" -> showHelp = false;
            case "toggleEnabled" -> state.toggleSelectedEnabled();
            case "resetView" -> {
                yaw = 35.0F;
                pitch = 25.0F;
                zoom = 1.0F;
                panX = 0.0F;
                panY = 0.0F;
            }
            case "toggleMultiblock" -> multiblockPreview = !multiblockPreview;
            case "save" -> {
                state.rebuildLocalValidation();
                PacketDistributor.sendToServer(new SaveLockableBlocksPayload(state.workingEntries()));
                state.statusMessage = "Saving to server...";
            }
            case "reload" -> {
                PacketDistributor.sendToServer(new ReloadLockableBlocksEditorPayload());
                state.statusMessage = "Reloading from server...";
            }
            case "close" -> Minecraft.getInstance().setScreen(null);
            default -> {
            }
        }
        if (action.equals("add") || action.equals("dup") || action.equals("remove")) {
            resetBlockIdDraft();
        }
    }

    private int statusColor(LockableBlockValidationResult result) {
        if (result == null) return MUTED;
        if (result.status() == LockableBlockValidationStatus.OK) return OK;
        if (result.status() == LockableBlockValidationStatus.DISABLED) return MUTED;
        return ERROR;
    }

    private void ensureBlockIdDraft() {
        if (blockIdTextIndex == state.selectedIndex) {
            return;
        }
        resetBlockIdDraft();
    }

    private void resetBlockIdDraft() {
        blockIdTextIndex = state.selectedIndex;
        LockableBlockEntry selected = state.selectedEntry();
        blockIdText = selected == null || selected.blockId() == null ? "" : selected.blockId().toString();
    }

    private static boolean contains(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static String trim(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 1)) + "...";
    }

    private static List<String> wrap(String text, int max) {
        ArrayList<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > max) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private record Rect(String action, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private enum Focus {
        NONE,
        FILTER,
        BLOCK_ID
    }
}

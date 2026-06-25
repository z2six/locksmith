// MainFile: neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksEditorState.java
package org.z2six.locksmith.client.screen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import org.z2six.locksmith.render.profile.LockTargetType;
import org.z2six.locksmith.render.profile.LockTransform;
import org.z2six.locksmith.render.profile.LockableBlockDefaults;
import org.z2six.locksmith.render.profile.LockableBlockEntry;
import org.z2six.locksmith.render.profile.LockableBlockValidationResult;
import org.z2six.locksmith.render.profile.LockableBlockValidationStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public final class LockableBlocksEditorState {
    private final ArrayList<LockableBlockEntry> serverEntries = new ArrayList<>();
    private final ArrayList<LockableBlockEntry> workingEntries = new ArrayList<>();
    private final ArrayList<LockableBlockValidationResult> validationResults = new ArrayList<>();

    int selectedIndex = 0;
    String filterText = "";
    boolean dirty = false;
    String statusMessage = "";

    public LockableBlocksEditorState(List<LockableBlockEntry> entries, List<LockableBlockValidationResult> validation) {
        replaceFromServer(entries, validation);
    }

    public List<LockableBlockEntry> workingEntries() {
        return new ArrayList<>(workingEntries);
    }

    public List<LockableBlockValidationResult> validationResults() {
        return new ArrayList<>(validationResults);
    }

    public LockableBlockEntry selectedEntry() {
        if (selectedIndex < 0 || selectedIndex >= workingEntries.size()) return null;
        return workingEntries.get(selectedIndex);
    }

    public LockableBlockValidationResult selectedValidation() {
        if (selectedIndex < 0 || selectedIndex >= validationResults.size()) return null;
        return validationResults.get(selectedIndex);
    }

    public List<Integer> visibleIndices() {
        ArrayList<Integer> out = new ArrayList<>();
        String filter = filterText == null ? "" : filterText.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < workingEntries.size(); i++) {
            LockableBlockEntry entry = workingEntries.get(i);
            String id = entry == null || entry.blockId() == null ? "<invalid>" : entry.blockId().toString();
            if (filter.isEmpty() || id.toLowerCase(Locale.ROOT).contains(filter)) {
                out.add(i);
            }
        }
        return out;
    }

    public void select(int index) {
        if (index >= 0 && index < workingEntries.size()) {
            selectedIndex = index;
        }
    }

    public void replaceFromServer(List<LockableBlockEntry> entries, List<LockableBlockValidationResult> validation) {
        serverEntries.clear();
        workingEntries.clear();
        if (entries != null) {
            serverEntries.addAll(entries);
            workingEntries.addAll(entries);
        }
        if (workingEntries.isEmpty()) {
            workingEntries.addAll(LockableBlockDefaults.create());
            serverEntries.addAll(workingEntries);
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, workingEntries.size() - 1));
        dirty = false;
        statusMessage = "Loaded " + workingEntries.size() + " entries from server.";
        replaceValidation(validation);
        if (validationResults.size() != workingEntries.size()) {
            rebuildLocalValidation();
        }
    }

    public void applySaveResult(boolean success, String message, List<LockableBlockValidationResult> validation) {
        statusMessage = message == null ? "" : message;
        replaceValidation(validation);
        if (success) {
            serverEntries.clear();
            serverEntries.addAll(workingEntries);
            dirty = false;
        }
        if (validationResults.size() != workingEntries.size()) {
            rebuildLocalValidation();
        }
    }

    public void discard() {
        workingEntries.clear();
        workingEntries.addAll(serverEntries);
        selectedIndex = Math.max(0, Math.min(selectedIndex, workingEntries.size() - 1));
        dirty = false;
        statusMessage = "Discarded unsaved changes.";
        rebuildLocalValidation();
    }

    public void addEntry() {
        workingEntries.add(new LockableBlockEntry(null, LockTargetType.GENERIC, LockTransform.genericDefault(), false, true));
        selectedIndex = workingEntries.size() - 1;
        dirty = true;
        statusMessage = "Added new lockable block entry.";
        rebuildLocalValidation();
    }

    public void duplicateSelected() {
        LockableBlockEntry selected = selectedEntry();
        if (selected == null) return;
        workingEntries.add(new LockableBlockEntry(selected.blockId(), selected.type(), selected.transform(), false, selected.enabled()));
        selectedIndex = workingEntries.size() - 1;
        dirty = true;
        statusMessage = "Duplicated selected entry.";
        rebuildLocalValidation();
    }

    public void removeSelected() {
        if (selectedIndex < 0 || selectedIndex >= workingEntries.size()) return;
        workingEntries.remove(selectedIndex);
        selectedIndex = Math.max(0, Math.min(selectedIndex, workingEntries.size() - 1));
        dirty = true;
        statusMessage = "Removed selected entry.";
        rebuildLocalValidation();
    }

    public void resetSelectedTransform() {
        LockableBlockEntry selected = selectedEntry();
        if (selected == null) return;
        LockTransform transform = switch (selected.type()) {
            case CHEST -> LockTransform.chestDefault();
            case GENERIC -> LockTransform.genericDefault();
            case DOOR -> LockTransform.doorDefault();
        };
        updateSelected(selected.withTransform(transform));
        statusMessage = "Reset selected transform.";
    }

    public void updateSelectedBlockId(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text == null ? "" : text.trim());
        LockableBlockEntry selected = selectedEntry();
        if (selected == null) return;
        updateSelected(selected.withBlockId(id));
    }

    public void cycleSelectedType() {
        LockableBlockEntry selected = selectedEntry();
        if (selected == null) return;
        LockTargetType next = switch (selected.type()) {
            case DOOR -> LockTargetType.CHEST;
            case CHEST -> LockTargetType.GENERIC;
            case GENERIC -> LockTargetType.DOOR;
        };
        updateSelected(selected.withType(next));
    }

    public void toggleSelectedEnabled() {
        LockableBlockEntry selected = selectedEntry();
        if (selected == null) return;
        updateSelected(selected.withEnabled(!selected.enabled()));
    }

    public void nudgeTransform(String field, double delta) {
        LockableBlockEntry selected = selectedEntry();
        if (selected == null) return;
        LockTransform t = selected.transform();
        LockTransform next = switch (field) {
            case "offsetX" -> new LockTransform(t.offsetX() + delta, t.offsetY(), t.offsetZ(), t.rotX(), t.rotY(), t.rotZ(), t.scale(), t.hingeNudgeLeft(), t.hingeNudgeRight(), t.doubleNudgeX());
            case "offsetY" -> new LockTransform(t.offsetX(), t.offsetY() + delta, t.offsetZ(), t.rotX(), t.rotY(), t.rotZ(), t.scale(), t.hingeNudgeLeft(), t.hingeNudgeRight(), t.doubleNudgeX());
            case "offsetZ" -> new LockTransform(t.offsetX(), t.offsetY(), t.offsetZ() + delta, t.rotX(), t.rotY(), t.rotZ(), t.scale(), t.hingeNudgeLeft(), t.hingeNudgeRight(), t.doubleNudgeX());
            case "rotX" -> new LockTransform(t.offsetX(), t.offsetY(), t.offsetZ(), (float) (t.rotX() + delta), t.rotY(), t.rotZ(), t.scale(), t.hingeNudgeLeft(), t.hingeNudgeRight(), t.doubleNudgeX());
            case "rotY" -> new LockTransform(t.offsetX(), t.offsetY(), t.offsetZ(), t.rotX(), (float) (t.rotY() + delta), t.rotZ(), t.scale(), t.hingeNudgeLeft(), t.hingeNudgeRight(), t.doubleNudgeX());
            case "rotZ" -> new LockTransform(t.offsetX(), t.offsetY(), t.offsetZ(), t.rotX(), t.rotY(), (float) (t.rotZ() + delta), t.scale(), t.hingeNudgeLeft(), t.hingeNudgeRight(), t.doubleNudgeX());
            case "scale" -> new LockTransform(t.offsetX(), t.offsetY(), t.offsetZ(), t.rotX(), t.rotY(), t.rotZ(), (float) (t.scale() + delta), t.hingeNudgeLeft(), t.hingeNudgeRight(), t.doubleNudgeX());
            case "hingeNudgeLeft" -> new LockTransform(t.offsetX(), t.offsetY(), t.offsetZ(), t.rotX(), t.rotY(), t.rotZ(), t.scale(), t.hingeNudgeLeft() + delta, t.hingeNudgeRight(), t.doubleNudgeX());
            case "hingeNudgeRight" -> new LockTransform(t.offsetX(), t.offsetY(), t.offsetZ(), t.rotX(), t.rotY(), t.rotZ(), t.scale(), t.hingeNudgeLeft(), t.hingeNudgeRight() + delta, t.doubleNudgeX());
            case "doubleNudgeX" -> new LockTransform(t.offsetX(), t.offsetY(), t.offsetZ(), t.rotX(), t.rotY(), t.rotZ(), t.scale(), t.hingeNudgeLeft(), t.hingeNudgeRight(), t.doubleNudgeX() + delta);
            default -> t;
        };
        updateSelected(selected.withTransform(next.clamped()));
    }

    public boolean hasBlockingErrors() {
        rebuildLocalValidation();
        for (LockableBlockValidationResult result : validationResults) {
            if (result != null && !result.okForSave()) return true;
        }
        return false;
    }

    private void updateSelected(LockableBlockEntry entry) {
        if (selectedIndex < 0 || selectedIndex >= workingEntries.size()) return;
        workingEntries.set(selectedIndex, entry);
        dirty = true;
        rebuildLocalValidation();
    }

    private void replaceValidation(List<LockableBlockValidationResult> validation) {
        validationResults.clear();
        if (validation != null) {
            validationResults.addAll(validation);
        }
    }

    void rebuildLocalValidation() {
        validationResults.clear();
        HashMap<ResourceLocation, Integer> counts = new HashMap<>();
        for (LockableBlockEntry entry : workingEntries) {
            if (entry != null && entry.enabled() && entry.blockId() != null) {
                counts.merge(entry.blockId(), 1, Integer::sum);
            }
        }
        for (LockableBlockEntry entry : workingEntries) {
            validationResults.add(validateLocal(entry, counts));
        }
    }

    private LockableBlockValidationResult validateLocal(LockableBlockEntry entry, HashMap<ResourceLocation, Integer> counts) {
        if (entry == null || entry.blockId() == null) {
            return new LockableBlockValidationResult(null, LockableBlockValidationStatus.INVALID_ID, "Block id is missing or invalid.");
        }
        if (!entry.enabled()) {
            return new LockableBlockValidationResult(entry.blockId(), LockableBlockValidationStatus.DISABLED, "Entry is disabled.");
        }
        if (counts.getOrDefault(entry.blockId(), 0) > 1) {
            return new LockableBlockValidationResult(entry.blockId(), LockableBlockValidationStatus.DUPLICATE_BLOCK, "Duplicate enabled entry.");
        }
        var block = BuiltInRegistries.BLOCK.get(entry.blockId());
        if (block == null || !BuiltInRegistries.BLOCK.getKey(block).equals(entry.blockId())) {
            return new LockableBlockValidationResult(entry.blockId(), LockableBlockValidationStatus.UNKNOWN_BLOCK, "Unknown block id.");
        }
        if (entry.type() == LockTargetType.DOOR && !(block instanceof DoorBlock)) {
            return new LockableBlockValidationResult(entry.blockId(), LockableBlockValidationStatus.TYPE_MISMATCH, "Selected block is not a door.");
        }
        if (entry.type() == LockTargetType.CHEST && !(block instanceof ChestBlock)) {
            return new LockableBlockValidationResult(entry.blockId(), LockableBlockValidationStatus.TYPE_MISMATCH, "Selected block is not a chest.");
        }
        if (entry.type() == LockTargetType.GENERIC) {
            return new LockableBlockValidationResult(entry.blockId(), LockableBlockValidationStatus.OK, "Other entry is active as a single-block lock target.");
        }
        return new LockableBlockValidationResult(entry.blockId(), LockableBlockValidationStatus.OK, "Entry is active.");
    }
}

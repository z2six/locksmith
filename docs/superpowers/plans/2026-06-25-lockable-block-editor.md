# Lockable Block Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a server-authoritative fullscreen in-game editor for lockable door/chest entries and lock placement transforms.

**Architecture:** Replace direct JSON profile ownership with a server profile service backed by `config/locksmith-lockable-blocks.toml`. The service validates editable entries, produces the existing flattened `LockRenderProfile` map for gameplay/render sync, and hot-syncs clients after successful saves. The client editor owns an unsaved local copy, renders a 3D preview with shared placement math, and submits changes through permission-checked C2S payloads.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.80, custom `Screen` rendering with `GuiGraphics`, NeoForge custom payloads, Brigadier commands, existing Locksmith render/profile classes.

---

## File Structure

- Create `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockTransform.java`: immutable transform record shared by entries, validation, preview, and renderer.
- Create `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockEntry.java`: editable canonical profile entry.
- Create `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockValidationStatus.java`: validation status enum.
- Create `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockValidationResult.java`: per-entry validation result.
- Create `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockDefaults.java`: default door/chest entries.
- Create `neoforge/src/main/java/org/z2six/locksmith/config/LockableBlockConfigStore.java`: TOML load/save plus legacy JSON import.
- Create `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockProfileService.java`: authoritative server cache, permission checks, validation, save, reload, sync.
- Create `neoforge/src/main/java/org/z2six/locksmith/render/LockPlacementResolver.java`: reusable lock placement math.
- Modify `neoforge/src/main/java/org/z2six/locksmith/render/DoorLockRenderer.java`: use `LockPlacementResolver`.
- Create `neoforge/src/main/java/org/z2six/locksmith/command/LocksmithCommands.java`: `/locksmith editor` command.
- Modify `neoforge/src/main/java/org/z2six/locksmith/Locksmith.java`: initialize new service and register command listener.
- Create `neoforge/src/main/java/org/z2six/locksmith/network/OpenLockableBlocksEditorPayload.java`: S2C editor data.
- Create `neoforge/src/main/java/org/z2six/locksmith/network/SaveLockableBlocksPayload.java`: C2S edited entries.
- Create `neoforge/src/main/java/org/z2six/locksmith/network/LockableBlocksEditorSaveResultPayload.java`: S2C save result.
- Create `neoforge/src/main/java/org/z2six/locksmith/network/ReloadLockableBlocksEditorPayload.java`: C2S reload request.
- Modify `neoforge/src/main/java/org/z2six/locksmith/network/LocksmithPayloads.java`: register new payloads.
- Create `neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksEditorScreen.java`: fullscreen custom editor.
- Create `neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksEditorState.java`: local editor state and mutation helpers.
- Create `neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksPreviewRenderer.java`: 3D block and lock preview.

## Task 1: Shared Entry Model

**Files:**
- Create: `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockTransform.java`
- Create: `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockEntry.java`
- Create: `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockValidationStatus.java`
- Create: `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockValidationResult.java`

- [ ] **Step 1: Add immutable transform model**

Create `LockTransform` with explicit defaults and conversion helpers:

```java
package org.z2six.locksmith.render.profile;

public record LockTransform(
        double offsetX,
        double offsetY,
        double offsetZ,
        float rotX,
        float rotY,
        float rotZ,
        float scale,
        double hingeNudgeLeft,
        double hingeNudgeRight,
        double doubleNudgeX
) {
    public static LockTransform doorDefault() {
        return new LockTransform(-0.05D, 0.5D, -0.5D, 0.0F, 0.0F, 0.0F, 0.75F, 0.18D, 0.325D, 0.0D);
    }

    public static LockTransform chestDefault() {
        return new LockTransform(-0.025D, 0.05D, 0.45D, 0.0F, 180.0F, 0.0F, 0.75F, 0.0D, 0.0D, -0.5D);
    }

    public LockTransform clamped() {
        return new LockTransform(
                clamp(offsetX, -4.0D, 4.0D),
                clamp(offsetY, -4.0D, 4.0D),
                clamp(offsetZ, -4.0D, 4.0D),
                (float) clamp(rotX, -360.0D, 360.0D),
                (float) clamp(rotY, -360.0D, 360.0D),
                (float) clamp(rotZ, -360.0D, 360.0D),
                (float) clamp(scale, 0.05D, 8.0D),
                clamp(hingeNudgeLeft, -4.0D, 4.0D),
                clamp(hingeNudgeRight, -4.0D, 4.0D),
                clamp(doubleNudgeX, -4.0D, 4.0D)
        );
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
```

- [ ] **Step 2: Add editable entry model**

Create `LockableBlockEntry` with conversion to the existing flattened profile:

```java
package org.z2six.locksmith.render.profile;

import net.minecraft.resources.ResourceLocation;

public record LockableBlockEntry(
        ResourceLocation blockId,
        LockTargetType type,
        LockTransform transform,
        boolean builtinDefault,
        boolean enabled
) {
    public LockableBlockEntry {
        if (transform == null) {
            transform = type == LockTargetType.CHEST ? LockTransform.chestDefault() : LockTransform.doorDefault();
        }
    }

    public LockRenderProfile toRenderProfile() {
        LockTransform t = transform.clamped();
        double hingeLeft = type == LockTargetType.CHEST ? t.doubleNudgeX() : t.hingeNudgeLeft();
        double hingeRight = type == LockTargetType.CHEST ? t.doubleNudgeX() : t.hingeNudgeRight();
        return new LockRenderProfile(
                type,
                blockId,
                t.offsetX(), t.offsetY(), t.offsetZ(),
                t.rotX(), t.rotY(), t.rotZ(),
                t.scale(),
                hingeLeft,
                hingeRight
        );
    }

    public LockableBlockEntry withTransform(LockTransform next) {
        return new LockableBlockEntry(blockId, type, next, builtinDefault, enabled);
    }
}
```

- [ ] **Step 3: Add validation result types**

Create the enum and result record:

```java
package org.z2six.locksmith.render.profile;

public enum LockableBlockValidationStatus {
    OK,
    INVALID_ID,
    UNKNOWN_BLOCK,
    DUPLICATE_BLOCK,
    UNSUPPORTED_TYPE,
    TYPE_MISMATCH,
    MISSING_PROPERTIES,
    DISABLED
}
```

```java
package org.z2six.locksmith.render.profile;

import net.minecraft.resources.ResourceLocation;

public record LockableBlockValidationResult(
        ResourceLocation blockId,
        LockableBlockValidationStatus status,
        String message
) {
    public boolean okForSave() {
        return status == LockableBlockValidationStatus.OK || status == LockableBlockValidationStatus.DISABLED;
    }
}
```

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`. If it fails here, fix imports and Java record syntax before continuing.

## Task 2: Defaults And TOML Store

**Files:**
- Create: `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockDefaults.java`
- Create: `neoforge/src/main/java/org/z2six/locksmith/config/LockableBlockConfigStore.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/config/LockProfileConfig.java`

- [ ] **Step 1: Add default entries**

Create `LockableBlockDefaults` returning the same vanilla door/chest defaults currently written by `LockProfileConfig.ensureDefaultFileExists()`.

```java
package org.z2six.locksmith.render.profile;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class LockableBlockDefaults {
    private LockableBlockDefaults() {
    }

    public static List<LockableBlockEntry> create() {
        ArrayList<LockableBlockEntry> out = new ArrayList<>();
        LockTransform door = LockTransform.doorDefault();
        String[] doors = {
                "minecraft:oak_door", "minecraft:spruce_door", "minecraft:birch_door",
                "minecraft:jungle_door", "minecraft:acacia_door", "minecraft:dark_oak_door",
                "minecraft:mangrove_door", "minecraft:cherry_door", "minecraft:bamboo_door",
                "minecraft:crimson_door", "minecraft:warped_door"
        };
        for (String id : doors) {
            out.add(new LockableBlockEntry(ResourceLocation.parse(id), LockTargetType.DOOR, door, true, true));
        }
        LockTransform chest = LockTransform.chestDefault();
        out.add(new LockableBlockEntry(ResourceLocation.parse("minecraft:chest"), LockTargetType.CHEST, chest, true, true));
        out.add(new LockableBlockEntry(ResourceLocation.parse("minecraft:trapped_chest"), LockTargetType.CHEST, chest, true, true));
        return out;
    }
}
```

- [ ] **Step 2: Implement config path and save format**

Create `LockableBlockConfigStore` with `getConfigPath()`, `loadOrCreate()`, `saveAtomically(List<LockableBlockEntry>)`, and `importLegacyJsonIfNeeded()`. Use a simple line-based TOML writer with repeated `[[entries]]` sections:

```toml
[[entries]]
block = "minecraft:chest"
type = "chest"
enabled = true
offsetX = -0.025
offsetY = 0.05
offsetZ = 0.45
rotX = 0.0
rotY = 180.0
rotZ = 0.0
scale = 0.75
hingeNudgeLeft = 0.0
hingeNudgeRight = 0.0
doubleNudgeX = -0.5
```

The parser only needs to support the format the writer emits: comments, blank lines, `[[entries]]`, and `key = value`.

- [ ] **Step 3: Keep legacy JSON as importer only**

Leave `LockProfileConfig.getConfigPath()` available so the importer can find `locksmith_profiles.json`, but stop using `ensureDefaultFileExists()` as the new startup path in a later task.

- [ ] **Step 4: Verify generated TOML**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 3: Server Profile Service

**Files:**
- Create: `neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockProfileService.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/render/profile/ServerLockRenderProfiles.java`

- [ ] **Step 1: Implement authoritative cache**

Add a service with:

```java
public static synchronized void reloadFromDisk()
public static synchronized SaveResult saveFromEditor(MinecraftServer server, ServerPlayer actor, List<LockableBlockEntry> entries)
public static List<LockableBlockEntry> getEntriesSnapshot()
public static Map<ResourceLocation, LockRenderProfile> getProfilesForNetwork()
public static List<LockableBlockValidationResult> validateAll(List<LockableBlockEntry> entries)
public static boolean canEdit(ServerPlayer player)
public static void syncProfilesToAll(MinecraftServer server)
```

`canEdit` returns true when `player.hasPermissions(2)` or the server player list marks the profile as op.

- [ ] **Step 2: Implement validation**

Validation rules:

- Disabled entries return `DISABLED`.
- Null ids return `INVALID_ID`.
- Missing registry blocks return `UNKNOWN_BLOCK`.
- Duplicate enabled ids return `DUPLICATE_BLOCK`.
- `GENERIC` returns `UNSUPPORTED_TYPE`.
- `DOOR` requires `block instanceof DoorBlock`.
- `CHEST` requires `block instanceof ChestBlock` and a default block state with `ChestBlock.FACING` and `ChestBlock.TYPE`.

- [ ] **Step 3: Bridge existing server profile holder**

Modify `ServerLockRenderProfiles` so `reloadFromDisk()`, `getProfilesForNetwork()`, and `getCachedProfiles()` delegate to `LockableBlockProfileService`. Keep method names stable to minimize changes in gameplay classes.

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 4: Hot Sync And Startup Wiring

**Files:**
- Modify: `neoforge/src/main/java/org/z2six/locksmith/Locksmith.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/event/LocksmithProfileSyncEvents.java`

- [ ] **Step 1: Replace startup JSON creation**

In `Locksmith`, replace the `LockProfileConfig.ensureDefaultFileExists()` startup path with `LockableBlockProfileService.reloadFromDisk()`.

- [ ] **Step 2: Keep login sync**

Keep `LocksmithProfileSyncEvents.onPlayerLoggedIn` sending `SyncLockRenderProfilesPayload`, but source profiles from `LockableBlockProfileService.getProfilesForNetwork()` through `ServerLockRenderProfiles` compatibility.

- [ ] **Step 3: Add broadcast helper**

Inside the profile service, broadcast to every player returned by `server.getPlayerList().getPlayers()`:

```java
for (ServerPlayer target : server.getPlayerList().getPlayers()) {
    PacketDistributor.sendToPlayer(target, new SyncLockRenderProfilesPayload(getProfilesForNetwork()));
}
```

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 5: Editor Network Payloads

**Files:**
- Create: `neoforge/src/main/java/org/z2six/locksmith/network/OpenLockableBlocksEditorPayload.java`
- Create: `neoforge/src/main/java/org/z2six/locksmith/network/SaveLockableBlocksPayload.java`
- Create: `neoforge/src/main/java/org/z2six/locksmith/network/LockableBlocksEditorSaveResultPayload.java`
- Create: `neoforge/src/main/java/org/z2six/locksmith/network/ReloadLockableBlocksEditorPayload.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/network/LocksmithPayloads.java`

- [ ] **Step 1: Add entry codecs**

Each payload that carries entries writes:

```java
buf.writeUtf(entry.blockId().toString(), 256);
buf.writeByte(entry.type().id);
buf.writeBoolean(entry.enabled());
buf.writeBoolean(entry.builtinDefault());
buf.writeDouble(t.offsetX());
buf.writeDouble(t.offsetY());
buf.writeDouble(t.offsetZ());
buf.writeFloat(t.rotX());
buf.writeFloat(t.rotY());
buf.writeFloat(t.rotZ());
buf.writeFloat(t.scale());
buf.writeDouble(t.hingeNudgeLeft());
buf.writeDouble(t.hingeNudgeRight());
buf.writeDouble(t.doubleNudgeX());
```

Read using `ResourceLocation.tryParse`, `LockTargetType.fromId`, and `LockTransform`.

- [ ] **Step 2: Implement open payload handler**

`OpenLockableBlocksEditorPayload.handle` runs on the client thread and calls:

```java
Minecraft.getInstance().setScreen(new LockableBlocksEditorScreen(entries, validationResults));
```

- [ ] **Step 3: Implement save payload handler**

`SaveLockableBlocksPayload.handle` enqueues server work, calls `LockableBlockProfileService.saveFromEditor(server, player, entries)`, and sends `LockableBlocksEditorSaveResultPayload` back to the actor. On success the service also syncs profiles to all players.

- [ ] **Step 4: Implement reload payload handler**

`ReloadLockableBlocksEditorPayload.handle` checks permissions, reloads the service from disk, syncs profiles to all players, and sends a fresh `OpenLockableBlocksEditorPayload` to the requesting player.

- [ ] **Step 5: Register payloads**

Add the new payloads to `LocksmithPayloads.onRegisterPayloadHandlers`, using `playToClient` for open/save-result and `playToServer` for save/reload.

- [ ] **Step 6: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 6: Command Entry Point

**Files:**
- Create: `neoforge/src/main/java/org/z2six/locksmith/command/LocksmithCommands.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/Locksmith.java`

- [ ] **Step 1: Add command registration**

Create a command listener for `RegisterCommandsEvent`:

```java
dispatcher.register(Commands.literal("locksmith")
        .then(Commands.literal("editor")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> openEditor(ctx.getSource()))));
```

`openEditor` obtains the `ServerPlayer`, snapshots entries and validation from `LockableBlockProfileService`, sends `OpenLockableBlocksEditorPayload`, and returns success.

- [ ] **Step 2: Register listener**

In `Locksmith`, add:

```java
NeoForge.EVENT_BUS.addListener(LocksmithCommands::onRegisterCommands);
```

- [ ] **Step 3: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 7: Shared Placement Resolver

**Files:**
- Create: `neoforge/src/main/java/org/z2six/locksmith/render/LockPlacementResolver.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/render/DoorLockRenderer.java`

- [ ] **Step 1: Add resolved transform record**

In `LockPlacementResolver`, define:

```java
public record ResolvedLockPlacement(
        double offsetX,
        double offsetY,
        double offsetZ,
        float rotX,
        float rotY,
        float rotZ,
        float scale
) {}
```

- [ ] **Step 2: Move door placement math**

Implement `resolveDoor(BlockState state, LockRenderProfile profile)` with the existing hinge nudge logic:

```java
double hingeSignedNudge = (hinge == DoorHingeSide.LEFT) ? -hingeLeftMag : hingeRightMag;
double finalX = baseOffsetX + hingeSignedNudge;
```

- [ ] **Step 3: Move chest placement math**

Implement `resolveChest(BlockState state, LockRenderProfile profile)` with the existing double chest logic:

```java
double finalX = baseOffsetX;
if (type != ChestType.SINGLE && doubleNudgeX != 0.0D) {
    if (type == ChestType.LEFT) finalX += doubleNudgeX;
    if (type == ChestType.RIGHT) finalX -= doubleNudgeX;
}
```

- [ ] **Step 4: Replace renderer inline math**

In `DoorLockRenderer.renderDoorLock` and `renderChestLock`, call the resolver, then apply `placement.offsetX()`, `placement.offsetY()`, `placement.offsetZ()`, rotations, and scale.

- [ ] **Step 5: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 8: Editor State And Custom UI Shell

**Files:**
- Create: `neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksEditorState.java`
- Create: `neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksEditorScreen.java`

- [ ] **Step 1: Add editor state**

State contains:

```java
List<LockableBlockEntry> serverEntries
List<LockableBlockEntry> workingEntries
List<LockableBlockValidationResult> validationResults
int selectedIndex
String filterText
boolean dirty
String statusMessage
```

Add methods for selection, add, duplicate, remove, reset selected to default, discard, apply save result, and replace from server.

- [ ] **Step 2: Add custom screen layout**

In `render`, compute stable regions:

```java
int leftW = 230;
int rightW = 260;
int bottomH = 42;
int centerX = leftW;
int centerW = this.width - leftW - rightW;
int centerH = this.height - bottomH;
```

Draw panels with `GuiGraphics.fill`, text with `drawString`, and custom hit rectangles. Do not add vanilla `Button` widgets.

- [ ] **Step 3: Add click handling**

Implement `mouseClicked`, `mouseDragged`, `mouseReleased`, `keyPressed`, and `charTyped` to support list selection, text field editing, numeric field editing, button hit rectangles, and preview orbit drag.

- [ ] **Step 4: Add save/reload actions**

Save sends `SaveLockableBlocksPayload` with `workingEntries`. Reload sends `ReloadLockableBlocksEditorPayload`. Discard replaces `workingEntries` with `serverEntries`.

- [ ] **Step 5: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 9: Preview Renderer

**Files:**
- Create: `neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksPreviewRenderer.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksEditorScreen.java`

- [ ] **Step 1: Render selected block model**

Use `Minecraft.getInstance().getBlockRenderer().renderSingleBlock(...)` inside a clipped/scissored preview area. Use the selected entry's registry block default state.

- [ ] **Step 2: Render lock item with shared placement**

Create a temporary `LockRenderProfile` from the selected entry, call `LockPlacementResolver`, and render `new ItemStack(ModItems.LOCK_IRON.get())` using `ItemDisplayContext.FIXED`.

- [ ] **Step 3: Add orbit camera**

Store `yaw`, `pitch`, and `zoom` in the screen. While LMB dragging inside the preview panel, update yaw and pitch. Clamp pitch to `[-80, 80]`.

- [ ] **Step 4: Add reset view**

Add a custom bottom-bar or preview-panel hit rectangle that resets yaw, pitch, and zoom to defaults.

- [ ] **Step 5: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 10: Gameplay Gating Cleanup

**Files:**
- Modify: `neoforge/src/main/java/org/z2six/locksmith/event/LocksmithChestEvents.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/network/LockChestPayload.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/mixin/MixinMultiPlayerGameMode.java`

- [ ] **Step 1: Centralize chest lockability check**

Add `LockableBlockProfileService.isLockableChest(ResourceLocation blockId)` or keep compatibility through `ServerLockRenderProfiles.getCachedProfiles()`. Ensure all server-side checks use the server cache.

- [ ] **Step 2: Keep client prediction gated by sync cache**

Keep client prediction using `ClientLockRenderProfiles.get(blockId)`, so hot-sync affects prediction immediately.

- [ ] **Step 3: Verify removed entries take effect**

After save removes `minecraft:chest`, server-side `LockChestPayload` and `LocksmithChestEvents` must reject new chest locks immediately.

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 11: UX Polish And Diagnostics

**Files:**
- Modify: `neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksEditorScreen.java`
- Modify: `neoforge/src/main/java/org/z2six/locksmith/client/screen/LockableBlocksEditorState.java`

- [ ] **Step 1: Show validation state per entry**

Render each list row with a small colored status indicator:

- OK: green.
- Disabled: gray.
- Error: red.

Show the selected entry's validation message in the right panel.

- [ ] **Step 2: Disable save when local validation has blocking errors**

The server remains authoritative, and client-side validation prevents obvious invalid saves. Save is enabled only when every enabled entry has `OK`.

- [ ] **Step 3: Show server save result**

When `LockableBlocksEditorSaveResultPayload` arrives, update `statusMessage`, replace validation results, clear `dirty` on success, and keep edits on failure.

- [ ] **Step 4: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

## Task 12: Verification

**Files:**
- No planned source changes unless verification finds defects.

- [ ] **Step 1: Compile**

Run: `.\gradlew.bat :neoforge:compileJava`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Launch client**

Run: `.\gradlew.bat :neoforge:runClient`

Expected: Minecraft opens with Locksmith loaded.

- [ ] **Step 3: Verify operator access**

In a single-player world with commands enabled, run `/locksmith editor`.

Expected: the editor opens with vanilla doors, `minecraft:chest`, and `minecraft:trapped_chest`.

- [ ] **Step 4: Verify persistence and hot sync**

Change the chest `offsetY`, save, close the editor, and inspect an already locked chest.

Expected: the lock moves immediately without reconnecting, and `config/locksmith-lockable-blocks.toml` contains the new value.

- [ ] **Step 5: Verify invalid entry rejection**

Add an entry with block id `not a valid id`, save.

Expected: the server rejects the save, the editor stays open, and the right panel shows `INVALID_ID`.

- [ ] **Step 6: Verify non-operator denial**

Run a dedicated server, connect as a non-op player, and run `/locksmith editor`.

Expected: command is denied and no editor payload opens.

- [ ] **Step 7: Verify legacy import**

Delete `config/locksmith-lockable-blocks.toml`, keep an existing `config/locksmith_profiles.json`, restart.

Expected: new TOML file is created from legacy entries, and the editor shows imported entries.

# Lockable Block Editor Design

## Goal

Replace the hand-edited lock render profile configuration with a server-authoritative in-game editor for managing lockable block entries and lock placement transforms.

The editor is a fullscreen client GUI, but the server owns all saved data. Clients may view synced profiles, but only server operators may request editor data, submit changes, and cause the server to persist and hot-sync new lockable block definitions.

## Current System

Locksmith currently writes and reads `config/locksmith_profiles.json`. The file stores grouped profiles with shared render values, and `LockRenderProfilesLoader` flattens them into one `LockRenderProfile` per block id. The server caches this flattened map in `ServerLockRenderProfiles`, syncs it to clients on login with `SyncLockRenderProfilesPayload`, and client rendering reads it from `ClientLockRenderProfiles`.

Door lock gameplay is hard-coded to `DoorBlock`. Chest lock gameplay is gated through the profile map and only allows entries whose profile type is `CHEST`. The renderer supports doors and vanilla chest-style blocks, but the placement math is embedded inside `DoorLockRenderer`.

## Requirements

1. The server is authoritative for lockable block entries, transform values, validation, persistence, and sync.
2. The editor can only be opened and saved by operators. The client may request the editor, but the server must verify permissions before sending editor data or accepting saves.
3. Saving in the GUI writes the server config file, reloads the active server cache, and immediately syncs updated profiles to all connected clients.
4. Existing visible locks update immediately after a successful save because renderers read the updated client profile cache.
5. The editor shows all active lockable entries, including default entries.
6. Adding, editing, removing, resetting, saving, discarding, and reloading entries is possible from the GUI.
7. The editor exposes clear validation status per entry: invalid block id, missing registry block, duplicate id, unsupported target type, missing required block properties, permission denied, save failure, or successful sync.
8. The central preview renders the selected block in 3D with the Locksmith iron lock attached by the same placement resolver used by world rendering.
9. The preview supports mouse orbit with left-click drag inside the preview panel.
10. The GUI uses custom-drawn controls and layout, not vanilla button styling.

## Scope

The first implementation supports the target types Locksmith can enforce today:

- `DOOR`: Minecraft `DoorBlock` instances.
- `CHEST`: Minecraft `ChestBlock`-style instances with expected facing and chest type properties.

`GENERIC` remains a data enum value for forward compatibility, but the editor marks it unsupported until gameplay enforcement and rendering semantics exist for arbitrary blocks. This prevents the GUI from promising that any random block can be locked when the server logic cannot enforce that yet.

## Data Model

Introduce an editor-oriented entry model:

```text
LockableBlockEntry
  blockId: ResourceLocation
  type: LockTargetType
  transform:
    offsetX, offsetY, offsetZ
    rotX, rotY, rotZ
    scale
    hingeNudgeLeft
    hingeNudgeRight
    doubleNudgeX
  builtinDefault: boolean
  enabled: boolean
```

The runtime network model continues to use a flattened `LockRenderProfile` map for gameplay and render sync. The new entry store is the canonical persisted form, and it produces the flattened map whenever the server loads, saves, reloads, or syncs profiles.

Persisted config is server-side, human-readable TOML at `config/locksmith-lockable-blocks.toml`. The implementation includes a one-time importer for `locksmith_profiles.json` when the new TOML file does not exist, then writes the imported entries to the TOML file.

## Server Architecture

Create a server profile service responsible for:

- Loading defaults and saved entries.
- Importing legacy JSON profiles when no new config exists.
- Validating entries against the block registry and target type rules.
- Producing the flattened profile map used by gameplay and render sync.
- Saving edited entries to disk atomically.
- Reloading the active cache after save.
- Broadcasting updated profile sync packets to all connected clients.

Permission checks use server operator status. The server checks permissions both when handling an editor-open request and when handling a save request. Client-side checks may improve UX, but they are not trusted.

## Client Architecture

Add a fullscreen `LockableBlocksEditorScreen` opened with `/locksmith editor`. The server command requires operator permissions and sends editor data to the requesting player. A client-side keybind can be added later, but the initial entry point is the command to avoid local key conflicts and to keep permission behavior explicit.

The screen owns a local editable copy of entries. Unsaved edits affect the preview immediately but do not affect world rendering until saved and accepted by the server.

The screen layout:

- Left panel: searchable list of lockable entries, add, duplicate, remove, reset entry.
- Center panel: 3D block and lock preview with orbit camera.
- Right panel: type selector, transform fields/sliders/steppers, nudge controls, validation details.
- Bottom bar: save, discard, reload from server, reset defaults, close.

## Rendering Architecture

Extract the placement calculation from `DoorLockRenderer` into a reusable resolver. The resolver receives a block state, target type, facing/chest/door metadata when available, and a lockable entry/profile, then returns the final local transform for the lock item.

`DoorLockRenderer` uses the resolver for in-world rendering. The editor preview uses the same resolver for the selected block preview. This keeps tuning work honest: if the preview looks right, the world render uses the same math.

The preview renderer renders the selected block model with Minecraft's block renderer and the Locksmith lock item with the item renderer. It supports a stable default camera, mouse orbit, and reset-view behavior.

## Network Flow

Open editor:

1. Player runs `/locksmith editor`.
2. Server command checks `hasPermissions`.
3. Server sends `OpenLockableBlocksEditorPayload` with entries and validation data to the player. If permission fails, the command returns a denial message and sends no editor data.

Save:

1. Client sends `SaveLockableBlocksPayload` with the edited entry list.
2. Server checks permissions again.
3. Server validates all entries.
4. If invalid, server sends `LockableBlocksEditorSaveResultPayload` with errors and does not change active profiles.
5. If valid, server writes config, reloads active entries, broadcasts `SyncLockRenderProfilesPayload` to all clients, and sends success result to the editor client.

Reload:

1. Operator client requests reload from disk.
2. Server checks permissions, reloads, broadcasts sync, and returns refreshed editor data.

## Validation

Validation is explicit and user-facing:

- `OK`: Entry is active and will sync.
- `INVALID_ID`: Resource location cannot be parsed.
- `UNKNOWN_BLOCK`: No block exists for the id.
- `DUPLICATE_BLOCK`: More than one enabled entry targets the same block.
- `UNSUPPORTED_TYPE`: Type is not currently enforceable.
- `TYPE_MISMATCH`: The block does not satisfy the selected type, such as a non-door set to `DOOR`.
- `MISSING_PROPERTIES`: A chest-like entry lacks required properties for current placement logic.
- `DISABLED`: Entry is saved but not active.

Invalid saves do not partially apply. The server either accepts the full edited set or rejects it with actionable reasons.

## Testing And Verification

Add focused tests around config load/save, legacy JSON import, duplicate detection, and validation logic if the current Gradle setup can support lightweight JVM tests without fighting the mod runtime. If that is impractical, keep validation code small and deterministic, then verify through NeoForge client runs.

Manual verification:

- Existing default door and chest entries appear in the editor.
- Non-operator cannot open or save the editor.
- Operator can edit a chest transform, save, and see an existing lock move immediately without reconnecting.
- Invalid block ids show clear errors and do not save.
- Removing a chest entry prevents new locks on that chest type after save.
- Reload from disk restores server state and syncs clients.
- Legacy `locksmith_profiles.json` imports into the new store when no new config exists.

## Deferred Work

True arbitrary block locking is deferred. It needs separate gameplay rules for normalizing positions, blocking interaction, rendering placement, lock lifecycle cleanup, and permission checks for blocks that are neither doors nor chest-style containers.

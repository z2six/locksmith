<p align="center">
  <img src="https://media.forgecdn.net/attachments/description/null/description_19d46064-372e-4feb-853e-354836ae2339.png" alt="Locksmith title banner">
</p>

<p align="center">
Allows the locking of blocks using physical keys instead of permissions.
</p>

<p align="center">
By default, vanilla doors, chests, and trapped chests are supported.
Additional blocks — including modded ones — can be configured server-side.
</p>

<p align="center">
  <img src="https://media.forgecdn.net/attachments/description/null/description_c1ee597e-11c0-4339-9928-c51c137a907e.png" alt="Features banner">
</p>

<p align="center">
  🔑 Craft iron keys and register them with a secret passphrase<br><br>
  🔒 Lock doors, chests, and other blocks using those keys<br><br>
  📋 Duplicate keys safely (master & copied key system)<br><br>
  🚪 Optional auto-closing for locked doors<br><br>
  🧩 Supports modded blocks via simple configuration<br><br>
  🌐 Fully server-authoritative & multiplayer-safe
</p>

<p align="center">
  <img src="https://media.forgecdn.net/attachments/description/null/description_1aeb1c2d-e797-456a-962f-c9844d94b2c2.png" alt="How-to banner">
</p>

<p align="center">
    1. Craft an iron key (1x iron ingot, 2x iron nugget below)<br><br>
    2. Right-click while holding the key to register it.<br><br>
    3. Enter a secret passphrase when prompted.<br><br>
    4. Right-click a door or chest with the registered key to lock it.<br><br>
    5. Duplicate keys using the minting recipe if multiple players need access.
    Players without a matching key will be unable to open the locked block.
</p>

<p align="center">
  <img src="https://media.forgecdn.net/attachments/description/null/description_0d6e24e9-0307-4a76-ae20-278ca808e1c8.png" alt="Customize banner">
</p>

<p align="center">
Server administrators can define exactly which blocks are lockable.
This includes vanilla blocks as well as blocks added by other mods.
</p>

<p align="center">Example: adding a custom door:</p>

```json
{
  "id": "my_mod_doors",
  "type": "door",
  "blocks": [
    "modid:steel_door",
    "modid:reinforced_door"
  ],
  "render": {
    "offsetX": -0.05,
    "offsetY": 0.5,
    "offsetZ": -0.5,
    "rotX": 0.0,
    "rotY": 0.0,
    "rotZ": 0.0,
    "scale": 0.75,
    "hingeNudgeLeft": 0.18,
    "hingeNudgeRight": 0.325
  }
}
```

<p align="center">
These profiles control locking behavior only.
They do not affect block hardness or explosion resistance.
</p>

<h2 align="center">
💡 Tips
</h2>

<p align="center">
This mod does not make locked blocks invulnerable. 
I'd strongly recommend combining this mod with something 
like OpenPAC (this mod was built exactly for that scenario).
</p>

<h2 align="center">
📝 Licensing and distribution
</h2>

<p align="center">
Locksmith is released under the MIT License. All code and assets are MIT licensed. 
You are free to use, modify, and redistribute the mod in any form,
including modpacks and commercial projects, in accordance with the license.
</p>

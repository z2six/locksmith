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
Craft iron keys and register them using a secret passphrase.
</p>

<p align="center">
The passphrase is never stored in plain text.
It is converted into a SHA hash and written to the key’s NBT data.
</p>

<p align="center">
Registered keys can be duplicated safely using the key minting recipe.
</p>

<p align="center">
Locked doors can optionally auto-close after a configurable delay.
</p>

<p align="center">
Locksmith does not make blocks indestructible.
Locked blocks can still be broken or destroyed by explosions.
</p>

<p align="center">
For land ownership or grief protection, a claims mod such as OpenPAC is recommended.
</p>

<p align="center">
  <img src="https://media.forgecdn.net/attachments/description/null/description_1aeb1c2d-e797-456a-962f-c9844d94b2c2.png" alt="How-to banner">
</p>

<p align="center">
Craft an iron key.
</p>

<p align="center">
Right-click while holding the key to register it.
</p>

<p align="center">
Enter a secret passphrase when prompted.
</p>

<p align="center">
Right-click a door or chest with the registered key to lock it.
</p>

<p align="center">
Duplicate keys using the minting recipe if multiple players need access.
</p>

<p align="center">
Players without a matching key will be unable to open the locked block.
</p>

<p align="center">
  <img src="https://media.forgecdn.net/attachments/description/null/description_0d6e24e9-0307-4a76-ae20-278ca808e1c8.png" alt="Customize banner">
</p>

<p align="center">
Server administrators can define exactly which blocks are lockable.
</p>

<p align="center">
This includes vanilla blocks as well as blocks added by other mods.
</p>

Example: adding a modded chest
```json
{
  "minecraft:chest": {
    "type": "chest"
  },
  "modid:custom_chest": {
    "type": "chest"
  }
}
```

Example: adding a custom door
```json
{
  "minecraft:oak_door": {
    "type": "door"
  },
  "modid:steel_door": {
    "type": "door"
  }
}
```

<p align="center">
These profiles control locking behavior only.
They do not affect block hardness or explosion resistance.
</p>

<p align="center">
Licensing and distribution
</p>

<p align="center">
Locksmith is released under the MIT License.
</p>

<p align="center">
All code and assets are MIT licensed.
</p>

<p align="center">
You are free to use, modify, and redistribute the mod in any form,
including modpacks and commercial projects, in accordance with the license.
</p>

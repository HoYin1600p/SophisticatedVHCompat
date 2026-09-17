# Sophisticated VH Compat

Small Forge 1.18.2 compatibility mod for Sophisticated Storage and Sophisticated Core in Vault Hunters packs. It can run on the client, server, or both; either side accepts a connection when the other side does not have it installed.

## Included fixes

- Compressium display rendering compatibility for Sophisticated Storage limited barrel faces.
- Spore blossoms on barrel faces rotate 90 degrees, with their base against the front panel and petals projecting outward, in both baked and dynamic rendering.
- Big dripleaf displays move outward by 6/16 and small dripleaf by 4/16 of a block. Both offsets scale down for two-, three-, and four-slot barrels, preserving the approved stem placement.
- Limited barrel front-face sneak-right-click opens the inventory with an empty main hand. Includes a client fallback for servers without this mod.
- Packed limited barrel Shift tooltips show each nonempty slot's calculated contents, including compression denominations, in slot order.
- Packed barrel Shift tooltips show installed upgrade icons, the correct stack multiplier, and a material tier label for both regular and limited barrels.
- Compression extraction satisfies external-storage requests without duplicating items in Refined Storage crafting. Hotbar swaps reconcile calculated counts without sharing mutable stacks.
- Mixed compression ratios use the correct capacity. Invalid insertion states reject items without consuming them; calculated slots refresh tracking before notifying listeners.
- Compacting queues callbacks by inventory and excludes compression slots from both compaction and ingredient extraction. Pending tick work is drained safely.
- JEI recipe transfer returns only the unaccepted remainder, including when the player's inventory is full, and saves changes to partially filled stacks.
- Shared crafting upgrades notify other open menus when their inputs change.
- Memory and filter changes refresh slot tracking. Clearing all memory releases compression reservations; fallback insertion respects reserved slots.
- Upgrade interface lookup uses a concurrent cache.
- Internal whole-slot removal handles upgraded stacks without changing ordinary automation extraction limits.
- Sorting shortcuts work when the pointer is outside inventory slots.
- Barrel count labels reuse bounded cached layouts, reset on font/resource reload, and preserve fractional widths. Fill rendering reuses vectors while retaining sprite UVs and live lighting.
- Sophisticated Storage controllers have an optional configurable range from 1 to 96 blocks, set to 24 but disabled by default. Operators can activate it with `/svhc controller-range enable`.

Vault Additions does not disable any fixes. Remove overlapping mixins from it before using this mod alongside it. Compressium is optional; its display fix is inactive when the mod is absent and retains the original model when its texture is unavailable.

## Installation sides

| Feature | Client installation | Server installation |
| --- | --- | --- |
| Models, face positions, labels and packed tooltips | Required to see the changes | Not required |
| Sorting shortcut and front-face interaction fallback | Available | Native interaction also available |
| Duplication, compression, crafting, compacting and inventory fixes | Applies to local single-player storage and client prediction | Required for authoritative multiplayer fixes |

VH Compat adds no required network channel or new content registry entries. A client without it can join a server with it, and a client with it can join a server without it. Client-only installation cannot repair an unpatched server's inventory logic. Sophisticated Core and Storage themselves still need to match the pack's requirements.

The supported release baseline is Core 1.18.2-0.6.4.604 and Storage 1.18.2-0.9.8.915. The native Core/Storage backport pair is detected to avoid applying duplicate patches.

Compression quantities use the same calculated slot view as a placed barrel. For example, one iron block can also appear as nine ingots and 81 nuggets; these are equivalent views of the same contents. The preview initializes a detached copy of the synchronized data so hovering cannot change the stored inventory.

## Build

```powershell
.\gradlew.bat clean test jarJar
```

The distributable jar is written to `build\libs\SophisticatedVHCompat-1.18.2-0.0.2-all.jar`.

The combined distribution includes GPL-3.0 Sophisticated code. [NOTICE.md](NOTICE.md) identifies the retained source revisions and credits; see [LICENSE](LICENSE) for the distribution terms. The original VH Compat MIT grant is retained in [LICENSE-MIT](LICENSE-MIT).

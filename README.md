# Sophisticated VH Compat

Small Forge 1.18.2 compatibility mod for Sophisticated Storage and Sophisticated Core in Vault Hunters packs. It can run on the client, server, or both; either side accepts a connection when the other side does not have it installed.

## Included fixes

- Compressium display rendering compatibility for Sophisticated Storage limited barrel faces.
- Spore blossoms on barrel faces rotate 90 degrees, with their base against the front panel and petals projecting outward, in both baked and dynamic rendering.
- Big and small dripleaf displays move outward by 6/16 of a block, preserving their orientation and size. Each offset can be tuned independently in `BarrelDisplayAdjustments`.
- Limited barrel front-face sneak-right-click client interaction fix.
- Packed limited barrel Shift tooltips show each nonempty slot's calculated contents, including compression denominations, in slot order.
- Packed barrel Shift tooltips show installed upgrade icons, the correct stack multiplier, and a material tier label for both regular and limited barrels.
- Compression and Compacting upgrades no longer recurse through controller or external-storage insertions, malformed compression states reject insertion without consuming items, and calculated slot tracking stays synchronized.
- Stack-upgraded storage slots can satisfy large extraction requests in one operation.
- Optional mixin gate that skips the original Compressium display/interaction fixes when `vaultadditions` is installed. The packed tooltip and plant display fixes remain enabled.

Compression quantities use the same calculated slot view as a placed barrel. For example, one iron block can also appear as nine ingots and 81 nuggets; these are equivalent views of the same contents. The preview initializes a detached copy of the synchronized data so hovering cannot change the stored inventory.

## Build

```powershell
.\gradlew.bat clean test jarJar
```

The distributable jar is written to `build\libs\SophisticatedVHCompat-1.18.2-0.0.2-all.jar`.

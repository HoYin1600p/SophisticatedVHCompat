# Sophisticated VH Compat

Forge 1.18.2 compatibility fixes for Sophisticated Storage and Sophisticated Core in Vault Hunters packs. It can be installed on the client, the server, or both. It adds no required network channel: clients without it can join a server that has it, and clients with it can join a server that does not.

## Client-side features

Install on the client to see and use:

- Correct Compressium, spore blossom, big dripleaf, and small dripleaf displays on Sophisticated Storage barrel faces.
- Packed-barrel Shift tooltips with calculated compression contents, installed upgrades, stack multipliers, and material tiers.
- The limited-barrel empty-hand sneak-right-click fallback when joining a server without the mod.
- Count-label and fill-rendering improvements for barrel faces.

## Server-side features

Install on the server for authoritative gameplay fixes:

- Compression extraction that avoids Refined Storage crafting duplication and correctly handles hotbar swaps, mixed ratios, invalid insertion states, and calculated-slot tracking.
- Compacting, crafting, JEI transfer, memory, filter, sorting, and upgraded-inventory fixes from newer Sophisticated releases.
- Correct shared crafting-upgrade updates for everyone viewing the same storage.
- An optional Sophisticated Storage controller range from 1 to 96 blocks. It defaults to disabled; operators can enable it with `/svhc controller-range enable`.

## Installing on both sides

Installing on both client and server gives players the visual and tooltip improvements while the server applies the gameplay fixes. Client-only installation cannot repair an unpatched multiplayer server's inventory logic. Sophisticated Core and Sophisticated Storage must still match the pack's normal requirements.

The supported release baseline is Sophisticated Core 1.18.2-0.6.4.604 and Sophisticated Storage 1.18.2-0.9.8.915. Compressium is optional; its display compatibility is inactive when Compressium is absent.

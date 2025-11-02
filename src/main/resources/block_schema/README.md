# BlockStateUpgradeSchema
JSON schemas and other data needed for upgrading blockstates found in older Minecraft: Bedrock worlds

## Motivation

Since Minecraft Bedrock doesn't auto-upgrade terrain or inventories unless they've been loaded during a game session, any software that supports Minecraft Bedrock worlds has to accommodate all of the old blockstates all the way back to the first versions that used them, and many backwards-incompatible changes were made since then.
If they do not, they may randomly fail to load chunks in older worlds that work just fine in latest Bedrock.

Projects such as [CloudburstMC/BlockStateUpdater](https://github.com/CloudburstMC/BlockStateUpdater) solve this with code. However, this approach has several disadvantages:
- Non-portable - can't be used by a non-JVM language
- Relies on manual analysis of the Minecraft server binary
- Created manually by humans, which is naturally an error-prone process
- Some changes may be missed (especially those which Minecraft itself doesn't account for, e.g. the addition of `item_frame_photo_bit`)

This project instead provides data that describes how to upgrade without binding the information to any particular language.

It has the following advantages:
- Not bound to any language, interpreter or project
- Data quality is at least as good as Bedrock's own, due to being generated from information created by BDS (and amended manually where Mojang themselves made mistakes)
- All changes are accounted for, including those which aren't obvious from analysing the game code
- No dependent code changes required to support new Minecraft versions - just bumping your required version of BedrockBlockUpgradeSchema

## `nbt_upgrade_schema/*.json`
These schemas describe how to upgrade blockstate NBT from one version to the next. The structure of the schema is described in `nbt_upgrade_schema_schema.json`. An example implementation can be seen [in PocketMine-MP 5.21.0](https://github.com/pmmp/PocketMine-MP/blob/5.21.0/src/data/bedrock/block/upgrade/BlockStateUpgrader.php).

### Usage notes
- Mojang don't always bump the format version when making backwards-incompatible changes. A prominent example of this is in the [`0131_1.18.20.27_beta_to_1.18.30.json`](/nbt_upgrade_schema/0131_1.18.20.27_beta_to_1.18.30.json).
- `remappedStates` requires different treatment than other rules:
  - It **must have highest priority**
  - If a blockstate matches a remap rule, **do not apply any other transformations** (the newly mapped state will be correct for the new version).
  - `oldState` acts as a **filter, not an exact match**. Therefore, remapped state rules must be sorted from most specific filter -> least specific filter to ensure correct transformations. (This is the order they are provided in by the JSON).
- With the exception of `remappedStates`, modifications can be applied in any order.
  - For example, `renamedIds` can be applied before or after `renamedProperties`.
  - To facilitate this, `addedProperties`, `renamedProperties`, `removedProperties`, `remappedPropertyValues` and `flattenedProperties` always use the old blockID and old property names for indexing.

### How are new schemas created?

#### Generated from data
First, you need to get a `.bin` mapping table file, which you can obtain using the current version of BDS + [pmmp/bds-mod-mapping](https://github.com/pmmp/bds-mod-mapping). The mod creates these files by feeding old block palettes into BDS, and outputting a pairing of every blockstate from the respective old palette to its upgraded counterpart. The output files will be placed in `mapping_files/old_palette_mappings`.

A mapping table file is then given to PocketMine-MP's [schema generator script](https://github.com/pmmp/PocketMine-MP/blob/stable/tools/blockstate-upgrade-schema-utils.php). This script analyzes patterns in the upgraded blockstates to calculate what changes were made, and then generates a JSON file like the ones you see in this repo.

*Why not just use the mapping table files directly?* The mapping tables are typically large and contain lots of redundant information, while also being very difficult for humans to analyze and modify. By post-processing the tables, we can extract only the useful information and represent it in a much more compact, human-readable and human-editable way.

#### Written by hand
Since the schemas are JSON, they can be created and modified by humans. This is useful when Mojang themselves have incorrectly performed upgrades, or if it's not possible to generate a schema for some reason. However, more work will be needed to test and verify correctness.

#### File name structure
Every JSON schema has three variables, and is structured like this: `<schemaID>_<oldPaletteVersion>_to_<newPaletteVersion>.json`.

- Schema ID: a number assigned by us to keep the schemas sorted properly. By convention, the final digit is usually left as a 1 for new schemas, to allow us to slot in extra schemas between existing ones if necessary without renaming all subsequent files.
- Old palette version: filename (without `.nbt` extension) of the palette in [BedrockBlockPaletteArchive](https://github.com/pmmp/BedrockBlockPaletteArchive) that this schema applies to
- New palette version: filename (without `.nbt` extension) of the palette in the archive which all of the upgraded states should be testable against

This file name structure allows us to test and regenerate the schemas using [BedrockBlockPaletteArchive](https://github.com/pmmp/BedrockBlockPaletteArchive).

> [!CAUTION]
> A common mistake is to name the old version according to the previous schema. This is not correct and will lead to testing errors.
>
> For example, a new palette might add new blocks without changing existing ones (so no new schema), but the subsequent version might modify the blocks added in the newer version.
>
> This mistake was made with the 1.21.50 -> 1.21.60 schema (incorrectly used 1.21.40 as base version) which led to testing errors.

## `block_legacy_id_map.json`
This JSON file contains a mapping of string ID -> legacy ID for all blocks known up 1.16.0.

Technically, you'd only need everything up to 1.2.13, but the excess might be handy in some cases.

## `id_meta_to_nbt/*.bin`
These binary files contain mappings of all known valid ID/meta combinations to their corresponding blockstate NBTs for that version.
For example, `1.12.0.bin` allows you to convert 1.12.0 ID/meta into 1.12.0 NBTs.

### Usage
If you only plan to upgrade worlds to the latest version (1.20.1 at the time of writing), you will only need the 1.12.0 file, which will suffice for upgrading all older blocks (and blockitems) to 1.12 and newer.

However, if you want to upgrade to 1.9, 1.10 or 1.11, you may need the 1.9.0 file in order to upgrade saved blockitems, as 1.9 started to save blockitems using blockstate NBT on disk. The 1.12.0 file won't be suitable for this case, as it contains the NBT blockstates appropriate for 1.12, which will not work on earlier versions.

If you previously used the now-deleted `1.12.0_to_1.18.10_blockstate_map.bin`, you can replace it directly with the `1.12.0.bin`. You will need to upgrade the states within as before.

### Schema
The file is structured as described below.

- unsigned varint32 - Number of entries
  - unsigned varint32 - block string ID length
  - byte[] - block string ID
  - unsigned varint32 - Number of meta -> blockstate pairings
    - unsigned varint32 - Meta value
    - TAG_Compound (standard little-endian) - NBT blockstate (as of that file's version) corresponding to the ID and meta pair

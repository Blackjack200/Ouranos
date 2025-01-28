# BedrockItemUpgradeSchema

JSON schemas for upgrading items found in older Minecraft: Bedrock world saves

## Background

As with blocks, Bedrock doesn't auto upgrade items (e.g. in inventories, item frames, dropped items, etc) unless the chunk they were in has been loaded and saved.

This means that any program that wants to support all Minecraft: Bedrock worlds needs to know how to upgrade this old data.

## Repository contents

- `id_meta_upgrade_schema` subdirectory contains a list of JSON schemas for upgrading from version to version incrementally.
- `item_legacy_id_map.json` contains a mapping of legacy numeric IDs to their string ID counterparts (up to 1.16, although numeric IDs haven't been used in vanilla world saves since 1.5)
- `1.12.0_item_id_to_block_id_map.json` contains a mapping of item IDs to corresponding block IDs for all blockitems.

## Recommended methods for deserializing old data

Items are rather more of a pain to handle than blocks due to the lack of any versioning. This means we can only guess at the actual version and/or apply all upgraders all the time.


### Classic items (MCPE <= 1.5, PM <= 1.12)
1. start with int ID + meta
2. 1.16 string ID via `item_id_map.json` -> string ID + meta
3. deserialize as medieval item


### Medieval items (MCPE 1.6 - 1.8)
1. start with string ID + meta
2. if ID found in `1.12.0_item_id_to_block_id_map.json`, deserialize as blockitem; otherwise as normal item

#### Non-blockitems
3. deserialize as modern item

#### Blockitems
3. string block ID via `1.12.0_item_id_to_block_id_map.json` -> string block ID + meta
4. convert to blockstate using [BedrockBlockUpgradeSchema](https://github.com/pmmp/BedrockBlockUpgradeSchema) data -> blockstate NBT
6. deserialize blockstate NBT as block


### Modern items (MCPE 1.9 - present)
1. start with string ID + meta / blockstate NBT
2. if blockstate NBT found, deserialize as blockitem; otherwise as normal item

#### Non-blockitems
3. current string ID via schemas provided in `id_meta_upgrade_schema/` subdirectory -> current string ID + meta

#### Blockitems
3. deserialize blockstate NBT as block (may require [BedrockBlockUpgradeSchema](https://github.com/pmmp/BedrockBlockUpgradeSchema) for upgrading)

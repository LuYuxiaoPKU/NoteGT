# chouzz/minecraft-mod-dev — 小文件存档（mod-links.md + mod-env-check.sh）

> 来源：https://github.com/chouzz/minecraft-mod-dev

## references/mod-links.md（原版全文）

# Minecraft Modding Documentation Index

## Official Loaders
- **NeoForge 1.21:** https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- **NeoForge Latest:** https://docs.neoforged.net/docs/gettingstarted/
- **Fabric Wiki:** https://wiki.fabricmc.net/develop:start

## Popular Mod APIs
- **JEI (Just Enough Items):** https://github.com/mezz/JustEnoughItems/wiki
- **AE2 (Applied Energistics 2):** https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/API.md
- **Create Mod:** https://github.com/Creators-of-Create/Create/wiki
- **Patchouli (Documentation Book):** https://vazkiiMods.github.io/Patchouli/docs/

## UI & Frameworks
- **Modern UI:** https://www.curseforge.com/minecraft/mc-mods/modern-ui
- **Cloth Config:** https://github.com/shedaniel/cloth-config

## scripts/mod-env-check.sh（原版全文）

```bash
#!/bin/bash
echo "--- Minecraft Modding Environment Check ---"
if [ -f "gradle.properties" ]; then
    echo "Found gradle.properties:"
    grep -E "minecraft_version|neoforge_version|neo_version|fabric_version|fabric_loader_version|create_version" gradle.properties
fi

echo "Dependencies Check:"
find . -mindepth 1 -maxdepth 4 \( -name "build.gradle" -o -name "build.gradle.kts" \) -not -path "*/build/*" -print0 |
while IFS= read -r -d '' file; do
    matches=$(grep -E "neoforge|fabric-loader|mzm.jei|appeng|com.simibubi.create" "$file")
    if [ -n "$matches" ]; then
      echo "$file:"
      echo "$matches"
    fi
done
```

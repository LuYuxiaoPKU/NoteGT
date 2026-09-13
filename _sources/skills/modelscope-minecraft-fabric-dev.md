# Minecraft Fabric Mod Development Skill（ModelScope @majiayu000 原版全文）

> 来源：https://www.modelscope.cn/skills/@majiayu000/minecraft-fabric-dev
> 源仓库：https://github.com/majiayu000/claude-skill-registry/tree/main/skills/data/minecraft-fabric-dev
> 抓取：2026-08（ModelScope API ReadMeContent）
> 摘要见 `docs/10-模组开发Skill笔记.md`。

---
name: minecraft-fabric-dev
description: Comprehensive guidance for Minecraft mod development with Fabric, including porting from other mod loaders (Forge, NeoForge). Integrates MCP servers for decompilation, documentation access, and mixin validation. Use when developing Fabric mods, porting from Forge, or working with Minecraft source code.
---

# Minecraft Fabric Mod Development Skill

## Overview

This skill provides comprehensive guidance for Minecraft mod development with Fabric, including porting from other mod loaders (Forge, NeoForge). It integrates three MCP servers to provide complete tooling for mod development.

## Available MCP Servers

### 1. minecraft-dev-mcp
Core Minecraft development tools for source code access, decompilation, and analysis.

### 2. fabric-docs-mcp
Official Fabric documentation access with version-specific content.

### 3. baritone-docs-mcp
Baritone pathfinding library documentation (useful for AI/automation mods).

## Core Workflows

### Initial Setup Workflow

**When starting ANY Minecraft development task:**

1. **Sync documentation first:**
   ```
   sync_fabric_docs (force: false) → Get latest Fabric docs
   baritone_refresh_docs → Get Baritone docs if needed
   ```
2. **Identify target version:**
   ```
   list_fabric_versions → See available Fabric versions
   list_minecraft_versions → See available/cached Minecraft versions
   ```
3. **Decompile target version (if needed):**
   ```
   decompile_minecraft_version (version, mapping: "yarn", force: false)
   ```

### Understanding Mappings

**Mapping Types (in priority order for Fabric):**
- **yarn** - Community-driven, human-readable names (PREFERRED for Fabric)
- **mojmap** - Official Mojang names (good for vanilla reference)
- **intermediary** - Stable obfuscation-independent IDs (used internally by Fabric)
- **official** - Obfuscated names (a, b, c, etc.) - **NOTE: Minecraft is transitioning to de-obfuscated releases**

**When to use each:**
- Development: Use **yarn** (best community support)
- Reading vanilla code: Use **mojmap** (official names)
- Mapping translation: Use **intermediary** as bridge
- Deobfuscating: From **official** to yarn/mojmap

**Future-Proofing Note:** Starting with experimental snapshots after 1.21.11, Minecraft is releasing de-obfuscated builds. Eventually the "official" mapping will contain human-readable names.

**Current State (1.21.11 and earlier):** Official = obfuscated, use yarn/mojmap for development
**Future State (experimental snapshots+):** Official = de-obfuscated, still prefer yarn for Fabric consistency

## Common Development Tasks

### 1. Understanding Minecraft Source Code
```
Step 1: Find the class
  → search_fabric_docs (query: "entity", mcVersion: "latest")
  → search_minecraft_code (version, query: "Entity", searchType: "class", mapping: "yarn")
Step 2: Get source code
  → get_minecraft_source (version, className: "net.minecraft.entity.Entity", mapping: "yarn")
Step 3: Get documentation context
  → get_documentation (className: "Entity")
  → get_fabric_doc (path: "develop/entities.md", mcVersion: "latest")
```

### 2. Creating Mixins
```
Step 1: Understand the target
  → get_minecraft_source (version, className: "[target]", mapping: "yarn")
  → get_fabric_doc (path: "develop/mixins.md", mcVersion: "latest")
Step 2: Write mixin code
Step 3: Validate mixin
  → analyze_mixin (source: "[mixin code]", mcVersion: "[version]", mapping: "yarn")
Step 4: Fix issues
```

**Critical rules:**
- Mixins MUST use yarn mappings for Fabric
- Validate EVERY mixin before suggesting it's complete
- Check injection points exist in target class
- Verify method signatures match exactly
- Consider mixin priority and conflicts

### 3. Using Access Wideners

**Access widener syntax:**
```
accessWidener v2 named
accessible class net/minecraft/class/Name
accessible method net/minecraft/class/Name methodName (Lparams;)Lreturn;
accessible field net/minecraft/class/Name fieldName Ltype;
extendable class net/minecraft/class/Name
mutable field net/minecraft/class/Name fieldName Ltype;
```

### 4. Analyzing Existing Mods
```
Step 1: Extract mod metadata
  → analyze_mod_jar (jarPath: "[path]", includeAllClasses: false, includeRawMetadata: true)
Step 2: Examine mixins (if present)
Step 3: Check dependencies and entry points
Step 4: Decompile/remap if needed
  → remap_mod_jar (inputJar, outputJar, mcVersion, toMapping: "yarn")
```

### 5. Version Migration & Porting
```
Step 1: Get high-level overview
  → compare_versions (fromVersion, toVersion, mapping: "yarn", category: "all")
Step 2: Detailed API changes
  → compare_versions_detailed (fromVersion, toVersion, mapping: "yarn",
     packages: ["net.minecraft.entity", "net.minecraft.world"], maxClasses: 500)
Step 3: Check registry changes
  → get_registry_data (version: "[old]", registry: "blocks")
Step 4: Search for specific changes
  → search_fabric_docs (query: "migration [version]", mcVersion: "all")
```

**Breaking changes checklist:**
- Class renames/moves
- Method signature changes
- Field type changes
- Registry ID changes
- Removed/deprecated APIs

### 6. Porting from Forge to Fabric

| Forge Pattern | Fabric Equivalent |
|---------------|-------------------|
| `@Mod` class | `ModInitializer` interface in fabric.mod.json |
| `MinecraftForge.EVENT_BUS.register()` | Event callbacks in respective classes |
| `@SubscribeEvent` | Direct method registration with event |
| Capabilities | Cardinal Components API (separate library) |
| `@ObjectHolder` | Direct `Registry.register()` calls |
| Config (ForgeConfig) | Cloth Config API or custom solution |
| `@OnlyIn(Dist.CLIENT)` | `client` entrypoint in fabric.mod.json |
| Network packets (SimpleChannel) | Fabric Networking API |
| `@Mod.EventBusSubscriber` | `ClientModInitializer` / `DedicatedServerModInitializer` |

**Critical Fabric-specific requirements:**
- `fabric.mod.json` replaces `mods.toml`
- Mixins config file (`modid.mixins.json`)
- Access widener file (if needed)
- Proper entrypoint registration
- Different event system architecture

## Advanced Workflows

### Large-Scale Code Search
```
Step 1: Index the version (one-time, enables fast search)
  → index_minecraft_version (version, mapping: "yarn")
Step 2: Fast full-text search
  → search_indexed (query: "entity damage", version, mapping: "yarn",
     types: ["method", "field"], limit: 100)
```

### Finding Mappings Between Systems
```
find_mapping (symbol: "a", version: "1.21.11", sourceMapping: "official", targetMapping: "yarn")
find_mapping (symbol: "class_1234", version: "1.21.11", sourceMapping: "intermediary", targetMapping: "yarn")
```

### Registry Data Analysis
```
get_registry_data (version, registry: "blocks" | "items" | "entities" | "biomes")
```

## Documentation Strategy

**Fabric Docs - Priority 1**（官方、维护中、版本特定）
**Minecraft Source - Priority 2**（看原版实现）
**Baritone Docs - Priority 3**（仅寻路/AI）

## Error Handling & Troubleshooting

**"Decompiled source not found"** → Run `decompile_minecraft_version` first
**"Mixin validation failed"** → Check target class exists; verify signatures; ensure yarn mappings
**"Documentation not found"** → Run `sync_fabric_docs`
**"Invalid mapping type"** → Fabric ALWAYS uses "yarn"
**"Path not found"** → Check WSL/Windows path format

**Validation Best Practices — ALWAYS validate before suggesting code is complete:**
- Mixins: `analyze_mixin`
- Access wideners: `validate_access_widener`
- Version compatibility: `compare_versions`
- Registry IDs: `get_registry_data`

## Critical Reminders

### ALWAYS Remember
1. **Fabric = yarn mappings** (consistent across all versions)
2. **Validate before declaring complete** (mixins, access wideners)
3. **Sync docs first** (prevent stale documentation)
4. **Check version compatibility** (especially for porting)
5. **Use appropriate search scope** (indexed vs direct)
6. **Provide validation commands** (so user can verify)

### NEVER Do
1. Don't suggest mojmap for Fabric development（legacy 语境；1.21.9+ 官方推荐 Mojang mappings，见 NoteGT docs/10）
2. Don't skip mixin validation
3. Don't assume classes exist without checking
4. Don't provide partial validation steps
5. Don't ignore version differences
6. Don't forget to sync documentation
7. Don't mix mapping types in same context

## Final Checklist for Mod Development

Before suggesting a solution is complete, verify:
- [ ] Documentation searched and referenced
- [ ] Target Minecraft version confirmed
- [ ] Correct mapping type used (yarn for Fabric)
- [ ] Source code examined for vanilla implementation
- [ ] Mixin validated
- [ ] Access widener validated (if used)
- [ ] Version compatibility checked
- [ ] Dependencies identified
- [ ] Testing instructions provided
- [ ] Potential conflicts noted

# Change Log

All notable changes to the "cobweb" Minecraft mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Crystal Nest Semantic Versioning](https://crystalnest.it/#/versioning).

## [Unreleased]

- **Miscellaneous changes**
  - Ported to 26.3

- **Registry changes**
  - Removed the Fabric-only `Register` and `RegisterProvider` API. Use `CobwebRegistry` on all platforms.
  - Changed `CobwebEntry` from a record to an abstract class extending `Holder.Reference` and implementing `Supplier`.
  - Removed vanilla `Holder` unsealing.
  - Fabric entries now retain native registered references, exposing registry keys, names, tags, components, and ownership.
  - Fixed NeoForge entry support for native holder delegation, registry codecs, and data maps.
  - Corrected entry equality and hashing to follow each loader's native reference-holder behavior, including symmetric comparisons between Fabric entries and their wrapped native holders.
  - Added identifier-aware registration factories.
  - Added item/block property suppliers and transformations, plus simple item/block helpers.
  - Added lazy block-item properties, custom block-item constructor overloads, and keyed-holder shortcuts.
  - `CobwebRegistry.ofEntityTypes()` now returns a specialized register with entity builder helpers.
  - Added `CobwebRegistry.ofDataComponents()` and a typed component builder helper for the standard data component registry.
  - Added registry-key access, tag-key creation helpers, and an unmodifiable live view of each register's declared entries, including unbound NeoForge entries.
  - Fixed generic and specialized registry factories returning incompatible cached instances when requested in a different order.
  - Fixed register-cache races during parallel NeoForge mod initialization.
  - Added specialized potion and creative mode tab registers with helpers for potion effects, shared translation suffixes, and native tab builders.
  - Removed the `ofMobEffects()` shortcut. Use `CobwebRegistry.of(Registries.MOB_EFFECT, modId)`.
  - Added native registry aliases with namespaced-path and full-identifier overloads. Aliases retain loader-native declaration timing and conflict handling.
  - Added Java-created custom registries with optional native numeric-ID synchronization and a default entry, duplicate-creation protection, and registration from multiple namespaces.
  - Added `CobwebRegistry.createWorldRegistry()` and `createReloadableRegistry()` for world-loaded and reloadable data-pack registries, with optional native network codecs for world registries. Clarified synchronization opt-in, codec reuse, and client decoding requirements.
  - Added `registry()` access to the full native static registry. NeoForge custom registries can be unavailable before its new-registry event; the creating register exposes its registry immediately.
  - Added explicit-key data component factories, including enchantment effect components and custom component registries. Specialized custom views share generic registrations and entry inspection.
  - Custom `CobwebRegister` implementations must now implement `registryKey()`, `registry()`, `addAlias(Identifier, Identifier)`, and `entries()`.

- **Resource pack changes**
  - Replaced the static and dynamic pack APIs with `DataPack.registrar()` and `AssetPack.registrar()`. Both support bundled files, generation callbacks, and stable selection IDs.
  - Added pack titles accepting a `Component` or a string translation key.
  - Added disabled-by-default, enabled-by-default, and always-active modes on both loaders, independently of pack source and position. Packs default to disabled, top placement, and movable position.
  - Added fixed top/bottom placement and preserved pack selections and ordering across restarts, including optional fixed packs and packs changed from always active to optional.
  - Texture overlays use Minecraft's alpha blending.
  - Fixed bundled pack overlays on Fabric and nested overlay roots on NeoForge, retaining native metadata and conditional overlay handling.
  - Pack metadata is read during native discovery, with Java overrides for description and formats. Generated files can override bundled base files while active overlays retain their native precedence.
  - Applied activation and initial placement to data-pack discovery and `/reload`, and prevented always-active data packs from being disabled or omitted during resource reloads.
  - Added native generation phases and stages, asynchronous callbacks, typed data and asset views, grouped sprites, and keyed sound definitions.
  - Added pack and mod requirements, optional pack dependency versions, and ordering between packs with generation callbacks.
  - Added selection-dependent titles, descriptions and icons, and native load completion observers.
  - Added opt-in callback caching, disk-backed streaming for large generated files, and inspection export for applied Cobweb packs.
  - Added ordered asset-pack delivery to Vanilla clients on dedicated servers, including optional server-side generation and configurable HTTP hosting.
  - Retry a failed server resource-pack download batch once within the existing login timeout, including when an earlier connection's download fails late.
  - Added file-based resource generation for local asset and data packs, with copying, JSON patching, texture rules, and inline JSON or text writes with optional destination checks. Conditional titles, descriptions, and icons can also be declared in pack metadata.
  - Added optional copying of resource sidecars in JSON rules and Java callbacks.
  - Added automatic caching for file rules and tracked resolved resource sidecars in cached generation.
  - Shared first-read resource snapshots across Java callbacks and JSON rules in the same stage, keeping cache fingerprints consistent with the bytes used for generation.
  - Enforced file-rule input limits during capture and cache validation, and released superseded generated backing after successful loads.
  - Fixed large additive-tag cache records, reused directory listings and immutable file digests during validation, and kept registry-independent codecs cacheable. Documented supported fingerprint values and warned about unsupported types.
  - Reported file-rule preparation failures to load observers and preserved reload cancellation when asset publication is enabled.
  - Queued bounded HTTP download bursts, stopped obsolete publication work cooperatively, and reused unchanged static archives. Rejected client mod requirements for server-delivered packs.
  - Cached discovery identities until repository refresh and separated generation planning, contribution merging, rule payloads, ownership, and delivery phases.
  - Organized the pack API into subpackages for generation, dependencies, presentation, and queries. Generation declarations are grouped by input, output, and lifecycle, and `PackActivation` lives in `pack.model`. Pack implementation lives alongside its feature under `api.pack`, with internal bridges marked explicitly and server delivery in `pack.delivery`.
  - Preserved resource capture limits through native DEBUG leak-detection streams and kept discovery identities separate for distinct packs with equal locations.

- **Configuration changes**
  - Common and server config registration now uses native `LOCAL` and `SYNCED` config types, respectively. Default filenames use `-local.toml` and `-synced.toml`, with per-world synced overrides in `syncedconfig`. This is due to upstream changes (NeoForge
    and FCAPI).

## [v1.4.0] - 2026/10/02

- Ported to 26.2.
- Unsealed vanilla `Holder`.

## [v1.4.0] - 2026/04/03

- Ported to 26.1 and 26.1.1.

## [v1.4.0] - 2025/12/20

- Ported to 1.21.11.

## [v1.4.0] - 2025/11/04

- Added `DynamicBlockEntityType`.
- Dropped support for Minecraft `<1.21`.

## [v1.3.4] - 2025/10/11

- Ported to 1.21.10.

## [v1.3.3] - 2025/07/21

- Ported to 1.21.8.

## [v1.3.3] - 2025/07/02

- Ported to 1.21.7.

## [v1.3.3] - 2025/06/20

- Ported to 1.21.6.

## [v1.3.3] - 2025/03/26

- Ported to 1.21.5.

## [v1.3.3] - 2025/03/07

- 1.21 and above only.
- Fixed a bug where `CobwebEntry`s wouldn't properly check whether a `Holder` matched.

## [v1.3.2] - 2025/02/12

- 1.21 and above only.
- Fixed mappings when importing Cobweb in a Fabric-Yarn development environment.

## [v1.3.1] - 2025/01/15

- 1.21 and above only.
- Cut down jar file size.

## [v1.0.1] - 2025/01/15

- 1.20.4 and below only.
- Cut down jar file size.

## [v1.3.0] - 2024/12/16

- Ported to 1.21.4.

## [v1.3.0] - 2024/12/14

- 1.21 and above only.
- Added new API to easily register, in common, built-in resource packs.
- Moved deeper into `dynamic` the unstable API for dynamic resource packs.

## [v1.2.2] - 2024/12/11

- 1.21.3 and above only.
- Added new method to register BlockItem subclasses while keeping their exact type, rather than forcing BlockItem class.

## [v1.2.1] - 2024/12/08

- 1.21.3 and above only.
- Fixed crash when using dynamic datapacks feature.

## [v1.2.0] - 2024/12/08

- 1.21.3 and above only.
- Added new API methods to more easily register blocks and items.

## [v1.1.4] - 2024/12/04

- Fixed a critical bug in 1.21.3.

## [v1.1.3] - 2024/11/29

- Ported to 1.21.3.

## [v1.1.3] - 2024/09/30

- 1.21 and above only.
- Fix support for 1.21.1.

## [v1.1.2] - 2024/07/14

- 1.21 and above only.
- Another fix on item tags in internal API for data packs.

## [v1.1.1] - 2024/07/14

- 1.21 and above only.
- Fixed item tags in internal API for data packs.

## [v1.1.0] - 2024/07/13

- 1.21 and above only.
- Changed registry methods to return `CobwebEntry` instead of a simple `Supplier`.

## [v1.0.0] - 2024/07/09

- Updated to 1.21
- Removed the Tool Tiers API due to Minecraft removing levels from tiers.

## [v1.0.0] - 2024/06/18

- Stable version release.

## [v0.5.9-beta] - 2024/06/18

- 1.18.2 only: Revert Mixin Squared update

## [v0.5.8-beta] - 2024/06/18

- 1.18.2 only: Update to the latest Mixin Squared dependency version.

## [v0.5.7-beta] - 2024/06/18

- 1.18.2 only: Revert Mixin Squared dependency version change.

## [v0.5.6-beta] - 2024/06/17

- 1.18.2 only: Changed Mixin Squared dependency.

## [v0.5.5-beta] - 2024/06/17

- Fixed accesswidener for Fabric.

## [v0.5.4-beta] - 2024/06/11

- Fixed dynamic datapacks for Fabric.

## [v0.5.3-beta] - 2024/06/11

- Dynamic datapacks for tags now build lazily.

## [v0.5.2-beta] - 2024/06/11

- Add dynamic datapacks for tags.

## [v0.5.1-beta] - 2024/06/09

- Exposed unified registering API.

## [v0.5.0-beta] - 2024/06/08

- Removed redundant methods in config helpers, namely the ones without the mod ID as parameter.
- Added unified registering API.

## [v0.4.0-beta] - 2024/06/08

- Added some Internal annotations.
- Added a few overloads for some `TierUtils` methods.
- 1.18.2 only: Integrate Fabric Polyfill backported event.

## Legacy

<details>
  <summary>Click to expand</summary>

### [1.18.2-0.0.3.7-beta] - 2024/05/30

- Backport to 1.18.2.

### [1.19.2-0.0.3.7-beta] - 2024/05/29

- Fix missing nightconfig dependency on dependants.

### [1.19.2-0.0.3.6-beta] - 2024/05/29

- Backport to 1.19.2.

### [1.19.4-0.0.3.6-beta] - 2024/05/28

- Backport to 1.19.4.

### [1.20.2-0.0.3.6-beta] - 2024/05/28

- Fix NeoForge build.

### [1.20.2-0.0.3.5-beta] - 2024/05/27

- Fix Forge distribution.

### [1.20.4-0.0.3.5-beta] - 2024/05/26

- Improve Forge distribution.

### [1.20.4-0.0.3.4-beta] - 2024/05/26

- Fix Forge distribution.

### [1.20.4-0.0.3.3-beta] - 2024/05/26

- Nothing new, just a test.

### [1.20.4-0.0.3.2-beta] - 2024/05/26

- Revert to working version.

### [1.20.4-0.0.3.1-beta] - 2024/05/26

- Attempt to fix Forge distribution, but instead broke everything.

### [1.20.4-0.0.3.0-beta] - 2024/05/18

- Fixed declared related dependencies on CurseForge.

### [1.20.4-0.0.2.6-alpha] - 2024/05/16

- Changed declared related dependencies.

### [1.20.4-0.0.2.5-alpha] - 2024/05/08

- Fixed NPE when using config with NeoForge.

### [1.20.4-0.0.2.4-alpha] - 2024/05/06

- Made FCAP an optional dependency in development environments too.

### [1.20.4-0.0.2.3-alpha] - 2024/05/06

- Moved the services into a more proper class.

### [1.20.4-0.0.2.2-alpha] - 2024/05/04

- Improved tool tiers API.
- Fixed rare NPE.

### [1.20.4-0.0.2.1-alpha] - 2024/05/04

- Improved tool tiers API.

### [1.20.4-0.0.2.0-alpha] - 2024/05/04

- Added tool tiers API.

### [1.20.4-0.0.1.1-alpha] - 2024/05/01

- Fixed Fabric jar.
- Updated in-game icons and readme.
- Made FCAP an actual optional dependency at runtime.

### [1.20.4-0.0.1.0-alpha] - 2024/04/30

- Added utility to retrieve in-game IDs.
- Added first draft of a unified configuration system.
- Added Fabric specific registering system.

</details>

[Unreleased]: https://github.com/crystal-nest/cobweb

[README]: https://github.com/crystal-nest/cobweb#readme

[v1.4.0]: https://github.com/crystal-nest/cobweb/releases?q=1.4.0

[v1.3.4]: https://github.com/crystal-nest/cobweb/releases?q=1.3.4

[v1.3.3]: https://github.com/crystal-nest/cobweb/releases?q=1.3.3

[v1.3.2]: https://github.com/crystal-nest/cobweb/releases?q=1.3.2

[v1.3.1]: https://github.com/crystal-nest/cobweb/releases?q=1.3.1

[v1.3.0]: https://github.com/crystal-nest/cobweb/releases?q=1.3.0

[v1.2.2]: https://github.com/crystal-nest/cobweb/releases?q=1.2.2

[v1.2.1]: https://github.com/crystal-nest/cobweb/releases?q=1.2.1

[v1.2.0]: https://github.com/crystal-nest/cobweb/releases?q=1.2.0

[v1.1.4]: https://github.com/crystal-nest/cobweb/releases?q=1.1.4

[v1.1.3]: https://github.com/crystal-nest/cobweb/releases?q=1.1.3

[v1.1.2]: https://github.com/crystal-nest/cobweb/releases?q=1.1.2

[v1.1.1]: https://github.com/crystal-nest/cobweb/releases?q=1.1.1

[v1.1.0]: https://github.com/crystal-nest/cobweb/releases?q=1.1.0

[v1.0.1]: https://github.com/crystal-nest/cobweb/releases?q=1.0.1

[v1.0.0]: https://github.com/crystal-nest/cobweb/releases?q=1.0.0

[v0.5.9-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.9-beta

[v0.5.8-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.8-beta

[v0.5.7-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.7-beta

[v0.5.6-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.6-beta

[v0.5.5-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.5-beta

[v0.5.4-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.4-beta

[v0.5.3-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.3-beta

[v0.5.2-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.2-beta

[v0.5.1-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.1-beta

[v0.5.0-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.5.0-beta

[v0.4.0-beta]: https://github.com/crystal-nest/cobweb/releases?q=0.4.0-beta

[1.18.2-0.0.3.7-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.18.2-0.0.3.7-beta

[1.19.2-0.0.3.7-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.19.2-0.0.3.7-beta

[1.19.2-0.0.3.6-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.19.2-0.0.3.6-beta

[1.19.4-0.0.3.6-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.19.4-0.0.3.6-beta

[1.20.2-0.0.3.6-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.2-0.0.3.6-beta

[1.20.2-0.0.3.5-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.2-0.0.3.5-beta

[1.20.4-0.0.3.5-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.3.5-beta

[1.20.4-0.0.3.4-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.3.4-beta

[1.20.4-0.0.3.3-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.3.3-beta

[1.20.4-0.0.3.2-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.3.2-beta

[1.20.4-0.0.3.1-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.3.1-beta

[1.20.4-0.0.3.0-beta]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.3.0-beta

[1.20.4-0.0.2.6-alpha]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.2.6-alpha

[1.20.4-0.0.2.5-alpha]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.2.5-alpha

[1.20.4-0.0.2.4-alpha]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.2.4-alpha

[1.20.4-0.0.2.3-alpha]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.2.3-alpha

[1.20.4-0.0.2.2-alpha]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.2.2-alpha

[1.20.4-0.0.2.1-alpha]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.2.1-alpha

[1.20.4-0.0.2.0-alpha]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.2.0-alpha

[1.20.4-0.0.1.1-alpha]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.1.1-alpha

[1.20.4-0.0.1.0-alpha]: https://github.com/crystal-nest/cobweb/releases/tag/v1.20.4-0.0.1.0-alpha

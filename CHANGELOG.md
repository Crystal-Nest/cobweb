# Change Log

All notable changes to the "cobweb" Minecraft mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Crystal Nest Semantic Versioning](https://crystalnest.it/#/versioning).

## [Unreleased]

- Nothing new.

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

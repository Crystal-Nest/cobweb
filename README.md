![Cobweb banner](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/cobweb/banner.png "Cobweb banner")

---

![Minecraft](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/minecraft/minecraft.svg "Minecraft")[![26.3](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/minecraft/26-3.svg "26.3")](https://modrinth.com/mod/cobweb/versions?g=26.3)![Separator](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/separator.svg)[![26.2](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/minecraft/26-2.svg "26.2")](https://modrinth.com/mod/cobweb/versions?g=26.2)![Separator](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/separator.svg)[![26.1.x](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/minecraft/26-1-x.svg "26.1.x")](https://modrinth.com/mod/cobweb/versions?g=26.1)

![Loader](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/loader/loader.svg "Loader")[![NeoForge](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/loader/neoforge.svg "NeoForge")](https://modrinth.com/mod/cobweb/versions?l=neoforge)![Separator](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/separator.svg)[![Fabric](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/loader/fabric.svg "Fabric")](https://modrinth.com/mod/cobweb/versions?l=fabric)

![Overlay](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/side/client-server.svg)

![Issues](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/github/issues.svg "Issues")[![GitHub](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/github/github.svg "GitHub")](https://github.com/crystal-nest/cobweb/issues)

---

## 📝 **Description**

**Cobweb** is **Crystal Nest's multiloader API** for modders and pack authors.  
Its Java API works directly in the **common module** of a multiloader project and in individual loader projects, bringing content registration and resource-pack creation into shared code.

Register your mod's content, define new systems through data packs, and build packs whose contents adapt to game data and available resources.  
Generate recipes for your mod's variants, create matching textures from existing assets, or use JSON files to let an ordinary pack generate and transform its own resources.

📚 See the **[wiki](https://github.com/Crystal-Nest/cobweb/wiki)** for setup and usage.

Mods using Cobweb's client features still require it on the client.

[![Minecraft Storage](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/cobweb/minecraft-storage-banner.png "Minecraft Storage")](https://minecraftstorage.com/mods/cobweb?ref=crystal-nest)

## ✨ **Features**

### ✅ **Common registration**

Declare items, blocks, entity types, potions, creative tabs, and data components directly from shared code.  
Specialized helpers handle builders, properties, and block items, while generic registers cover other game objects.

### 🗂️ **Custom registries**

Create registries for your own content, including data-pack registries whose entries are defined in JSON.  
Choose world-load or reloadable entries and opt into client synchronization for world registries.

### 📦 **Resource packs and data packs**

Combine bundled files with content generated while Minecraft loads your packs.  
Use existing resources and game data to generate tags, recipes, enchantments, models, textures, translations, and sound definitions.

Modders can use Java to decide what to create and how.  
Pack authors can add JSON rules to a normal folder or ZIP pack to copy files, edit JSON, write new content, and transform textures.  
For example, recolor an existing texture to match a new material or adapt a recipe supplied by another pack.

Give players selectable packs with custom titles, descriptions, and icons, declare dependencies on other packs or mods, and generate content at the right point during loading or reloading.  
Caching reuses eligible generated content, streamed files support large packs, and exports let you inspect the result.

Dedicated servers can offer selected resource packs as ordinary Minecraft downloads, including assets generated from the world's data.  
Players can receive those packs using an unmodified Minecraft client.

### 🔍 **Shared utilities**

Query loaders and installed mods, inspect pack selection and priority, transform images, and create block-entity types with flexible valid blocks.

## 📜 **License and right of use**

Feel free to use this mod for any mod, modpack, or video, just be sure to give credit and possibly link [here](https://github.com/crystal-nest/cobweb#readme).  
This project is published under the [Crystal Nest Community License v1](https://github.com/crystal-nest/cobweb/blob/master/LICENSE).

## ❤️ **Support the Nest**

<a href="https://crystalnest.it"><img alt="Crystal Nest Website" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/crystal-nest/pic512.png" width="14.286%"></a><a href="https://discord.gg/BP6EdBfAmt"><img alt="Discord" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/discord/discord512.png" width="14.286%"></a><a href="https://www.patreon.com/crystalspider"><img alt="Patreon" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/patreon/patreon512.png" width="14.286%"></a><a href="https://ko-fi.com/crystalspider"><img alt="Ko-fi" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/kofi/kofi512.png" width="14.286%"></a><a href="https://github.com/Crystal-Nest"><img alt="Our other projects" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/github/github512.png" width="14.286%"></a><a href="https://modrinth.com/organization/crystal-nest"><img alt="Modrinth" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/modrinth/modrinth512.png" width="14.286%"></a><a href="https://www.curseforge.com/members/crystalspider/projects"><img alt="CurseForge" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/curseforge/curseforge512.png" width="14.286%"></a>

[![Bisect Hosting](https://www.bisecthosting.com/partners/custom-banners/d559b544-474c-4109-b861-1b2e6ca6026a.webp "Bisect Hosting")](https://bisecthosting.com/crystalspider)

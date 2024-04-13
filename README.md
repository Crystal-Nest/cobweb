![Cobweb Mod Template banner](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/cobweb-mod-template/banner.png "Cobweb Mod Template banner")

---

![Minecraft](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/minecraft/minecraft.svg "Minecraft")[![1.18.2](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/minecraft/1-18-2.svg "1.18.2")](https://modrinth.com/mod/cobweb-mod-template/versions?g=1.18.2)

![Loader](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/loader/loader.svg "Loader")[![Forge](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/loader/forge.svg "Forge")](https://modrinth.com/mod/cobweb-mod-template/versions?l=forge)![Separator](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/separator.svg)[![Fabric](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/loader/fabric.svg "Fabric")](https://modrinth.com/mod/cobweb-mod-template/versions?l=fabric)

![Overlay](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/side/client-server.svg)

![Issues](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/github/issues.svg "Issues")[![GitHub](https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/github/github.svg "GitHub")](https://github.com/crystal-nest/cobweb-mod-template/issues)

---

## **Description**

Multiloader skeleton for Minecraft mods!  
Built on [Jared's MultiLoaderTemplate](https://github.com/jaredlll08/MultiLoader-Template), with the addition of:

- Tasks to publish on GitHub, Modrinth, and CurseForge.
- [Cobweb](https://modrinth.com/mod/cobweb) API dependency.
- A little bit more Javadoc.
- Code style changes.

## **Setup completion**

To complete the setup:

- Change the [Support us](#support-us) section and the banner link.
- Add your project CurseForge ID in the `gradle.properties`.
- Replace the placeholder values in `api-keys.properties`.
- Update the changelog with proper release notes.
- Run the task `common > Tasks > vanilla gradle > decompile`
- Run the task `forge > Tasks > forgegradle runs > genIntellijRuns`

## Removing Platforms and Loaders

While this template includes support for Fabric and Forge, you can easily remove support for the ones you don't need.  
This can be done by deleting the subproject folder and then removing it from the associated `include` in the `settings.gradle` file.

The same thing applies for the different publishing platforms, GitHub, Modrinth, and CurseForge.  
To remove support for the ones you don't need just remove the plugin declaration and use in the root `build.gradle`.

## **License and right of use**

Feel free to use this mod template for any mod, just be sure to give credit and possibly link [here](https://github.com/crystal-nest/cobweb-mod-template#readme).  
This project is published under the [GNU General Public License v3.0](https://github.com/crystal-nest/cobweb-mod-template/blob/master/LICENSE).

## **Support us**

<a href="https://crystalnest.it"><img alt="Crystal Nest Website" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/crystal-nest/pic512.png" width="14.286%"></a><a href="https://www.twitch.tv/crystal_spider_"><img alt="Twitch" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/twitch/twitch512.png" width="14.286%"></a><a href="https://www.patreon.com/crystalspider"><img alt="Patreon" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/patreon/patreon512.png" width="14.286%"></a><a href="https://ko-fi.com/crystalspider"><img alt="Ko-fi" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/kofi/kofi512.png" width="14.286%"></a><a href="https://github.com/Crystal-Nest"><img alt="Our other projects" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/github/github512.png" width="14.286%"><a href="https://modrinth.com/organization/crystal-nest"><img alt="Modrinth" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/modrinth/modrinth512.png" width="14.286%"></a><a href="https://www.curseforge.com/members/crystalspider/projects"><img alt="CurseForge" src="https://raw.githubusercontent.com/crystal-nest/mod-fancy-assets/main/curseforge/curseforge512.png" width="14.286%"></a>

[![Bisect Hosting](https://www.bisecthosting.com/partners/custom-banners/d559b544-474c-4109-b861-1b2e6ca6026a.webp "Bisect Hosting")](https://bisecthosting.com/crystalspider)

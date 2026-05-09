🌐 Biome Extractor

Biome Extractor is a multi-loader utility mod for Minecraft that allows players to seamlessly identify, extract, and visualize biome data in the world. Built natively for both Fabric and NeoForge, it provides powerful in-game UI tools and real-time 3D rendering to help you understand the exact boundaries of the biomes around you.

✨ Features

🔍 Smart Tooltips

Say goodbye to guessing! Biome Extractor automatically appends detailed biome data directly to your item tooltips. Hover over your extracted items to instantly see which environmental conditions and biome tags they are tied to.

🧊 The 3D Biome Matrix

Biome borders in Minecraft are actually calculated in 4x4x4 chunks. Biome Extractor includes a custom-built, highly optimized 3D rendering engine that draws a holographic matrix of these chunks directly into your world!

Center Focus: The exact 4x4x4 biome chunk you are currently standing inside is highlighted with a bold, glowing Neon Aqua outline.

Surrounding Grid: The neighboring chunks are rendered with thinner, semi-transparent lines, allowing you to see exactly where biome borders begin and end without cluttering your screen.

Translucency Support: The holographic grid dynamically renders after translucent terrain like water and glass, ensuring your view is never blocked when exploring oceans or building windows.

⌨️ Controls

Toggle Matrix Grid: Press B (Default)
(The keybind can be changed in the standard Minecraft Controls menu under the "Miscellaneous" category).

⚙️ Installation

For Fabric Players:

Install the Fabric Loader.

Install the Fabric API.

Drop the biomeextractor-fabric.jar into your mods folder.

For NeoForge Players:

Install NeoForge.

Drop the biomeextractor-neoforge.jar into your mods folder.

🤝 Compatibility

Because Biome Extractor utilizes modern, non-invasive rendering hooks (LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN and RenderLevelStageEvent), it is highly compatible with massive performance overhauls and rendering engines like Sodium, Lithium, C2ME, and More Culling.

📜 License

This project is open-source and licensed under the MIT License. You are free to view the source code, learn from the 3D rendering logic, and include this mod in any of your public or private modpacks!

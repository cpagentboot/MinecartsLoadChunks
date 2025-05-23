# Minecarts Load Chunks Mod

A **Fabric mod** that prevents minecarts from getting stuck at unloaded chunk borders by automatically loading chunks as the minecarts travel. It works with **all minecart types**: regular, chest, furnace, and hopper carts, ensuring smooth and uninterrupted transportation across your Minecraft world.

## Features
- **Automatic Chunk Loading:** Loads a **3x3 grid** of chunks around any moving minecart to prevent chunk border issues.
- **Supports All Minecart Types:** Works with **regular minecarts**, **chest minecarts**, **furnace minecarts**, and **hopper minecarts**.
- **Tick-Based Expiry:** Chunks remain loaded for **1 minute (1200 ticks)** after the minecart leaves.
- **Persistent Chunk Data:** Loaded chunks are saved on a **per-world basis** and restored when the server restarts, preventing stranded chunks.
- **Debug Messages:** Provides informative console output for tracking loaded, saved, and restored chunks.

## Installation
1. Install the **Fabric Loader** and **Fabric API**:
    - Download the [Fabric Installer](https://fabricmc.net/use/) and install the loader.
    - Place the [Fabric API](https://modrinth.com/mod/fabric-api) JAR into your `mods` folder.

2. Download the mod’s **JAR file**:
    - [Releases Page](#) (Add your link here)
    - Place the downloaded **JAR** into your `mods` folder:
      ```
      %AppData%\.minecraft\mods\  (Windows)
      ~/.minecraft/mods/          (Linux/Mac)
      ```

## Building from Source
If you want to build the mod yourself:

1. Clone this repository:
   ```bash
   git clone https://github.com/cpagentboot/MinecartsLoadChunks.git
   cd minecarts-load-chunks
   ```

2. Run **Gradle** to build the JAR:
   ```bash
   ./gradlew build   # Linux/Mac
   gradlew build     # Windows
   ```

3. The JAR will be generated in the `build/libs` directory.

## How It Works
- The mod scans for **all minecarts** (regular, chest, furnace, and hopper carts) every server tick.
- When a minecart is detected with **nonzero velocity**, it records the tick and forces a **3x3 grid** of chunks around the minecart’s position to be loaded.
- The chunks remain loaded for **1 minute (1200 ticks)** or until the minecart leaves the area.
- **Persistent storage:** The chunk data is saved to **each world’s save directory** and restored when the world is reloaded.

### Debug Output
You can see debug messages in the server or client console, such as:
- When a chunk is loaded:
  ```
  Minecart loaded chunk at (x, z) in world <world>
  ```
- When chunks are saved or restored:
  ```
  Saved 10 forceloaded chunks for world <world>
  Loaded 10 forceloaded chunks for world <world>
  ```

## Configuration
Currently, the mod has no configuration files. You can customize its behavior by modifying the source code in:
- `THIRTY_SECONDS` (600 ticks): The time window to detect minecart movement.
- `ONE_MINUTE` (1200 ticks): The chunk load duration.

## Contributing
Feel free to contribute to this project by:
- Reporting issues
- Submitting pull requests
- Requesting new features

## License
This mod is available under the **MIT License**. See [LICENSE](LICENSE) for details.

## Credits
- Developed by **Dylan**
- Special thanks to the Minecraft and Fabric communities for making modding such a joy.


package D7lan.minecartsloadchunks;

import net.minecraft.entity.EntityType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    public boolean loadChunks; //Weather or not the mod in entirety is active. set to false in order to disable mod.
    public boolean spamConsole;

    public int cartLoadDuration; //How long in seconds each chunk should remain loaded after a moving cart loads it.
    public int movementDuration; //How long after a cart stops moving should we continue to load it for?

    //True/false for each type of minecart
    public boolean loadMinecarts;
    public boolean loadFurnaceMinecarts;
    public boolean loadChestMinecarts;
    public boolean loadHopperMinecarts;
    public boolean loadTntMinecarts;
    public boolean loadCommandBlockMinecarts;
    // File location for the configuration file
    private static final String CONFIG_PATH = "config/minecartsloadchunks.json";

    /**
     * Constructor that sets default values.
     */
    public ModConfig() {
        // Set default values for the configuration options.
        this.loadChunks = true;
        this.spamConsole = false;
        this.cartLoadDuration = 30;      // default: 30 seconds
        this.movementDuration = 10;      // default: 10 seconds

        this.loadMinecarts = true;
        this.loadFurnaceMinecarts = true;
        this.loadChestMinecarts = true;
        this.loadHopperMinecarts = true;
        this.loadTntMinecarts = false;
        this.loadCommandBlockMinecarts = false;
    }

    /**
     * Loads the configuration from a JSON file.
     * If the file does not exist, a default configuration is created and saved.
     *
     * @return the loaded or newly created configuration.
     */
    public static ModConfig loadConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(CONFIG_PATH);

        // Ensure the config directory exists.
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        // If the configuration file does not exist, create a new one with default values.
        if (!configFile.exists()) {
            ModConfig defaultConfig = new ModConfig();
            defaultConfig.saveConfig();
            return defaultConfig;
        }

        // Otherwise, try to load the configuration from the file.
        try (FileReader reader = new FileReader(configFile)) {
            ModConfig config = gson.fromJson(reader, ModConfig.class);
            if (config == null) {
                // If the file was empty or invalid, create a default config.
                config = new ModConfig();
                config.saveConfig();
            }
            return config;
        } catch (IOException e) {
            e.printStackTrace();
            // If an error occurs, return a default configuration.
            ModConfig defaultConfig = new ModConfig();
            defaultConfig.saveConfig();
            return defaultConfig;
        }
    }

    /**
     * Saves the current configuration to a JSON file.
     */
    public void saveConfig() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(CONFIG_PATH);

        // Ensure the config directory exists.
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public EntityType<?>[] getMinecartTypes() {
        List<EntityType<?>> minecartTypes = new ArrayList<>();

        if (loadMinecarts) minecartTypes.add(EntityType.MINECART);
        if (loadChestMinecarts) minecartTypes.add(EntityType.CHEST_MINECART);
        if (loadFurnaceMinecarts) minecartTypes.add(EntityType.FURNACE_MINECART);
        if (loadHopperMinecarts) minecartTypes.add(EntityType.HOPPER_MINECART);
        if (loadTntMinecarts) minecartTypes.add(EntityType.TNT_MINECART);
        if (loadCommandBlockMinecarts) minecartTypes.add(EntityType.COMMAND_BLOCK_MINECART);

        return minecartTypes.toArray(new EntityType<?>[0]); // Convert List to Array
    }

}
package logan.smp;

import org.bukkit.Chunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public final class SimpleMetadataPersistence extends JavaPlugin implements Listener {

    private static SimpleMetadataPersistence instance;

    private static final File DATA_FILE = new File("plugins/smp/data.yml");
    private static YamlConfiguration config;

    private static int lastId = 0;

    @Override
    public void onEnable() {

        instance = this;

        config = getConfig();

        lastId = getLastId();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(getName() + " enabled.");
    }

    @Override
    public void onDisable() {

        getLogger().info(getName() + " disabled.");
    }

    public static SimpleMetadataPersistence getInstance() {
        return instance;
    }

    public YamlConfiguration getConfig() {
        return config = YamlConfiguration.loadConfiguration(DATA_FILE);
    }

    public void saveConfiguration() {

        try {
            config.save(DATA_FILE);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public int getLastId() {
        return getConfig().getInt("id", lastId);
    }

    /**
     * Attempts to create persistent metadata for this entity
     * by assigning it an id then creating a section for the id
     * in the configuration.
     *
     * @param entity
     * @return True if successful. False otherwise.
     */
    public boolean registerPersistentMetadata(Entity entity) {

        entity.setCustomName(lastId++ + ":" + entity.getCustomName());
        System.out.println("Custom name: " + entity.getCustomName());

        /* Save lastId to file */
        getConfig().set("id", lastId);
        saveConfiguration();

        return true;
    }

    /**
     * Deletes the configuration section of this entities id.
     *
     * @param entity
     * @return True if successful. False otherwise.
     */
    public boolean unregisterPersistentMetadata(Entity entity) {

        try {
            deleteSection(getEntityIdSection(entity));
        } catch (PersistentEntityMetadataException ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    private ConfigurationSection getEntityIdSection(Entity entity) throws PersistentEntityMetadataException {

        /* Check if the entity has a custom name */
        if (entity.getCustomName() == null || entity.getCustomName().equalsIgnoreCase(""))
            throw new PersistentEntityMetadataException("entity doesn't have a custom name");

        /* If the entity has a custom name, split the id from the custom name */
        String[] parts = entity.getCustomName().split(":");

        /* See if this entity is set up for persistent metadata */
        if (parts.length == 0 || parts[0].equalsIgnoreCase(""))
            throw new PersistentEntityMetadataException("entity doesn't have persistent metadata id");

        String id = entity.getCustomName().split(":")[0];

        /* Create meta-data file if one doesn't exist */
        YamlConfiguration dataConfig = getConfig();

        return !dataConfig.isConfigurationSection(id) ? dataConfig.createSection(id) : dataConfig.getConfigurationSection(id);
    }

    private void deleteSection(ConfigurationSection section) {

        getConfig().set(section.getName(), null);
        saveConfiguration();
    }

    private void saveSection(ConfigurationSection section) {

        ConfigurationSection newSection = getConfig().createSection(section.getName());

        section.getKeys(false).forEach(key -> {
            newSection.set(key, section.get(key));
        });

        saveConfiguration();
    }

    public void setMetadata(Entity entity, String key, Object value) {

        entity.setMetadata(key, new FixedMetadataValue(this, value));

        ConfigurationSection idSection;

        try {
            idSection = getEntityIdSection(entity);
        } catch (PersistentEntityMetadataException ex) {
            ex.printStackTrace();
            return;
        }

        idSection.set(key, value);

        saveSection(idSection);
    }

    public Object getMetadata(Entity entity, String key) {

        ConfigurationSection idSection;

        try {
            idSection = getEntityIdSection(entity);
        } catch (PersistentEntityMetadataException ex) {
            ex.printStackTrace();
            return null;
        }

        return idSection.get(key);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {

        Chunk chunk = event.getChunk();

        Entity[] entities = chunk.getEntities();

        Arrays.stream(entities).forEach(entity -> {

            ConfigurationSection idSection;

            try {
                idSection = getEntityIdSection(entity);
            } catch (PersistentEntityMetadataException ex) {
                ex.printStackTrace();
                return;
            }

            /* Load meta-data from id into entity */
            idSection.getKeys(false).forEach(key -> {
                MetadataValue metadataValue = new FixedMetadataValue(this, idSection.get(key));
                entity.setMetadata(key, metadataValue);
            });
        });
    }
}

final class PersistentEntityMetadataException extends Exception {

    PersistentEntityMetadataException(String message) {
        super(message);
    }
}

package fr.popo.skytycoon.player;

import fr.popo.skytycoon.SkyTycoonPlugin;
import fr.popo.skytycoon.schematic.SchematicLoader;
import fr.popo.skytycoon.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des îles utilisant le WorldManager pour la création du monde
 */
public class IslandManager {
    private final SkyTycoonPlugin plugin;
    private final WorldManager worldManager;
    private final SchematicLoader schematicLoader;
    private final Map<UUID, Island> islands = new ConcurrentHashMap<>();
    private final int spacing = 512; // distance entre centres  
    private final int islandSize = 64; // rayon carré utilisable
    private int allocationIndex = 0; // simple incrément pour grille

    public IslandManager(SkyTycoonPlugin plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.schematicLoader = new SchematicLoader(plugin);
    }

    public Island getOrCreate(UUID owner) {
        return getOrCreate(owner, null);
    }
    
    public Island getOrCreate(UUID owner, org.bukkit.entity.Player playerToTeleport) {
        Island existing = islands.get(owner);
        if (existing != null) return existing;
        
        // Vérifier que le monde est disponible
        if (!worldManager.isWorldAvailable()) {
            plugin.getLogger().warning("Le monde SkyTycoon n'est pas encore disponible pour créer une île");
            return null;
        }
        
        Island created = allocateIsland(owner);
        islands.put(owner, created);
        generateStarterPlatform(created, playerToTeleport);
        // Sauvegarde intelligente après création
        saveIslandsToFile();
        return created;
    }

    public Island getIsland(UUID owner) {
        return islands.get(owner);
    }
    
    public SchematicLoader getSchematicLoader() {
        return schematicLoader;
    }

    private Island allocateIsland(UUID owner) {
        // grille simple: index -> (xIndex, zIndex)
        int idx = allocationIndex++;
        int gridX = idx % 1000; // arbitraire large
        int gridZ = idx / 1000;
        int centerX = gridX * spacing;
        int centerZ = gridZ * spacing;
        
        return new Island(owner, worldManager.getWorldName(), centerX, centerZ, islandSize);
    }
    
    private void generateStarterPlatform(Island island, org.bukkit.entity.Player playerToTeleport) {
        World world = worldManager.getSkyTycoonWorld();
        if (world == null) {
            plugin.getLogger().warning("Impossible de générer la plateforme: monde indisponible");
            return;
        }
        
        // Vérifier le type de génération dans la configuration  
        String generationType = plugin.getConfig().getString("island.generation_type", "schematic");
        
        if ("schematic".equalsIgnoreCase(generationType)) {
            generateSchematicIsland(world, island, playerToTeleport);
        } else {
            plugin.getLogger().severe("Type de génération non supporté: " + generationType);
            plugin.getLogger().severe("Seul 'schematic' est supporté. Aucune île ne sera créée.");
        }
    }
    
    /**
     * Génère une île en utilisant une schématique
     */
    private void generateSchematicIsland(World world, Island island, org.bukkit.entity.Player playerToTeleport) {
        String schematicName = plugin.getConfig().getString("island.schematic_name", "desert");
        int schematicY = plugin.getConfig().getInt("island.schematic_y", 64);
        
        Location pasteLocation = new Location(world, island.centerX(), schematicY, island.centerZ());
        
        plugin.getLogger().info("Chargement de la schématique '" + schematicName + "' pour l'île à " + 
            island.centerX() + "," + island.centerZ());
        
        // Planifier le chargement de la schématique
        Bukkit.getRegionScheduler().run(plugin, pasteLocation, task -> {
            boolean success = schematicLoader.pasteSchematic(schematicName, world, pasteLocation);
            
            if (!success) {
                plugin.getLogger().severe("Échec du chargement de la schématique '" + schematicName + "'");
                plugin.getLogger().severe("Aucune île ne sera créée (mode strict : schématiques uniquement)");
            } else {
                plugin.getLogger().info("Schématique collée avec succès pour l'île à " + 
                    island.centerX() + "," + island.centerZ());
                
                // Téléporter le joueur vers l'île après placement de la schématique
                if (playerToTeleport != null) {
                    Location spawnLocation = island.spawnLocation(world);
                    if (spawnLocation != null) {
                        // Utiliser le scheduler régional de Folia avec la bonne signature
                        Bukkit.getRegionScheduler().runDelayed(plugin, spawnLocation, (scheduledTask) -> {
                            playerToTeleport.teleportAsync(spawnLocation);
                            playerToTeleport.sendMessage("§aBienvenue sur votre nouvelle île !");
                        }, 10L); // 0.5 seconde de délai
                    }
                }
            }
        });
    }

    /**
     * Supprime une île et nettoie la zone
     */
    public boolean disbandIsland(UUID owner) {
        Island island = islands.get(owner);
        if (island == null) {
            return false;
        }
        
        World world = worldManager.getSkyTycoonWorld();
        if (world == null) {
            plugin.getLogger().warning("Impossible de supprimer l'île: monde indisponible");
            return false;
        }
        
        // Supprimer l'île de la map
        islands.remove(owner);
        // Sauvegarde intelligente après suppression
        saveIslandsToFile();
        // Nettoyer la zone de l'île
        Location spawn = island.spawnLocation(world);
        if (spawn != null) {
            Bukkit.getRegionScheduler().run(plugin, spawn, task -> {
                clearIslandArea(world, island.centerX(), island.centerZ(), island.size());
                plugin.getLogger().info("Île supprimée et zone nettoyée à " + island.centerX() + "," + island.centerZ());
            });
        }
        
        return true;
    }
    
    /**
     * Nettoie complètement une zone d'île
     */
    private void clearIslandArea(World world, int centerX, int centerZ, int radius) {
        // Nettoyer une zone plus large que l'île pour être sûr
        int clearRadius = radius + 10;
        
        for (int x = centerX - clearRadius; x <= centerX + clearRadius; x++) {
            for (int z = centerZ - clearRadius; z <= centerZ + clearRadius; z++) {
                for (int y = 0; y <= 100; y++) { // Nettoyer de 0 à 100 de hauteur
                    world.setBlockData(x, y, z, Bukkit.createBlockData(Material.AIR));
                }
            }
        }
    }

    /**
     * Sauvegarde les îles dans un fichier YAML
     */
    public void saveIslandsToFile() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "islands_data.yml");
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        int i = 0;
        for (Map.Entry<UUID, Island> entry : islands.entrySet()) {
            Island island = entry.getValue();
            String key = "islands." + i;
            yaml.set(key + ".owner", island.owner().toString());
            yaml.set(key + ".world", island.worldName());
            yaml.set(key + ".centerX", island.centerX());
            yaml.set(key + ".centerZ", island.centerZ());
            yaml.set(key + ".size", island.size());
            // Stockage
            Map<String, Integer> storageMap = new java.util.HashMap<>();
            for (Map.Entry<org.bukkit.Material, Integer> st : island.getStorage().entrySet()) {
                storageMap.put(st.getKey().name(), st.getValue());
            }
            yaml.set(key + ".storage", storageMap);
            i++;
        }
        try {
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la sauvegarde des îles: " + e.getMessage());
        }
    }

    /**
     * Charge les îles depuis un fichier YAML
     */
    public void loadIslandsFromFile() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "islands_data.yml");
        if (!file.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("islands");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String ownerStr = section.getString(key + ".owner");
            String worldName = section.getString(key + ".world");
            int centerX = section.getInt(key + ".centerX");
            int centerZ = section.getInt(key + ".centerZ");
            int size = section.getInt(key + ".size", 64);
            org.bukkit.configuration.ConfigurationSection storageSection = section.getConfigurationSection(key + ".storage");
            Map<String, Object> storageMap = storageSection != null ? storageSection.getValues(false) : new java.util.HashMap<>();
            if (ownerStr == null || worldName == null) continue;
            Island island = new Island(java.util.UUID.fromString(ownerStr), worldName, centerX, centerZ, size);
            // Restaurer le stockage
            for (Map.Entry<String, Object> st : storageMap.entrySet()) {
                try {
                    org.bukkit.Material mat = org.bukkit.Material.valueOf(st.getKey());
                    int amount = Integer.parseInt(st.getValue().toString());
                    island.getStorage().put(mat, amount);
                } catch (Exception ignored) {}
            }
            islands.put(island.owner(), island);
        }
    }

    public void shutdown() {
        islands.clear();
    }
}

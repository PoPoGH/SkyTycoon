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

    public void shutdown() {
        islands.clear();
    }
}

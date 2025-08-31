package fr.popo.skytycoon.world;

import fr.popo.skytycoon.SkyTycoonPlugin;
import net.kyori.adventure.key.Key;
import net.thenextlvl.worlds.api.WorldsProvider;
import net.thenextlvl.worlds.api.level.Level;
import net.thenextlvl.worlds.api.preset.Preset;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;

/**
 * Gestionnaire dédié pour la création et configuration du monde SkyTycoon
 * Utilise exclusivement l'API Worlds pour une gestion propre et compatible Folia
 */
public class WorldManager {
    private final SkyTycoonPlugin plugin;
    private final WorldsProvider worldsProvider;
    private final String worldName = "skytycoon";
    private final Key worldKey = Key.key("skytycoon", "islands");
    private World skyTycoonWorld;

    public WorldManager(SkyTycoonPlugin plugin) {
        this.plugin = plugin;
        this.worldsProvider = Bukkit.getServicesManager().load(WorldsProvider.class);
        
        if (worldsProvider == null) {
            throw new IllegalStateException("Worlds API non disponible ! Assurez-vous que le plugin Worlds est installé.");
        }
        
        plugin.getLogger().info("WorldManager initialisé avec l'API Worlds");
    }

    /**
     * Initialise le monde SkyTycoon de manière asynchrone
     * @return CompletableFuture qui se résout avec le monde créé/chargé
     */
    public CompletableFuture<World> initializeWorld() {
        // Vérifier si le monde existe déjà par nom
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            skyTycoonWorld = existingWorld;
            plugin.getLogger().info("Monde SkyTycoon existant chargé par nom: " + worldName);
            return CompletableFuture.completedFuture(existingWorld);
        }

        // Vérifier si le monde existe par clé via l'API Worlds
        try {
            // Essayer de charger le monde par clé si il existe déjà
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().equals(worldName) || world.getKey().equals(worldKey)) {
                    skyTycoonWorld = world;
                    plugin.getLogger().info("Monde SkyTycoon existant trouvé par clé: " + world.getName());
                    return CompletableFuture.completedFuture(world);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la vérification des mondes existants: " + e.getMessage());
        }

        plugin.getLogger().info("Création du monde SkyTycoon avec l'API Worlds...");
        
        return createWorldWithWorldsAPI()
            .thenApply(world -> {
                skyTycoonWorld = world;
                plugin.getLogger().info("Monde SkyTycoon void créé avec succès!");
                plugin.getLogger().info("Note: Les GameRules doivent être configurées manuellement via console/config serveur");
                return world;
            })
            .exceptionally(throwable -> {
                // Si le monde existe déjà, essayer de le récupérer
                if (throwable.getCause() instanceof IllegalArgumentException && 
                    throwable.getMessage().contains("already exists")) {
                    plugin.getLogger().info("Le monde existe déjà, tentative de récupération...");
                    
                    // Essayer de trouver le monde existant
                    for (World world : Bukkit.getWorlds()) {
                        if (world.getName().equals(worldName) || world.getKey().equals(worldKey)) {
                            plugin.getLogger().info("Monde existant récupéré: " + world.getName());
                            return world;
                        }
                    }
                    
                    plugin.getLogger().warning("Monde supposé existant non trouvé, impossible de récupérer");
                }
                
                plugin.getLogger().severe("Échec de création du monde SkyTycoon: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
    }

    private CompletableFuture<World> createWorldWithWorldsAPI() {
        try {
            // Créer un preset void (complètement vide)
            Preset voidPreset = new Preset("the_void")
                .features(false)
                .decoration(false)
                .lakes(false)
                .structures(Collections.emptySet())  // Set vide pour les structures
                .layers(Collections.emptySet());     // Set vide pour les layers
            
            // Utiliser la bonne API selon la documentation
            Level level = worldsProvider.levelBuilder(Path.of(worldName))
                .key(worldKey)
                .name("SkyTycoon Islands")
                .seed(12345L)
                .hardcore(false)
                .structures(false)
                .preset(voidPreset) // Utiliser le preset void créé
                .build();

            // Créer le monde de manière asynchrone
            return level.createAsync()
                .thenApply(world -> {
                    plugin.getLogger().info("Monde void créé avec succès: " + world.getName());
                    return world;
                });
                
        } catch (Exception e) {
            CompletableFuture<World> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * @return Le monde SkyTycoon ou null s'il n'est pas disponible
     */
    public World getSkyTycoonWorld() {
        return skyTycoonWorld;
    }

    /**
     * @return Le nom du monde SkyTycoon
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * @return La clé du monde SkyTycoon
     */
    public Key getWorldKey() {
        return worldKey;
    }

    /**
     * @return true si le monde SkyTycoon est disponible
     */
    public boolean isWorldAvailable() {
        return skyTycoonWorld != null;
    }

    /**
     * Décharge le monde si nécessaire
     */
    public void shutdown() {
        if (skyTycoonWorld != null) {
            plugin.getLogger().info("Déchargement du monde SkyTycoon...");
            // L'API Worlds gère le déchargement automatiquement
            skyTycoonWorld = null;
        }
    }
}

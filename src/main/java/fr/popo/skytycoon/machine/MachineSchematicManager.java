package fr.popo.skytycoon.machine;

import fr.popo.skytycoon.SkyTycoonPlugin;
import fr.popo.skytycoon.schematic.SchematicLoader;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire des schématiques pour les machines
 * Remplace le placement de blocs uniques par des structures complètes
 */
public class MachineSchematicManager {
    
    private final SkyTycoonPlugin plugin;
    private final SchematicLoader schematicLoader;
    
    // Cache des schématiques chargées
    private final Map<MachineType, String> schematicFiles = new HashMap<>();
    
    public MachineSchematicManager(SkyTycoonPlugin plugin) {
        this.plugin = plugin;
        this.schematicLoader = new SchematicLoader(plugin);
        
        // Définir les fichiers de schématiques pour chaque type de machine
        initializeSchematicFiles();
    }
    
    /**
     * Initialise les fichiers de schématiques pour chaque type de machine
     */
    private void initializeSchematicFiles() {
        schematicFiles.put(MachineType.BASIC_MINER, "basic_miner.schem");
        schematicFiles.put(MachineType.WOOD_CUTTER, "wood_cutter.schem");
        schematicFiles.put(MachineType.CROP_FARM, "crop_farm.schem");
        schematicFiles.put(MachineType.MOB_GRINDER, "mob_grinder.schem");
        schematicFiles.put(MachineType.SELL_STATION, "sell_station.schem");
    }
    
    /**
     * Place une schématique de machine à la location donnée
     */
    public boolean placeMachineSchematic(Location location, MachineType type) {
        String schematicFile = schematicFiles.get(type);
        if (schematicFile == null) {
            System.out.println("[SkyTycoon] Aucune schématique définie pour " + type);
            return placeSimpleMachine(location, type); // Fallback
        }
        
        try {
            System.out.println("[SkyTycoon] Placement de la schématique " + schematicFile + " pour " + type);
            
            // Charger et placer la schématique
            boolean success = schematicLoader.pasteSchematic(
                schematicFile.replace(".schem", ""), 
                location.getWorld(), 
                location
            );
            
            if (success) {
                System.out.println("[SkyTycoon] Schématique " + schematicFile + " placée avec succès");
                return true;
            } else {
                System.out.println("[SkyTycoon] Échec du placement de la schématique, utilisation du fallback");
                return placeSimpleMachine(location, type);
            }
            
        } catch (Exception e) {
            System.out.println("[SkyTycoon] Erreur lors du placement de la schématique: " + e.getMessage());
            return placeSimpleMachine(location, type);
        }
    }
    
    /**
     * Méthode de fallback - place un seul bloc comme avant
     */
    private boolean placeSimpleMachine(Location location, MachineType type) {
        Material material;
        
        switch (type) {
            case BASIC_MINER:
                material = Material.COBBLESTONE;
                break;
            case WOOD_CUTTER:
                material = Material.OAK_LOG;
                break;
            case CROP_FARM:
                material = Material.HAY_BLOCK;
                break;
            case MOB_GRINDER:
                material = Material.REDSTONE_BLOCK;
                break;
            case SELL_STATION:
                material = Material.EMERALD_BLOCK;
                break;
            default:
                material = Material.STONE;
                break;
        }
        
        location.getBlock().setType(material);
        System.out.println("[SkyTycoon] Machine simple placée: " + material + " à " + location);
        return true;
    }
    
    /**
     * Supprime une schématique de machine (remet de l'air)
     */
    public void removeMachineSchematic(Location location, MachineType type) {
        String schematicFile = schematicFiles.get(type);
        
        if (schematicFile != null) {
            // Pour une vraie suppression de schématique, il faudrait connaître sa taille
            // Pour l'instant, on supprime juste le bloc principal
            removeSimpleMachine(location);
        } else {
            removeSimpleMachine(location);
        }
    }
    
    /**
     * Supprime une machine simple (un seul bloc)
     */
    private void removeSimpleMachine(Location location) {
        location.getBlock().setType(Material.AIR);
        System.out.println("[SkyTycoon] Machine supprimée à " + location);
    }
    
    /**
     * Vérifie si une location contient une machine
     */
    public boolean isMachineLocation(Location location) {
        Block block = location.getBlock();
        Material type = block.getType();
        
        // Vérifier si le bloc correspond à un type de machine connu
        return type == Material.COBBLESTONE || // BASIC_MINER
               type == Material.OAK_LOG ||     // WOOD_CUTTER
               type == Material.HAY_BLOCK ||   // CROP_FARM
               type == Material.REDSTONE_BLOCK || // MOB_GRINDER
               type == Material.EMERALD_BLOCK; // SELL_STATION
    }
    
    /**
     * Détermine le type de machine basé sur le bloc
     */
    public MachineType getMachineTypeFromBlock(Location location) {
        Block block = location.getBlock();
        Material type = block.getType();
        
        switch (type) {
            case COBBLESTONE:
                return MachineType.BASIC_MINER;
            case OAK_LOG:
                return MachineType.WOOD_CUTTER;
            case HAY_BLOCK:
                return MachineType.CROP_FARM;
            case REDSTONE_BLOCK:
                return MachineType.MOB_GRINDER;
            case EMERALD_BLOCK:
                return MachineType.SELL_STATION;
            default:
                return null;
        }
    }
    
    /**
     * Obtient la taille approximative d'une schématique de machine
     */
    public int getMachineSize(MachineType type) {
        // Tailles approximatives pour les différents types de machines
        switch (type) {
            case BASIC_MINER:
                return 3; // 3x3
            case WOOD_CUTTER:
                return 3; // 3x3
            case CROP_FARM:
                return 5; // 5x5 pour les champs
            case MOB_GRINDER:
                return 7; // 7x7 pour les spawners
            case SELL_STATION:
                return 5; // 5x5
            default:
                return 1; // 1x1 pour les machines simples
        }
    }
    
    /**
     * Vérifie si il y a assez d'espace pour placer une machine
     */
    public boolean hasSpaceForMachine(Location location, MachineType type) {
        int size = getMachineSize(type);
        int radius = size / 2;
        
        // Vérifier que tous les blocs dans la zone sont libres (air ou remplaçables)
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y < 3; y++) { // Hauteur maximale de 3 blocs
                for (int z = -radius; z <= radius; z++) {
                    Location checkLoc = location.clone().add(x, y, z);
                    Material blockType = checkLoc.getBlock().getType();
                    
                    // Si le bloc n'est pas de l'air ou un bloc remplaçable
                    if (!blockType.isAir() && 
                        blockType != Material.SHORT_GRASS && 
                        blockType != Material.TALL_GRASS &&
                        blockType != Material.WATER) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
}

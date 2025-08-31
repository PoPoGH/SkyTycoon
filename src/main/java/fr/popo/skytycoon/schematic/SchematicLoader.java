package fr.popo.skytycoon.schematic;

import fr.popo.skytycoon.SkyTycoonPlugin;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Gestionnaire de chargement des schématiques .schem
 */
public class SchematicLoader {
    
    private final SkyTycoonPlugin plugin;
    private final File schematicFolder;
    
    public SchematicLoader(SkyTycoonPlugin plugin) {
        this.plugin = plugin;
        this.schematicFolder = new File(plugin.getDataFolder(), "schematics");
        
        // Créer le dossier des schématiques s'il n'existe pas
        if (!schematicFolder.exists()) {
            schematicFolder.mkdirs();
            plugin.getLogger().info("Dossier schematics créé: " + schematicFolder.getAbsolutePath());
        }
        
        plugin.getLogger().info("Lecteur de schématiques .schem basique initialisé");
    }
    
    /**
     * Liste les schématiques disponibles
     */
    public String[] getAvailableSchematics() {
        File[] files = schematicFolder.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".schem"));
        
        if (files == null) return new String[0];
        
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName().replaceAll("\\.schem$", "");
        }
        return names;
    }
    
    /**
     * Charge et colle une schématique .schem à la location donnée
     */
    public boolean pasteSchematic(String schematicName, World world, Location location) {
        File schematicFile = findSchematicFile(schematicName);
        if (schematicFile == null) {
            plugin.getLogger().severe("Schématique .schem non trouvée: " + schematicName);
            plugin.getLogger().severe("Aucune île ne sera créée - veuillez placer le fichier " + schematicName + ".schem dans le dossier schematics");
            return false;
        }
        
        try {
            plugin.getLogger().info("Chargement de la schématique .schem: " + schematicName);
            
            // Lecture du fichier .schem avec NBT-API (nouvelle API)
            ReadableNBT root = NBT.readFile(schematicFile);
            
            // Vérification du format Sponge Schematic
            if (!root.hasTag("Version") || root.getInteger("Version") != 2) {
                plugin.getLogger().severe("Format de schématique non supporté. Seul Sponge Schematic v2 est supporté.");
                return false;
            }
            
            // Lecture des dimensions
            short width = root.getShort("Width");
            short height = root.getShort("Height");
            short length = root.getShort("Length");
            
            plugin.getLogger().info("Dimensions de la schématique: " + width + "x" + height + "x" + length);
            
            // Lecture de la palette des blocs
            ReadableNBT palette = root.getCompound("Palette");
            if (palette == null) {
                plugin.getLogger().severe("Palette manquante dans la schématique");
                return false;
            }
            
            String[] blockTypes = new String[palette.getKeys().size()];
            for (String blockName : palette.getKeys()) {
                int index = palette.getInteger(blockName);
                blockTypes[index] = blockName;
            }
            
            // Lecture des données de blocs
            byte[] blockData = root.getByteArray("BlockData");
            if (blockData == null) {
                plugin.getLogger().severe("Données de blocs manquantes dans la schématique");
                return false;
            }
            
            // Placement des blocs
            int baseX = location.getBlockX();
            int baseY = location.getBlockY();
            int baseZ = location.getBlockZ();
            
            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        if (index < blockData.length) {
                            int blockId = blockData[index] & 0xFF;
                            if (blockId < blockTypes.length) {
                                String blockType = blockTypes[blockId];
                                Material material = Material.matchMaterial(blockType.split("\\[")[0]);
                                
                                if (material != null && material != Material.AIR) {
                                    world.setBlockData(
                                        baseX + x - width/2, 
                                        baseY + y, 
                                        baseZ + z - length/2, 
                                        material.createBlockData()
                                    );
                                }
                            }
                        }
                        index++;
                    }
                }
            }
            
            plugin.getLogger().info("Schématique '" + schematicName + "' chargée avec succès!");
            return true;
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la lecture du fichier " + schematicName, e);
            return false;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du chargement de la schématique '" + schematicName + "': " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Trouve le fichier de schématique par nom
     */
    private File findSchematicFile(String name) {
        // Essayer avec l'extension .schem
        File schem = new File(schematicFolder, name + ".schem");
        if (schem.exists()) return schem;
        
        // Essayer le nom exact
        File exact = new File(schematicFolder, name);
        if (exact.exists()) return exact;
        
        return null;
    }
}

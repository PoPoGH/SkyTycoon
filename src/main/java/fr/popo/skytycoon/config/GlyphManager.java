package fr.popo.skytycoon.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire des glyphs Unicode pour les machines, produits et tiers
 */
public class GlyphManager {
    private final Map<String, String> machineGlyphs = new HashMap<>();
    private final Map<String, String> productGlyphs = new HashMap<>();
    private final Map<String, Map<Integer, String>> machineTierGlyphs = new HashMap<>();
    private Map<Integer, String> progressBarGlyphs;
    private final File dataFolder;

    public GlyphManager(File dataFolder) {
        this.dataFolder = dataFolder;
        File glyphFile = new File(dataFolder, "glyphs.yml");
        if (!glyphFile.exists()) {
            try {
                dataFolder.mkdirs();
                java.nio.file.Files.copy(
                    getClass().getResourceAsStream("/glyphs.yml"),
                    glyphFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            } catch (Exception e) {
                System.err.println("Impossible de créer glyphs.yml par défaut : " + e.getMessage());
            }
        }
        loadGlyphs(glyphFile);
    }

    private void loadGlyphs(File file) {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // Machines
        if (yaml.isConfigurationSection("machines")) {
            var machines = yaml.getConfigurationSection("machines");
            for (String key : machines.getKeys(false)) {
                if (machines.isString(key)) {
                    machineGlyphs.put(key, machines.getString(key));
                } else if (machines.isConfigurationSection(key) && key.endsWith("_tiers")) {
                    Map<Integer, String> tiers = new HashMap<>();
                    var section = machines.getConfigurationSection(key);
                    for (String tierKey : section.getKeys(false)) {
                        try {
                            int tier = Integer.parseInt(tierKey);
                            tiers.put(tier, section.getString(tierKey));
                        } catch (NumberFormatException ignored) {}
                    }
                    machineTierGlyphs.put(key.replace("_tiers", ""), tiers);
                }
            }
        }
        // Produits
        if (yaml.isConfigurationSection("products")) {
            var products = yaml.getConfigurationSection("products");
            for (String key : products.getKeys(false)) {
                if (products.isString(key)) {
                    productGlyphs.put(key, products.getString(key));
                }
            }
        }
    }

    /**
     * Glyph pour une machine (ex: basic_miner)
     */
    public String getMachineGlyph(String machineId) {
        return machineGlyphs.getOrDefault(machineId, "");
    }

    /**
     * Glyph pour un produit (ex: cobblestone)
     */
    public String getProductGlyph(String productId) {
        return productGlyphs.getOrDefault(productId, "");
    }

    /**
     * Glyph pour un tier de machine (ex: basic_miner, 3)
     */
    public String getMachineTierGlyph(String machineId, int tier) {
        Map<Integer, String> tiers = machineTierGlyphs.get(machineId);
        if (tiers != null) {
            return tiers.getOrDefault(tier, "");
        }
        return "";
    }

    public String getProgressBarGlyph(int step) {
        int idx = Math.max(1, Math.min(8, step));
        if (progressBarGlyphs == null) {
            loadProgressBarGlyphs();
        }
        return progressBarGlyphs.getOrDefault(idx, "|");
    }

    private void loadProgressBarGlyphs() {
        progressBarGlyphs = new HashMap<>();
        File file = new File(dataFolder, "glyphs.yml");
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.isConfigurationSection("progress_bar")) {
            var section = yaml.getConfigurationSection("progress_bar");
            for (String key : section.getKeys(false)) {
                try {
                    int idx = Integer.parseInt(key);
                    progressBarGlyphs.put(idx, section.getString(key));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public static void copySchematicIfNotExists(File dataFolder, String schematicName) {
        File schemDir = new File(dataFolder, "schematics");
        File schemFile = new File(schemDir, schematicName);
        if (!schemFile.exists()) {
            try {
                schemDir.mkdirs();
                java.nio.file.Files.copy(
                    GlyphManager.class.getResourceAsStream("/schematics/" + schematicName),
                    schemFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            } catch (Exception e) {
                System.err.println("Impossible de copier la schematic par défaut : " + e.getMessage());
            }
        }
    }
}

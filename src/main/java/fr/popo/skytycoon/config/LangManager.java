package fr.popo.skytycoon.config;

import fr.popo.skytycoon.SkyTycoonPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire des messages avec support MiniMessage
 */
public class LangManager {
    
    private final SkyTycoonPlugin plugin;
    private final MiniMessage miniMessage;
    private YamlConfiguration langConfig;
    private final Map<String, String> cachedMessages = new HashMap<>();
    private GlyphManager glyphManager;
    
    public LangManager(SkyTycoonPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        loadLangFile();
    }
    
    /**
     * Charge le fichier lang.yml
     */
    private void loadLangFile() {
        File langFile = new File(plugin.getDataFolder(), "lang.yml");
        
        // Créer le fichier s'il n'existe pas
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
            plugin.getLogger().info("Fichier lang.yml créé avec les messages par défaut");
        }
        
        // Charger la configuration
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        plugin.getLogger().info("Messages chargés depuis lang.yml avec support MiniMessage");
    }
    
    /**
     * Recharge le fichier lang.yml
     */
    public void reload() {
        cachedMessages.clear();
        loadLangFile();
        plugin.getLogger().info("Messages rechargés depuis lang.yml");
    }
    
    /**
     * Obtient un message brut depuis le fichier lang
     */
    public String getRawMessage(String path) {
        return getRawMessage(path, "Message manquant: " + path);
    }
    
    /**
     * Obtient un message brut avec fallback
     */
    public String getRawMessage(String path, String fallback) {
        // Vérifier le cache d'abord
        if (cachedMessages.containsKey(path)) {
            return cachedMessages.get(path);
        }
        
        // Charger depuis la config
        String message = langConfig.getString(path, fallback);
        cachedMessages.put(path, message);
        return message;
    }
    
    /**
     * Obtient un Component MiniMessage
     */
    public Component getMessage(String path) {
        return getMessage(path, new HashMap<>());
    }
    
    /**
     * Obtient un Component MiniMessage avec placeholders
     */
    public Component getMessage(String path, Map<String, String> placeholders) {
        String rawMessage = getRawMessage(path);
        
        // Remplacer les placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rawMessage = rawMessage.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        try {
            return miniMessage.deserialize(rawMessage);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du parsing MiniMessage pour: " + path + " - " + e.getMessage());
            return Component.text(rawMessage); // Fallback en texte simple
        }
    }
    
    /**
     * Format spécial pour les hologrammes de machines
     */
    public Component getMachineHologramName(String machineId, Integer tier) {
        // Essayer d'abord le nom spécifique
        String specificPath = "machine_names." + machineId + ".name";
        String message;
        if (langConfig.contains(specificPath)) {
            message = getRawMessage(specificPath);
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("name", machineId.replace("_", " "));
            message = getRawMessage("holograms.machine.name");
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        // Remplacement glyphes dynamiques
        message = replaceGlyphPlaceholders(message, machineId, null, tier);
        try {
            return miniMessage.deserialize(message);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du parsing MiniMessage pour: " + machineId + " - " + e.getMessage());
            return Component.text(message);
        }
    }

    public Component getMachineHologramName(String machineId) {
        return getMachineHologramName(machineId, null);
    }
    
    /**
     * Format pour le contenu des machines
     */
    public Component getMachineHologramContent(int amount, String product, String machineId, Integer tier) {
        String specificPath = "machine_names." + machineId + ".content";
        String message;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("product", product);
        // Suppression de l'icône et de la couleur du produit
        if (machineId != null && langConfig.contains(specificPath)) {
            message = getRawMessage(specificPath);
        } else {
            message = getRawMessage("holograms.machine.content");
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        message = replaceGlyphPlaceholders(message, machineId, product, tier);
        try {
            return miniMessage.deserialize(message);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du parsing MiniMessage pour: " + product + " - " + e.getMessage());
            return Component.text(message);
        }
    }
    
    public Component getMachineHologramContent(int amount, String product) {
        return getMachineHologramContent(amount, product, null, null);
    }
    /**
     * Message de démarrage des machines
     */
    public Component getMachineStartingMessage() {
        return getMessage("holograms.machine.starting");
    }
    
    /**
     * Message de stockage plein
     */
    public Component getMachineStorageFullMessage() {
        return getMessage("holograms.machine.storage_full");
    }
    
    /**
     * Crée une barre de progression
     */
    public Component createProgressBar(int current, int max) {
        if (max <= 0) return Component.text("N/A");
        int percentage = Math.min(100, (current * 100) / max);
        int step = Math.max(1, Math.min(8, (percentage + 12) / 13)); // 0-12:1, 13-25:2, ..., 88-100:8
        String glyph = glyphManager != null ? glyphManager.getProgressBarGlyph(step) : "|";
        String bar = glyph;
        String colorPath;
        if (percentage >= 80) colorPath = "progress_bars.colors.full";
        else if (percentage >= 60) colorPath = "progress_bars.colors.high";
        else if (percentage >= 30) colorPath = "progress_bars.colors.medium";
        else colorPath = "progress_bars.colors.low";
        String color = getRawMessage(colorPath, "<white>");
        String format = getRawMessage("progress_bars.format", "{color}{bar} <white>{percentage}%</white>");
        format = format.replace("{bar}", bar).replace("{percentage}", String.valueOf(percentage)).replace("{color}", color);
        return miniMessage.deserialize(format);
    }
    
    /**
     * Message simple avec placeholders
     */
    public Component getMessageWithPlaceholders(String path, String... placeholders) {
        Map<String, String> placeholderMap = new HashMap<>();
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            placeholderMap.put(placeholders[i], placeholders[i + 1]);
        }
        return getMessage(path, placeholderMap);
    }

    public void setGlyphManager(GlyphManager glyphManager) {
        this.glyphManager = glyphManager;
    }

    public String getMachineGlyph(String machineId) {
        return glyphManager != null ? glyphManager.getMachineGlyph(machineId) : "";
    }
    public String getProductGlyph(String productId) {
        return glyphManager != null ? glyphManager.getProductGlyph(productId) : "";
    }
    public String getMachineTierGlyph(String machineId, int tier) {
        return glyphManager != null ? glyphManager.getMachineTierGlyph(machineId, tier) : "";
    }

    /**
     * Remplace dynamiquement les placeholders de glyphes dans le message fourni.
     * Placeholders supportés : {glyph}, {product_glyph}, {tier_glyph}
     * @param message Le message à traiter
     * @param machineId L'ID de la machine (optionnel)
     * @param productId L'ID du produit (optionnel)
     * @param tier Le niveau de la machine (optionnel)
     * @return Le message avec les glyphes remplacés
     */
    public String replaceGlyphPlaceholders(String message, String machineId, String productId, Integer tier) {
        if (message == null) return null;
        String result = message;
        if (machineId != null) {
            result = result.replace("{machine_glyph}", getMachineGlyph(machineId));
            if (tier != null) {
                result = result.replace("{tier_glyph}", getMachineTierGlyph(machineId, tier));
            }
        }
        if (productId != null) {
            result = result.replace("{product_glyph}", getProductGlyph(productId));
        }
        return result;
    }
}

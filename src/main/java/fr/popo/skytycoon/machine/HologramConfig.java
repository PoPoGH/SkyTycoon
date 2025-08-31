package fr.popo.skytycoon.machine;

import fr.popo.skytycoon.SkyTycoonPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire de configuration des hologrammes avec support MiniMessage
 */
public class HologramConfig {
    
    private final SkyTycoonPlugin plugin;
    private final MiniMessage miniMessage;
    
    // Templates de messages configurables
    private String machineTitle;
    private String machineContent;
    private String machineStarting;
    private String machineFull;
    private String machineProducing;
    
    // Icônes par produit
    private final Map<String, String> productIcons = new HashMap<>();
    
    public HologramConfig(SkyTycoonPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        loadConfig();
    }
    
    /**
     * Charge la configuration depuis config.yml
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        
        // Templates d'hologrammes
        machineTitle = config.getString("holograms.machine_title", 
            "<gradient:#FFD700:#FFA500><bold>{machine_name}</bold></gradient>");
        machineContent = config.getString("holograms.machine_content", 
            "<color:#00FF00>{icon}</color> <white>{amount}</white> <gray>{product}</gray>");
        machineStarting = config.getString("holograms.machine_starting", 
            "<color:#FFFF00>⚡ <italic>Démarrage...</italic></color>");
        machineFull = config.getString("holograms.machine_full", 
            "<color:#FF4444>⚠ <bold>STOCKAGE PLEIN</bold> ⚠</color>");
        machineProducing = config.getString("holograms.machine_producing", 
            "<color:#00FFFF>✨ <italic>Production en cours...</italic> ✨</color>");
        
        // Icônes des produits
        productIcons.clear();
        if (config.getConfigurationSection("holograms.product_icons") != null) {
            for (String key : config.getConfigurationSection("holograms.product_icons").getKeys(false)) {
                String icon = config.getString("holograms.product_icons." + key, "●");
                productIcons.put(key.toLowerCase(), icon);
            }
        }
        
        // Icônes par défaut si pas dans la config
        productIcons.putIfAbsent("cobblestone", "⛏");
        productIcons.putIfAbsent("oak_log", "🪓");
        productIcons.putIfAbsent("wheat", "🌾");
        productIcons.putIfAbsent("rotten_flesh", "⚔");
        productIcons.putIfAbsent("emerald", "💰");
        
        plugin.getLogger().info("Configuration des hologrammes chargée avec " + productIcons.size() + " icônes");
    }
    
    /**
     * Recharge la configuration
     */
    public void reload() {
        plugin.reloadConfig();
        loadConfig();
    }
    
    /**
     * Génère le Component pour le titre de la machine
     */
    public Component getMachineTitleComponent(String machineName) {
        String message = machineTitle.replace("{machine_name}", machineName);
        return miniMessage.deserialize(message);
    }
    
    /**
     * Génère le Component pour le contenu de la machine
     */
    public Component getMachineContentComponent(int amount, String productName) {
        String icon = getProductIcon(productName);
        String message = machineContent
            .replace("{icon}", icon)
            .replace("{amount}", String.valueOf(amount))
            .replace("{product}", productName);
        return miniMessage.deserialize(message);
    }
    
    /**
     * Génère le Component pour le démarrage
     */
    public Component getMachineStartingComponent() {
        return miniMessage.deserialize(machineStarting);
    }
    
    /**
     * Génère le Component pour stockage plein
     */
    public Component getMachineFullComponent() {
        return miniMessage.deserialize(machineFull);
    }
    
    /**
     * Génère le Component pour production en cours
     */
    public Component getMachineProducingComponent() {
        return miniMessage.deserialize(machineProducing);
    }
    
    /**
     * Obtient l'icône pour un produit
     */
    public String getProductIcon(String productName) {
        String cleanName = productName.toLowerCase().replace(" ", "_");
        return productIcons.getOrDefault(cleanName, "●");
    }
    
    /**
     * Obtient le texte brut d'un template (pour debug)
     */
    public String getRawTemplate(String templateName) {
        switch (templateName.toLowerCase()) {
            case "title": return machineTitle;
            case "content": return machineContent;
            case "starting": return machineStarting;
            case "full": return machineFull;
            case "producing": return machineProducing;
            default: return "Template inconnu: " + templateName;
        }
    }
    
    /**
     * Test de rendu d'un message MiniMessage
     */
    public Component testMiniMessage(String message) {
        try {
            return miniMessage.deserialize(message);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur de parsing MiniMessage: " + e.getMessage());
            return Component.text("§cErreur de format: " + message);
        }
    }
}

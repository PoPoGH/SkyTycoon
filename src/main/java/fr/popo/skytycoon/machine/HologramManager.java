package fr.popo.skytycoon.machine;

import fr.popo.skytycoon.config.LangManager;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.Component;
import java.util.HashMap;
import java.util.Map;

public class HologramManager {
    
    // Stocker les hologrammes TextDisplay par machine
    private final Map<Location, TextDisplay> holograms = new HashMap<>();
    private LangManager langManager;
    private final fr.popo.skytycoon.SkyTycoonPlugin plugin;
    
    public HologramManager(fr.popo.skytycoon.SkyTycoonPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Définit le LangManager à utiliser
     */
    public void setLangManager(LangManager langManager) {
        this.langManager = langManager;
    }
    
    /**
     * Crée un hologramme pour une machine
     */
    public void createMachineHologram(Location machineLocation, String machineName) {
        createMachineHologram(machineLocation, machineName, null);
    }
    
    /**
     * Crée un hologramme pour une machine avec ID spécifique
     */
    public void createMachineHologram(Location machineLocation, String machineName, String machineId) {
        // Position de l'hologramme au-dessus de la machine
        Location hologramLocation = machineLocation.clone().add(0.5, 2.5, 0.5);
        
        // Supprimer l'ancien hologramme s'il existe
        removeMachineHologram(machineLocation);
        
        // Créer le TextDisplay
        TextDisplay hologram = (TextDisplay) machineLocation.getWorld().spawnEntity(
            hologramLocation, EntityType.TEXT_DISPLAY
        );
        
        // Configuration du TextDisplay
        hologram.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        hologram.setViewRange(50.0f);
        hologram.setSeeThrough(false);
        
        // Obtenir le texte formaté
        Component nameComponent;
        Component contentComponent;
        
        if (langManager != null && machineId != null) {
            // Utiliser le LangManager pour un formatage stylé
            nameComponent = langManager.getMachineHologramName(machineId, null); // Tier à ajouter si disponible
            contentComponent = langManager.getMachineStartingMessage();
        } else {
            // Fallback sur l'ancien système
            nameComponent = Component.text("§6▬▬ §e" + machineName + " §6▬▬");
            contentComponent = Component.text("§7● 0 Démarrage...");
        }
        
        // Combiner les deux lignes
        Component fullText = nameComponent.append(Component.text("\n")).append(contentComponent);
        hologram.text(fullText);
        
        // Stocker l'hologramme
        holograms.put(machineLocation, hologram);
        
        System.out.println("[SkyTycoon] Hologramme TextDisplay créé pour " + machineName + " à " + 
            machineLocation.getBlockX() + "," + machineLocation.getBlockY() + "," + machineLocation.getBlockZ());
    }
    
    /**
     * Met à jour le texte d'un hologramme de machine
     */
    public void updateMachineHologram(Location machineLocation, String machineName, int amount, String productName) {
        updateMachineHologram(machineLocation, machineName, amount, productName, null);
    }
    
    /**
     * Met à jour le texte d'un hologramme de machine avec ID spécifique
     */
    public void updateMachineHologram(Location machineLocation, String machineName, int amount, String productName, String machineId) {
        TextDisplay hologram = holograms.get(machineLocation);
        
        if (hologram == null || !hologram.isValid()) {
            System.out.println("[SkyTycoon] Hologramme TextDisplay introuvable pour mise à jour à " + machineLocation);
            return;
        }
        
        // Obtenir les composants formatés
        Component nameComponent;
        Component contentComponent;
        Component progressComponent = null;
        
        int progressCurrent = 0;
        int progressMax = 1;
        if (langManager != null && machineId != null) {
            int tier = 1;
            if (machineName != null && machineName.matches(".*\\[T(\\d+)\\]$")) {
                try {
                    tier = Integer.parseInt(machineName.replaceAll(".*\\[T(\\d+)\\]$", "$1"));
                } catch (Exception ignored) {}
            }
            nameComponent = langManager.getMachineHologramName(machineId, tier);
            contentComponent = langManager.getMachineHologramContent(amount, productName, machineId, tier);
            // Calcul du temps avant le prochain tick de production
            ActiveMachine am = null;
            if (plugin != null && plugin.getMachineManager() != null) {
                am = plugin.getMachineManager().getActiveMachine(machineLocation);
            }
            if (am != null) {
                long currentTick = plugin.getMachineManager().getCurrentTick(machineLocation);
                long interval = am.getCurrentInterval();
                long startTick = am.nextTick() - interval;
                progressMax = (int) interval;
                progressCurrent = (int) Math.max(0, Math.min(interval, currentTick - startTick));
            }
            progressComponent = langManager.createProgressBar(progressCurrent, progressMax);
        } else {
            // Fallback sur l'ancien système
            String icon = getProductIcon(productName);
            String color = getProductColor(productName);
            nameComponent = Component.text("§6▬▬ §e" + machineName + " §6▬▬");
            contentComponent = Component.text(String.format("%s%s §f%d §7%s", color, icon, amount, productName));
        }
        
        // Combiner les trois lignes
        Component fullText = nameComponent.append(Component.text("\n")).append(contentComponent);
        if (progressComponent != null) {
            fullText = fullText.append(Component.text("\n")).append(progressComponent);
        }
        hologram.text(fullText);
        
        System.out.println("[SkyTycoon] Hologramme TextDisplay mis à jour: " + machineName + " | " + amount + " " + productName);
    }
    
    /**
     * Supprime l'hologramme d'une machine
     */
    public void removeMachineHologram(Location machineLocation) {
        TextDisplay hologram = holograms.remove(machineLocation);
        
        if (hologram != null && hologram.isValid()) {
            hologram.remove();
        }
        
        System.out.println("[SkyTycoon] Hologramme TextDisplay supprimé à " + machineLocation);
    }
    
    /**
     * Supprime tous les hologrammes
     */
    public void removeAllHolograms() {
        for (TextDisplay hologram : holograms.values()) {
            if (hologram != null && hologram.isValid()) {
                hologram.remove();
            }
        }
        holograms.clear();
        
        System.out.println("[SkyTycoon] Tous les hologrammes TextDisplay supprimés");
    }
    
    /**
     * Obtient l'icône Unicode pour un produit
     */
    private String getProductIcon(String productName) {
        switch (productName.toLowerCase()) {
            case "cobblestone":
            case "pierre":
                return "⛏";
            case "oak_log":
            case "bois":
                return "🪓";
            case "diamond":
            case "diamant":
                return "◆";
            case "gold_ingot":
            case "or":
                return "▲";
            case "emerald":
            case "émeraude":
                return "♦";
            case "iron_ingot":
            case "fer":
                return "▼";
            default:
                return "●";
        }
    }
    
    /**
     * Obtient la couleur pour un produit
     */
    private String getProductColor(String productName) {
        switch (productName.toLowerCase()) {
            case "cobblestone":
            case "pierre":
                return "§7";
            case "oak_log":
            case "bois":
                return "§6";
            case "diamond":
            case "diamant":
                return "§b";
            case "gold_ingot":
            case "or":
                return "§e";
            case "emerald":
            case "émeraude":
                return "§a";
            case "iron_ingot":
            case "fer":
                return "§f";
            default:
                return "§8";
        }
    }
    
    /**
     * Vérifie si un hologramme existe à cette position
     */
    public boolean hasHologram(Location machineLocation) {
        TextDisplay hologram = holograms.get(machineLocation);
        return hologram != null && hologram.isValid();
    }
    
    /**
     * Obtient le nombre d'hologrammes actifs
     */
    public int getHologramCount() {
        return holograms.size();
    }
}

package fr.popo.skytycoon.machine;

import fr.popo.skytycoon.SkyTycoonPlugin;
import fr.popo.skytycoon.config.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charge les définitions depuis config et gère les instances actives.
 * Pas de tâche globale synchrone: on schedule par région via Folia.
 */
public class MachineManager {
    private final SkyTycoonPlugin plugin;
    private final Map<String, MachineDefinition> definitions = new HashMap<>();
    private final Map<Location, ActiveMachine> active = new ConcurrentHashMap<>();
    private final Map<Location, io.papermc.paper.threadedregions.scheduler.ScheduledTask> machineTasks = new ConcurrentHashMap<>();
    private final HologramManager hologramManager;

    private final NamespacedKey keyMachineId;
    
    // Compteur interne pour remplacer le temps du monde qui peut être bloqué
    private final Map<Location, Long> machineTickCounters = new ConcurrentHashMap<>();

    public MachineManager(SkyTycoonPlugin plugin) {
        this.plugin = plugin;
        this.keyMachineId = new NamespacedKey(plugin, "machine-id");
        this.hologramManager = new HologramManager(plugin);
        loadDefinitions();
    }
    
    /**
     * Définit le LangManager pour les hologrammes stylés
     */
    public void setLangManager(LangManager langManager) {
        hologramManager.setLangManager(langManager);
    }

    public void reload() {
        definitions.clear();
        loadDefinitions();
    }

    private void loadDefinitions() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "machines.yml");
        if(!file.exists()) {
            plugin.saveResource("machines.yml", false);
        }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("machines");
        if(root == null) {
            plugin.getLogger().warning("Aucune section machines trouvée dans machines.yml");
            return;
        }
        for(String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if(s == null) continue;
            try {
                MachineType type = MachineType.valueOf(s.getString("type"));
                String name = s.getString("name", id);
                Material block = Material.matchMaterial(s.getString("block", "STONE"));
                long interval = s.getLong("base-interval", 40L);
                int yield = s.getInt("base-yield", 1);
                if(block == null) block = Material.STONE;
                MachineDefinition def = new MachineDefinition(type, id, name, block, interval, yield);
                definitions.put(id, def);
            } catch (Exception ex) {
                plugin.getLogger().warning("Erreur chargement machine " + id + ": " + ex.getMessage());
            }
        }
    }

    public int getRegisteredCount() { return definitions.size(); }

    public void registerActive(UUID owner, String id, Location loc) {
        MachineDefinition def = definitions.get(id);
        if(def == null) return;
        
        // Placer le bloc simple de la machine
        Material blockType = getMaterialForMachine(id);
        loc.getBlock().setType(blockType);
        System.out.println("[SkyTycoon] Machine " + id + " placée avec bloc " + blockType);
        
        ActiveMachine am = new ActiveMachine(owner, def, loc, 0L); // Commencer à 0 avec notre compteur interne
        active.put(loc, am);
        scheduleMachine(am);
        
        // Créer l'hologramme pour cette machine avec l'ID pour le formatage
        hologramManager.createMachineHologram(am.location(), am.def().displayName(), id);
    }

    private void scheduleMachine(ActiveMachine am) {
        Location l = am.location();
        plugin.getLogger().info("Démarrage de la machine " + am.def().displayName() + " à " + 
            l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        
        // Initialiser le compteur de ticks pour cette machine
        machineTickCounters.put(l, 0L);
        
        // Scheduler avec compteur interne au lieu du temps du monde
        var task = Bukkit.getRegionScheduler().runAtFixedRate(plugin, l, scheduledTask -> {
            // Incrémenter notre compteur interne
            long currentTick = machineTickCounters.merge(l, 1L, Long::sum);
            
            if(currentTick >= am.nextTick()) {
                System.out.println("[SkyTycoon] *** PRODUCTION TIME! ***");
                
                // Production avec animations dans le stockage de la machine !
                boolean produced = am.produceAndCheck();
                System.out.println("[SkyTycoon] Production result: " + produced);
                
                if (produced) {
                    // Mettre à jour l'hologramme avec les nouvelles quantités
                    Material mainProductMaterial = am.getMainProduct();
                    String mainProduct = mainProductMaterial.name().toLowerCase().replace("_", " ");
                    int amount = am.getStoredAmount(mainProductMaterial);
                    hologramManager.updateMachineHologram(am.location(), am.def().displayName(), amount, mainProduct, am.def().id());
                    
                    // Effets visuels spécifiques selon le type de machine
                    addMachineEffects(l, am.def().id());
                    
                    // Particules génériques de succès
                    Location effectLocation = l.clone().add(0.5, 1.5, 0.5);
                    l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLocation, 1,
                        0.1, 0.1, 0.1, 0.05);
                } else {
                    System.out.println("[SkyTycoon] DEBUG - Machine ne peut pas produire (storage plein?)");
                }
                
                am.scheduleNext(currentTick);
                
                plugin.getLogger().info("Machine " + am.def().displayName() + " a produit. Stockage: " + 
                    am.getStorageDisplay());
            }
            
            // Mise à jour de l'hologramme toutes les 20 ticks (1 seconde)
            if (currentTick % 20 == 0) { 
                Material mainProductMaterial = am.getMainProduct();
                String mainProduct = mainProductMaterial.name().toLowerCase().replace("_", " ");
                int amount = am.getStoredAmount(mainProductMaterial);
                hologramManager.updateMachineHologram(am.location(), am.def().displayName(), amount, mainProduct, am.def().id());
            }
        }, 1L, 5L); // Exécuter toutes les 5 ticks (0.25 secondes)
        
        // Stocker la tâche pour pouvoir l'arrêter plus tard
        machineTasks.put(l, task);
    }

    public void shutdown() {
        // Arrêter toutes les tâches
        for (var task : machineTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        machineTasks.clear();
        
        // Supprimer tous les hologrammes
        hologramManager.removeAllHolograms();
        
        // Nettoyer les compteurs
        machineTickCounters.clear();
        
        active.clear();
    }

    public ItemStack createMachineItem(String id) {
        MachineDefinition def = definitions.get(id);
        if(def == null) return null;
        ItemStack item = new ItemStack(def.blockMaterial());
        ItemMeta meta = item.getItemMeta();
        if(meta != null) {
            meta.displayName(Component.text(def.displayName()));
            meta.getPersistentDataContainer().set(keyMachineId, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String extractMachineId(ItemStack item) {
        if(item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if(meta == null) return null;
        return meta.getPersistentDataContainer().get(keyMachineId, PersistentDataType.STRING);
    }
    
    /**
     * Récupère une machine active à une location donnée
     */
    public ActiveMachine getActiveMachine(Location location) {
        return active.get(location);
    }
    
    /**
     * Désactive et retire une machine active
     */
    public boolean unregisterActive(Location location) {
        // Arrêter la tâche de production
        var task = machineTasks.remove(location);
        if (task != null) {
            task.cancel();
        }
        
        // Supprimer l'hologramme
        hologramManager.removeMachineHologram(location);
        
        // Supprimer le bloc de la machine (remettre de l'air)
        location.getBlock().setType(Material.AIR);
        System.out.println("[SkyTycoon] Bloc de machine supprimé à " + location);
        
        // Nettoyer le compteur de ticks
        machineTickCounters.remove(location);
        
        ActiveMachine removed = active.remove(location);
        if (removed != null) {
            plugin.getLogger().info("Machine " + removed.def().displayName() + 
                " désactivée à " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            return true;
        }
        return false;
    }
    
    /**
     * Obtient le nombre de machines actives
     */
    public int getActiveMachineCount() {
        return active.size();
    }
    
    /**
     * Met à jour l'affichage d'une machine (méthode publique)
     */
    public void updateMachineDisplay(ActiveMachine machine) {
        Material mainProductMaterial = machine.getMainProduct();
        String mainProduct = mainProductMaterial.name().toLowerCase().replace("_", " ");
        int amount = machine.getStoredAmount(mainProductMaterial);
        hologramManager.updateMachineHologram(machine.location(), machine.def().displayName(), amount, mainProduct);
    }
    
    /**
     * Obtient le matériau de bloc pour un type de machine
     */
    private Material getMaterialForMachine(String machineId) {
        switch (machineId.toLowerCase()) {
            case "basic_miner":
                return Material.COBBLESTONE;
            case "wood_cutter":
                return Material.OAK_LOG;
            case "crop_farm":
                return Material.HAY_BLOCK;
            case "mob_grinder":
                return Material.REDSTONE_BLOCK;
            case "sell_station":
                return Material.EMERALD_BLOCK;
            default:
                return Material.STONE; // Fallback
        }
    }
    
    /**
     * Ajoute des effets visuels spécifiques pour chaque type de machine
     */
    private void addMachineEffects(Location location, String machineId) {
        Location effectLoc = location.clone().add(0.5, 1.0, 0.5);
        
        switch (machineId.toLowerCase()) {
            case "basic_miner":
                // Poussière de pierre qui tombe
                location.getWorld().spawnParticle(Particle.BLOCK, effectLoc, 5,
                    0.3, 0.2, 0.3, 0.1, Material.COBBLESTONE.createBlockData());
                // Sons de pioche
                location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_STONE_BREAK, 0.5f, 1.0f);
                break;
                
            case "wood_cutter":
                // Copeaux de bois qui volent
                location.getWorld().spawnParticle(Particle.BLOCK, effectLoc, 4,
                    0.4, 0.3, 0.4, 0.2, Material.OAK_LOG.createBlockData());
                // Son de hache
                location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_WOOD_BREAK, 0.6f, 0.8f);
                break;
                
            case "crop_farm":
                // Particules vertes de croissance
                location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLoc, 3,
                    0.5, 0.2, 0.5, 0.1);
                // Particules de blé
                location.getWorld().spawnParticle(Particle.ITEM, effectLoc, 2,
                    0.3, 0.1, 0.3, 0.1, new org.bukkit.inventory.ItemStack(Material.WHEAT));
                // Son de récolte
                location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_CROP_BREAK, 0.7f, 1.2f);
                break;
                
            case "mob_grinder":
                // Particules rouges de combat
                location.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, effectLoc, 3,
                    0.3, 0.2, 0.3, 0.1);
                // Particules de sang/chair
                location.getWorld().spawnParticle(Particle.ITEM, effectLoc, 2,
                    0.2, 0.1, 0.2, 0.1, new org.bukkit.inventory.ItemStack(Material.ROTTEN_FLESH));
                // Son de combat
                location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_ZOMBIE_HURT, 0.4f, 0.8f);
                break;
                
            case "sell_station":
                // Particules dorées d'argent
                location.getWorld().spawnParticle(Particle.GLOW, effectLoc, 4,
                    0.3, 0.3, 0.3, 0.1);
                // Particules d'émeraudes
                location.getWorld().spawnParticle(Particle.ITEM, effectLoc, 1,
                    0.2, 0.2, 0.2, 0.1, new org.bukkit.inventory.ItemStack(Material.EMERALD));
                // Son de vente
                location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                break;
        }
    }

    public long getCurrentTick(Location loc) {
        return machineTickCounters.getOrDefault(loc, 0L);
    }
}

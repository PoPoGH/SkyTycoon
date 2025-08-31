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

    // Pour éviter les updates inutiles d'hologramme
    private final Map<Location, Integer> lastDisplayedAmount = new ConcurrentHashMap<>();
    private final Map<Location, Integer> lastDisplayedProgress = new ConcurrentHashMap<>();
    private final Map<Location, String> lastDisplayedBar = new ConcurrentHashMap<>();

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

        // Sauvegarder après ajout
        saveMachinesToFile();
    }

    private void scheduleMachine(ActiveMachine am) {
        Location l = am.location();
        plugin.getLogger().info("Démarrage de la machine " + am.def().displayName() + " à " + 
            l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        long startTick = am.nextTick() - am.getCurrentInterval();
        if (startTick < 0) startTick = 0;
        machineTickCounters.put(l, startTick);
        Material mainProductMaterial = am.getMainProduct();
        lastDisplayedAmount.put(l, am.getStoredAmount(mainProductMaterial));
        lastDisplayedProgress.put(l, 0);
        lastDisplayedBar.put(l, "");
        var task = Bukkit.getRegionScheduler().runAtFixedRate(plugin, l, scheduledTask -> {
            long currentTick = machineTickCounters.merge(l, 1L, Long::sum);
            Material mainProductMaterial1 = am.getMainProduct();
            String mainProduct = mainProductMaterial1.name().toLowerCase().replace("_", " ");
            int amount = am.getStoredAmount(mainProductMaterial1);
            long interval = am.getCurrentInterval();
            long startTickVal = am.nextTick() - interval;
            int progressMax = (int) interval;
            int progressCurrent = (int) Math.max(0, Math.min(interval, currentTick - startTickVal));
            String progressBar = "";
            if (plugin.getLangManager() != null && am.def() != null) {
                progressBar = plugin.getLangManager().createProgressBar(progressCurrent, progressMax).toString();
            }
            // Production
            if(currentTick >= am.nextTick()) {
                System.out.println("[SkyTycoon] *** PRODUCTION TIME! ***");
                boolean produced = am.produceAndCheck();
                System.out.println("[SkyTycoon] Production result: " + produced);
                if (produced) {
                    // Mettre à jour l'hologramme si la quantité ou la progress bar a changé
                    boolean update = false;
                    if (lastDisplayedAmount.getOrDefault(l, -1) != amount) update = true;
                    if (!Objects.equals(lastDisplayedBar.get(l), progressBar)) update = true;
                    if (update) {
                        hologramManager.updateMachineHologram(am.location(), am.def().displayName(), amount, mainProduct, am.def().id());
                        lastDisplayedAmount.put(l, amount);
                        lastDisplayedProgress.put(l, progressCurrent);
                        lastDisplayedBar.put(l, progressBar);
                    }
                    addMachineEffects(l, am.def().id());
                    Location effectLocation = l.clone().add(0.5, 1.5, 0.5);
                    l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLocation, 1,
                        0.1, 0.1, 0.1, 0.05);
                } else {
                    System.out.println("[SkyTycoon] DEBUG - Machine ne peut pas produire (storage plein?)");
                }
                am.scheduleNext(currentTick);
                plugin.getLogger().info("Machine " + am.def().displayName() + " a produit. Stockage: " +
                    am.getStorageDisplay());
            } else {
                // Mise à jour de l'hologramme uniquement si la progress bar change
                if (!Objects.equals(lastDisplayedBar.get(l), progressBar)) {
                    hologramManager.updateMachineHologram(am.location(), am.def().displayName(), amount, mainProduct, am.def().id());
                    lastDisplayedAmount.put(l, amount);
                    lastDisplayedProgress.put(l, progressCurrent);
                    lastDisplayedBar.put(l, progressBar);
                }
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
        // Nettoyer la dernière quantité affichée
        lastDisplayedAmount.remove(location);
        lastDisplayedProgress.remove(location);
        lastDisplayedBar.remove(location);
        ActiveMachine removed = active.remove(location);
        if (removed != null) {
            plugin.getLogger().info("Machine " + removed.def().displayName() + 
                " désactivée à " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            // Sauvegarder après suppression
            saveMachinesToFile();
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

    /**
     * Sauvegarde les machines actives dans un fichier YAML
     */
    public void saveMachinesToFile() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "machines_data.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<Location, ActiveMachine> entry : active.entrySet()) {
            ActiveMachine machine = entry.getValue();
            String key = "machines." + i;
            yaml.set(key + ".owner", machine.owner().toString());
            yaml.set(key + ".machineId", machine.def().id());
            yaml.set(key + ".world", machine.location().getWorld().getName());
            yaml.set(key + ".x", machine.location().getBlockX());
            yaml.set(key + ".y", machine.location().getBlockY());
            yaml.set(key + ".z", machine.location().getBlockZ());
            yaml.set(key + ".level", machine.getLevel());
            yaml.set(key + ".nextTick", machine.nextTick());
            // Stockage
            Map<String, Integer> storageMap = new HashMap<>();
            for (Map.Entry<org.bukkit.Material, Integer> st : machine.getStorage().entrySet()) {
                storageMap.put(st.getKey().name(), st.getValue());
            }
            yaml.set(key + ".storage", storageMap);
            i++;
        }
        try {
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la sauvegarde des machines: " + e.getMessage());
        }
    }

    /**
     * Charge les machines actives depuis un fichier YAML
     */
    public void loadMachinesFromFile() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "machines_data.yml");
        if (!file.exists()) return;
        // Nettoyer tous les anciens hologrammes pour éviter les doublons/orphelins
        hologramManager.removeAllHolograms();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("machines");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String ownerStr = section.getString(key + ".owner");
            String machineId = section.getString(key + ".machineId");
            String worldName = section.getString(key + ".world");
            int x = section.getInt(key + ".x");
            int y = section.getInt(key + ".y");
            int z = section.getInt(key + ".z");
            int level = section.getInt(key + ".level", 1);
            long nextTick = section.contains(key + ".nextTick") ? section.getLong(key + ".nextTick") : -1L;
            long lastProductionTime = section.contains(key + ".lastProductionTime") ? section.getLong(key + ".lastProductionTime") : 0L;
            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            Location loc = new Location(world, x, y, z);
            MachineDefinition def = definitions.get(machineId);
            if (def == null) continue;
            ActiveMachine machine = new ActiveMachine(java.util.UUID.fromString(ownerStr), def, loc, world.getFullTime());
            machine.setLevel(level);
            if (nextTick > 0) machine.setNextTick(nextTick);
            machine.setLastProductionTime(lastProductionTime);
            // Restaurer le stockage
            Map<String, Object> storageMap = section.getConfigurationSection(key + ".storage").getValues(false);
            for (Map.Entry<String, Object> st : storageMap.entrySet()) {
                try {
                    org.bukkit.Material mat = org.bukkit.Material.valueOf(st.getKey());
                    int amount = Integer.parseInt(st.getValue().toString());
                    machine.getStorage().put(mat, amount);
                } catch (Exception ignored) {}
            }
            // Rattrapage de production après un arrêt serveur
            long now = System.currentTimeMillis();
            long intervalTicks = machine.getCurrentInterval();
            long intervalMs = intervalTicks * 50L;
            if (lastProductionTime > 0 && now > lastProductionTime && intervalMs > 0) {
                long cycles = (now - lastProductionTime) / intervalMs;
                for (long i = 0; i < cycles; i++) {
                    if (!machine.canProduce()) break;
                    machine.produceAndCheck();
                }
                // Mettre à jour le timestamp pour le prochain cycle
                machine.setLastProductionTime(lastProductionTime + cycles * intervalMs);
            }
            active.put(loc, machine);
            // Recréer l'hologramme
            hologramManager.createOrUpdateHologram(machine);
            // Relancer la tâche de production
            scheduleMachine(machine);
        }
    }

    /**
     * Retire un certain nombre d'items du stockage principal de la machine et les donne au joueur,
     * sans jamais dépasser la capacité de l'inventaire (le reste reste dans la machine)
     */
    public void withdrawFromMachine(org.bukkit.entity.Player player, ActiveMachine machine, int amount) {
        Material mainProduct = machine.getMainProduct();
        int available = machine.getStoredAmount(mainProduct);
        if (available <= 0) {
            player.sendMessage("§cAucun item à retirer.");
            return;
        }
        int maxStackSize = mainProduct.getMaxStackSize();
        int toTake = Math.min(amount, available);
        // Calculer la place disponible dans l'inventaire
        int freeSpace = getFreeSpaceFor(player, mainProduct);
        if (freeSpace <= 0) {
            player.sendMessage("§eVotre inventaire est plein !");
            return;
        }
        int finalTake = Math.min(toTake, freeSpace);
        if (finalTake <= 0) {
            player.sendMessage("§eVotre inventaire est plein !");
            return;
        }
        ItemStack stack = machine.retrieveItems(mainProduct, finalTake);
        player.getInventory().addItem(stack);
        if (finalTake < toTake) {
            player.sendMessage("§eVous n'aviez pas assez de place, seuls " + finalTake + " ont été retirés.");
        } else {
            player.sendMessage("§aVous avez retiré " + finalTake + " " + mainProduct.name().toLowerCase().replace("_", " ") + ".");
        }
    }

    /**
     * Retire tout le stockage principal de la machine et le donne au joueur,
     * sans jamais dépasser la capacité de l'inventaire (le reste reste dans la machine)
     */
    public void withdrawAllFromMachine(org.bukkit.entity.Player player, ActiveMachine machine) {
        Material mainProduct = machine.getMainProduct();
        int available = machine.getStoredAmount(mainProduct);
        if (available <= 0) {
            player.sendMessage("§cAucun item à retirer.");
            return;
        }
        int freeSpace = getFreeSpaceFor(player, mainProduct);
        if (freeSpace <= 0) {
            player.sendMessage("§eVotre inventaire est plein !");
            return;
        }
        int toTake = Math.min(available, freeSpace);
        ItemStack stack = machine.retrieveItems(mainProduct, toTake);
        player.getInventory().addItem(stack);
        if (toTake < available) {
            player.sendMessage("§eVous n'aviez pas assez de place, seuls " + toTake + " ont été retirés.");
        } else {
            player.sendMessage("§aVous avez retiré " + toTake + " " + mainProduct.name().toLowerCase().replace("_", " ") + ".");
        }
    }

    /**
     * Calcule la place disponible dans l'inventaire du joueur pour un type d'item
     */
    private int getFreeSpaceFor(org.bukkit.entity.Player player, Material material) {
        int free = 0;
        int maxStack = material.getMaxStackSize();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                free += maxStack;
            } else if (item.getType() == material && item.getAmount() < maxStack) {
                free += (maxStack - item.getAmount());
            }
        }
        return free;
    }
}

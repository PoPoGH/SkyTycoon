package fr.popo.skytycoon.machine;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ActiveMachine {
    private final UUID owner; // island/player owner id
    private final MachineDefinition definition;
    private final Location location; // anchor block location
    private long nextTick; // next production tick (world time)
    private int level = 1;
    
    // Stockage individuel de cette machine
    private final Map<Material, Integer> storage = new HashMap<>();

    public ActiveMachine(UUID owner, MachineDefinition definition, Location location, long currentTick) {
        this.owner = owner;
        this.definition = definition;
        this.location = location;
        scheduleNext(currentTick);
    }

        /**
     * Retourne l'intervalle de production courant (en ticks)
     */
    public long getCurrentInterval() {
        return Math.max(1, definition.baseIntervalTicks() - (level-1)*5);
    }

    public UUID owner() { return owner; }
    public MachineDefinition def() { return definition; }
    public Location location() { return location; }

    public void scheduleNext(long currentTick) {
        long interval = Math.max(1, definition.baseIntervalTicks() - (level-1)*5);
        this.nextTick = currentTick + interval;
    }

    public long nextTick() { return nextTick; }

    public List<ItemStack> produce() {
        // Animation et effets selon le type de machine
        playMachineAnimation();
        
        // Production d'items selon le type et stockage dans la machine
        int produced = definition.baseYield() + (level - 1);
        
        switch (definition.type()) {
            case BASIC_MINER:
                storage.merge(Material.COBBLESTONE, produced, Integer::sum);
                break;
            case WOOD_CUTTER:
                storage.merge(Material.OAK_LOG, produced, Integer::sum);
                break;
            default:
                break;
        }
        
        // Retourner une liste vide car tout est stocké dans la machine maintenant
        return new ArrayList<>();
    }
    
    /**
     * Version simplifiée de produce() qui retourne true si la production a réussi
     */
    public boolean canProduce() {
        // Vérifier si on peut encore stocker (limite de sécurité)
        Material mainProduct = getMainProduct();
        int currentAmount = getStoredAmount(mainProduct);
        return currentAmount < 10000; // Limite de sécurité pour éviter l'overflow
    }
    
    /**
     * Produit et retourne true si la production a réussi
     */
    public boolean produceAndCheck() {
        if (!canProduce()) {
            System.out.println("[DEBUG] Cannot produce - storage full for " + getMainProduct());
            return false;
        }
        
        System.out.println("[DEBUG] PRODUISANT MAINTENANT!");
        
        // Produire
        produce();
        
        System.out.println("[DEBUG] Production terminée avec succès!");
        
        return true;
    }
    
    /**
     * Récupère des items du stockage de la machine
     */
    public ItemStack retrieveItems(Material material, int amount) {
        int available = storage.getOrDefault(material, 0);
        if (available <= 0) return null;
        
        int toTake = Math.min(amount, available);
        storage.put(material, available - toTake);
        
        return new ItemStack(material, toTake);
    }
    
    /**
     * Récupère tout le stockage d'un matériau
     */
    public ItemStack retrieveAllItems(Material material) {
        int available = storage.getOrDefault(material, 0);
        if (available <= 0) return null;
        
        storage.put(material, 0);
        return new ItemStack(material, available);
    }
    
    /**
     * Obtient la quantité stockée d'un matériau
     */
    public int getStoredAmount(Material material) {
        return storage.getOrDefault(material, 0);
    }
    
    /**
     * Obtient le matériau principal que cette machine produit
     */
    public Material getMainProduct() {
        switch (definition.type()) {
            case BASIC_MINER:
                return Material.COBBLESTONE;
            case WOOD_CUTTER:
                return Material.OAK_LOG;
            default:
                return Material.STONE;
        }
    }
    
    /**
     * Obtient une représentation textuelle du stockage pour l'affichage
     */
    public String getStorageDisplay() {
        Material mainProduct = getMainProduct();
        int amount = getStoredAmount(mainProduct);
        return "§f" + amount + " §7" + mainProduct.name().toLowerCase().replace("_", " ");
    }
    
    /**
     * Obtient la quantité d'un produit spécifique (version String pour compatibilité hologramme)
     */
    public int getProductAmount(String productName) {
        Material material = getMainProduct();
        if (material.name().toLowerCase().replace("_", " ").equals(productName.toLowerCase())) {
            return getStoredAmount(material);
        }
        return 0;
    }
    
    /**
     * Joue l'animation et les effets sonores pour cette machine (optimisé pour les performances)
     */
    private void playMachineAnimation() {
        World world = location.getWorld();
        if (world == null) return;
        
        Location effectLocation = location.clone().add(0.5, 1.0, 0.5);
        
        switch (definition.type()) {
            case BASIC_MINER:
                // Animation de minage avec particules de pierre (optimisée)
                world.spawnParticle(Particle.BLOCK, effectLocation, 8, 
                    0.3, 0.3, 0.3, 0.1, Material.STONE.createBlockData());
                
                // Particules de fumée pour l'effet industriel (réduites)
                world.spawnParticle(Particle.SMOKE, effectLocation.clone().add(0, 0.5, 0), 3,
                    0.2, 0.1, 0.2, 0.02);
                
                // Son de minage (volume réduit pour moins de spam sonore)
                world.playSound(location, Sound.BLOCK_STONE_BREAK, 0.4f, 0.8f);
                world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.2f, 1.2f);
                break;
                
            case WOOD_CUTTER:
                // Animation de coupe de bois (optimisée)
                world.spawnParticle(Particle.BLOCK, effectLocation, 6,
                    0.3, 0.3, 0.3, 0.1, Material.OAK_LOG.createBlockData());
                
                // Particules de feuilles qui tombent (réduites)
                world.spawnParticle(Particle.FALLING_DUST, effectLocation.clone().add(0, 1, 0), 4,
                    0.4, 0.2, 0.4, 0.1, Material.OAK_LEAVES.createBlockData());
                
                // Sons de coupe (volume réduit)
                world.playSound(location, Sound.BLOCK_WOOD_BREAK, 0.5f, 0.9f);
                world.playSound(location, Sound.ITEM_AXE_STRIP, 0.3f, 1.1f);
                break;
                
            default:
                // Animation générique (très légère)
                world.spawnParticle(Particle.HAPPY_VILLAGER, effectLocation, 2,
                    0.2, 0.2, 0.2, 0.1);
                world.playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.0f);
                break;
        }
        
        // Effet lumineux pour montrer que la machine fonctionne (réduit)
        world.spawnParticle(Particle.END_ROD, effectLocation, 1, 0, 0, 0, 0);
    }
}

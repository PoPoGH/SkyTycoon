package fr.popo.skytycoon.machine;

import fr.popo.skytycoon.SkyTycoonPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gère les interactions avec les machines (shift+clic droit pour récupérer, placement automatique)
 */
public class MachineListener implements Listener {
    
    private final SkyTycoonPlugin plugin;
    
    public MachineListener(SkyTycoonPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        
        // Vérifier si c'est un item machine
        String machineId = plugin.machines().extractMachineId(item);
        if (machineId == null) return; // Ce n'est pas une machine
        
        Location location = event.getBlockPlaced().getLocation();
        
        // Enregistrer la machine active automatiquement
        plugin.machines().registerActive(player.getUniqueId(), machineId, location);
        
        player.sendMessage("§aMachine §f" + machineId + " §aplacée et activée !");
        player.sendMessage("§7Elle commencera à produire dans quelques secondes...");
        
        // Effet sonore et visuel de placement
        location.getWorld().playSound(location, Sound.BLOCK_STONE_PLACE, 0.8f, 1.0f);
        location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.5f);
        
        // Particules de placement
        location.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, 
            location.clone().add(0.5, 1, 0.5), 5, 0.3, 0.3, 0.3, 0.1);
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Vérifier qu'il y a un bloc cliqué
        if (event.getClickedBlock() == null) return;
        
        Location location = event.getClickedBlock().getLocation();
        
        // Vérifier si c'est une machine active
        ActiveMachine machine = plugin.machines().getActiveMachine(location);
        if (machine == null) return;
        
        // Vérifier que le joueur est le propriétaire de la machine
        if (!machine.owner().equals(player.getUniqueId())) {
            player.sendMessage("§cCette machine ne vous appartient pas !");
            event.setCancelled(true);
            return;
        }
        
        // Gérer les différents types d'interactions
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking()) {
            // Shift + Clic Gauche = Récupérer la machine
            handleMachinePickup(player, machine, location);
            event.setCancelled(true);
            
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Clic Droit = Récupérer des items
            if (player.isSneaking()) {
                // Shift + Clic Droit = Récupérer un stack entier
                handleItemRetrievalStack(player, machine);
            } else {
                // Clic Droit simple = Récupérer 1 item + afficher le contenu
                handleItemRetrievalSingle(player, machine);
            }
            event.setCancelled(true);
        }
    }
    
    private void handleMachinePickup(Player player, ActiveMachine machine, Location location) {
        // Créer l'item machine
        ItemStack machineItem = plugin.machines().createMachineItem(machine.def().id());
        if (machineItem == null) {
            player.sendMessage("§cErreur lors de la création de l'item machine !");
            return;
        }
        
        // Donner l'item au joueur
        if (player.getInventory().firstEmpty() == -1) {
            // Inventaire plein, faire tomber l'item
            location.getWorld().dropItemNaturally(location.clone().add(0.5, 1, 0.5), machineItem);
            player.sendMessage("§eVotre inventaire est plein ! La machine est tombée au sol.");
        } else {
            player.getInventory().addItem(machineItem);
            player.sendMessage("§aMachine récupérée : §f" + machine.def().displayName());
        }
        
        // Retirer le bloc
        location.getBlock().setType(org.bukkit.Material.AIR);
        
        // Désactiver la machine
        plugin.machines().unregisterActive(location);
        
        // Effet sonore et visuel
        location.getWorld().playSound(location, Sound.BLOCK_STONE_BREAK, 0.8f, 1.0f);
        location.getWorld().playSound(location, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        
        // Particules de récupération
        location.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, 
            location.clone().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0.1);
    }
    
    private void handleItemRetrievalSingle(Player player, ActiveMachine machine) {
        Material mainProduct = machine.getMainProduct();
        ItemStack retrieved = machine.retrieveItems(mainProduct, 1);
        
        if (retrieved != null) {
            // Donner l'item au joueur
            if (player.getInventory().firstEmpty() == -1) {
                // Inventaire plein, faire tomber l'item
                machine.location().getWorld().dropItemNaturally(
                    machine.location().clone().add(0.5, 1, 0.5), retrieved);
                player.sendMessage("§eVotre inventaire est plein ! L'item est tombé au sol.");
            } else {
                player.getInventory().addItem(retrieved);
            }
            
            // Effet sonore
            machine.location().getWorld().playSound(machine.location(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        }
        
        // Afficher le contenu restant
        showMachineContent(player, machine);
    }
    
    private void handleItemRetrievalStack(Player player, ActiveMachine machine) {
        Material mainProduct = machine.getMainProduct();
        ItemStack retrieved = machine.retrieveAllItems(mainProduct);
        
        if (retrieved != null && retrieved.getAmount() > 0) {
            // Donner les items au joueur
            if (player.getInventory().firstEmpty() == -1) {
                // Inventaire plein, faire tomber les items
                machine.location().getWorld().dropItemNaturally(
                    machine.location().clone().add(0.5, 1, 0.5), retrieved);
                player.sendMessage("§eVotre inventaire est plein ! Les items sont tombés au sol.");
            } else {
                player.getInventory().addItem(retrieved);
                player.sendMessage("§aVous avez récupéré §f" + retrieved.getAmount() + " " + 
                    mainProduct.name().toLowerCase().replace("_", " ") + " §a!");
            }
            
            // Effet sonore
            machine.location().getWorld().playSound(machine.location(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.0f);
        } else {
            player.sendMessage("§cCette machine est vide !");
        }
        
        // Afficher le contenu restant
        showMachineContent(player, machine);
    }
    
    private void showMachineContent(Player player, ActiveMachine machine) {
        String content = machine.getStorageDisplay();
        player.sendMessage("§7Contenu de la machine: " + content);

    }
}

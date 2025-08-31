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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les interactions avec les machines (shift+clic droit pour récupérer, placement automatique)
 */
public class MachineListener implements Listener {
    private final SkyTycoonPlugin plugin;
    // Map pour suivre la dernière machine ouverte par chaque joueur
    private final Map<UUID, Location> lastOpenedMachine = new ConcurrentHashMap<>();

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

        // Particules de placement
        location.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
            location.clone().add(0.5, 1, 0.5), 5, 0.3, 0.3, 0.3, 0.1);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Location location = event.getClickedBlock().getLocation();
        ActiveMachine machine = plugin.machines().getActiveMachine(location);
        if (machine == null) return;
        Player player = event.getPlayer();
        if (!machine.owner().equals(player.getUniqueId())) {
            player.sendMessage("§cCette machine ne vous appartient pas !");
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking()) {
            handleMachinePickup(player, machine, location);
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Ouvre le menu principal (chest) de la machine
            lastOpenedMachine.put(player.getUniqueId(), location);
            new MachineMainMenu(plugin, player, machine).open();
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        // Menu principal (chest)
        if (inv.getHolder() == null && inv.getSize() == 27 && inv.getItem(11) != null && inv.getItem(11).getType() == Material.CHEST) {
            Location loc = lastOpenedMachine.get(player.getUniqueId());
            if (loc == null) return;
            ActiveMachine machine = plugin.machines().getActiveMachine(loc);
            if (machine == null) return;
            new MachineMainMenu(plugin, player, machine).handleClick(event);
        }
        // Sous-menu stockage (hopper)
        else if (inv.getHolder() == null && inv.getType() == InventoryType.HOPPER && inv.getItem(2) != null && inv.getItem(2).getType() == Material.CHEST) {
            Location loc = lastOpenedMachine.get(player.getUniqueId());
            if (loc == null) return;
            ActiveMachine machine = plugin.machines().getActiveMachine(loc);
            if (machine == null) return;
            new MachineMenu(plugin, player, machine).handleClick(event);
        }
    }

    // ...autres méthodes (gestion des menus, onInventoryClick, etc.) à conserver ou compléter ici...
}

package fr.popo.skytycoon.command;

import fr.popo.skytycoon.SkyTycoonPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Commande /st is - Gestion des îles
 */
public class IslandCommand extends BaseCommand {
    
    public IslandCommand(SkyTycoonPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        
        // Si pas d'arguments, créer/téléporter vers l'île
        if (args.length == 0) {
            return handleCreateOrTeleport(player);
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "disband":
                return handleDisband(player, args);
            case "info":
                return handleInfo(player);
            case "storage":
                return handleStorage(player);
            case "machine":
                return handleMachine(player, args);
            default:
                player.sendMessage("§cSous-commande inconnue: " + subCommand);
                showIslandHelp(player);
                return true;
        }
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("disband", "info", "storage", "machine");
        }
        
        if (args.length == 2 && "disband".equals(args[0])) {
            return Arrays.asList("confirm");
        }
        
        if (args.length == 2 && "machine".equals(args[0])) {
            return Arrays.asList("give", "place");
        }
        
        if (args.length == 3 && "machine".equals(args[0]) && ("give".equals(args[1]) || "place".equals(args[1]))) {
            return Arrays.asList("basic_miner", "wood_cutter");
        }
        
        return Collections.emptyList();
    }
    
    private boolean handleCreateOrTeleport(Player player) {
        // Vérifier que le système d'îles est disponible
        if (plugin.islands() == null) {
            player.sendMessage("§cSystème d'îles non disponible. Le monde est en cours de création...");
            return true;
        }
        
        boolean first = plugin.islands().getIsland(player.getUniqueId()) == null;
        var island = plugin.islands().getOrCreate(player.getUniqueId(), first ? player : null);
        
        if (island == null) {
            player.sendMessage("§cImpossible de créer votre île. Le monde n'est pas encore prêt.");
            return true;
        }
        
        // Si l'île existait déjà, téléporter immédiatement
        if (!first) {
            var world = plugin.worldManager() != null ? plugin.worldManager().getSkyTycoonWorld() : null;
            var loc = world != null ? island.spawnLocation(world) : island.spawnLocation();
            
            if (loc != null) {
                Bukkit.getRegionScheduler().run(plugin, loc, task -> player.teleportAsync(loc));
                player.sendMessage("§aTéléportation vers votre île.");
            } else {
                player.sendMessage("§cImpossible de vous téléporter sur votre île.");
            }
        }
        
        if (first) {
            String startId = plugin.getConfig().getString("starting-machine", "basic_miner");
            var starter = plugin.machines().createMachineItem(startId);
            if (starter != null) {
                player.getInventory().addItem(starter);
                player.sendMessage("§eVous recevez votre première machine !");
            }
        }
        return true;
    }
    
    private boolean handleDisband(Player player, String[] args) {
        if (plugin.islands() == null) {
            player.sendMessage("§cSystème d'îles non disponible.");
            return true;
        }
        
        if (plugin.islands().getIsland(player.getUniqueId()) == null) {
            player.sendMessage("§cVous n'avez pas d'île à supprimer.");
            return true;
        }
        
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            player.sendMessage("§c§lATTENTION: §rCette action supprimera définitivement votre île !");
            player.sendMessage("§7Pour confirmer, tapez: §f/st is disband confirm");
            return true;
        }
        
        if (plugin.islands().disbandIsland(player.getUniqueId())) {
            player.sendMessage("§aVotre île a été supprimée avec succès.");
        } else {
            player.sendMessage("§cErreur lors de la suppression de votre île.");
        }
        return true;
    }
    
    private boolean handleInfo(Player player) {
        if (plugin.islands() == null) {
            player.sendMessage("§cSystème d'îles non disponible.");
            return true;
        }
        
        var island = plugin.islands().getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage("§cVous n'avez pas encore d'île. Utilisez §f/st is §cpour en créer une.");
            return true;
        }
        
        player.sendMessage("§e=== INFORMATIONS DE VOTRE ÎLE ===");
        player.sendMessage("§7Position: §f" + island.centerX() + "x, " + island.centerZ() + "z");
        player.sendMessage("§7Taille: §f" + island.size() + " blocs");
        player.sendMessage("§7Monde: §f" + island.worldName());
        return true;
    }
    
    private boolean handleStorage(Player player) {
        if (plugin.islands() == null) {
            player.sendMessage("§cSystème d'îles non disponible.");
            return true;
        }
        
        var island = plugin.islands().getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage("§cVous n'avez pas encore d'île. Utilisez §f/st is §cpour en créer une.");
            return true;
        }
        
        // Afficher le stockage de l'île
        player.sendMessage(island.getStorageInfo());
        return true;
    }
    
    private boolean handleMachine(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /st is machine <give|place> <type>");
            player.sendMessage("§7Types disponibles: basic_miner, wood_cutter");
            return true;
        }
        
        String action = args[1].toLowerCase();
        String machineType = args.length > 2 ? args[2] : "basic_miner";
        
        switch (action) {
            case "give":
                return handleGiveMachine(player, machineType);
            case "place":
                return handlePlaceMachine(player, machineType);
            default:
                player.sendMessage("§cAction inconnue: " + action);
                player.sendMessage("§7Actions: give, place");
                return true;
        }
    }
    
    private boolean handleGiveMachine(Player player, String machineType) {
        var item = plugin.machines().createMachineItem(machineType);
        if (item == null) {
            player.sendMessage("§cType de machine inconnu: " + machineType);
            return true;
        }
        
        player.getInventory().addItem(item);
        player.sendMessage("§aVous avez reçu une machine: " + machineType);
        return true;
    }
    
    private boolean handlePlaceMachine(Player player, String machineType) {
        // Placer directement la machine devant le joueur
        var location = player.getLocation().add(player.getLocation().getDirection().multiply(2));
        location.setY(Math.floor(location.getY()) + 1);
        location.getBlock().setType(org.bukkit.Material.COBBLESTONE);
        
        // Enregistrer la machine active
        plugin.machines().registerActive(player.getUniqueId(), machineType, location);
        
        player.sendMessage("§aMachine " + machineType + " placée et activée !");
        player.sendMessage("§7Elle commencera à produire dans quelques secondes...");
        return true;
    }
    
    private void showIslandHelp(Player player) {
        player.sendMessage("§e=== COMMANDES ÎLE ===");
        player.sendMessage("§7/st is §f- Créer ou aller sur votre île");
        player.sendMessage("§7/st is info §f- Informations sur votre île");
        player.sendMessage("§7/st is storage §f- Voir les ressources stockées");
        player.sendMessage("§7/st is machine give <type> §f- Recevoir une machine");
        player.sendMessage("§7/st is machine place <type> §f- Placer une machine");
        player.sendMessage("§7/st is disband §f- Supprimer votre île");
        player.sendMessage("");
        player.sendMessage("§e=== UTILISATION DES MACHINES ===");
        player.sendMessage("§7• Placez avec clic droit (ou commande)");
        player.sendMessage("§7• §fClic droit §7= récupérer 1 item + voir contenu");
        player.sendMessage("§7• §fShift + Clic droit §7= récupérer tout un stack");
        player.sendMessage("§7• §fShift + Clic gauche §7= récupérer la machine");
        player.sendMessage("§7• Stockage individuel par machine");
        player.sendMessage("§7• Affichage automatique du contenu");
    }
}

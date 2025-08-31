package fr.popo.skytycoon.command;

import fr.popo.skytycoon.SkyTycoonPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Classe de base pour toutes les commandes SkyTycoon
 */
public abstract class BaseCommand {
    
    protected final SkyTycoonPlugin plugin;
    
    public BaseCommand(SkyTycoonPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Exécute la commande
     */
    public abstract boolean execute(CommandSender sender, Command command, String label, String[] args);
    
    /**
     * Autocomplétion pour la commande
     */
    public abstract List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args);
    
    /**
     * Vérifie si le sender est un joueur et le renvoie
     */
    protected Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande est réservée aux joueurs.");
            return null;
        }
        return (Player) sender;
    }
    
    /**
     * Vérifie si le joueur a une permission
     */
    protected boolean checkPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return false;
        }
        return true;
    }
}

package fr.popo.skytycoon.command;

import fr.popo.skytycoon.SkyTycoonPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Commande /st reload - Recharger la configuration
 */
public class ReloadCommand extends BaseCommand {
    
    public ReloadCommand(SkyTycoonPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!checkPermission(sender, "skytycoon.admin.reload")) {
            return true;
        }
        
        try {
            plugin.reloadConfig();
            plugin.getLangManager().reload();
            plugin.getMachineManager().setLangManager(plugin.getLangManager());
            sender.sendMessage("§aConfiguration SkyTycoon rechargée avec succès !");
        } catch (Exception e) {
            sender.sendMessage("§cErreur lors du rechargement de la configuration: " + e.getMessage());
            plugin.getLogger().severe("Erreur lors du rechargement: " + e.getMessage());
        }
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

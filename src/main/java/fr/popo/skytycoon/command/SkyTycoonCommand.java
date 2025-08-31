package fr.popo.skytycoon.command;

import fr.popo.skytycoon.SkyTycoonPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;

/**
 * Commande principale /skytycoon
 */
public class SkyTycoonCommand extends BaseCommand {
    
    private final Map<String, BaseCommand> subCommands;
    
    public SkyTycoonCommand(SkyTycoonPlugin plugin) {
        super(plugin);
        this.subCommands = new HashMap<>();
        
        // Enregistrement des sous-commandes
        subCommands.put("is", new IslandCommand(plugin));
        subCommands.put("reload", new ReloadCommand(plugin));
    }
    
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        BaseCommand handler = subCommands.get(subCommand);
        
        if (handler == null) {
            sender.sendMessage("§cSous-commande inconnue: " + subCommand);
            showHelp(sender);
            return true;
        }
        
        // Créer un nouveau tableau d'arguments sans la sous-commande
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return handler.execute(sender, command, label, subArgs);
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Autocomplétion des sous-commandes
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            
            for (String subCmd : subCommands.keySet()) {
                if (subCmd.startsWith(partial)) {
                    completions.add(subCmd);
                }
            }
            return completions;
        }
        
        if (args.length > 1) {
            // Déléguer à la sous-commande
            String subCommand = args[0].toLowerCase();
            BaseCommand handler = subCommands.get(subCommand);
            
            if (handler != null) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return handler.tabComplete(sender, command, alias, subArgs);
            }
        }
        
        return Collections.emptyList();
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§e=== SKYTYCOON ===");
        sender.sendMessage("§7/st is §f- Gérer votre île");
        sender.sendMessage("§7/st reload §f- Recharger la configuration");
    }
}

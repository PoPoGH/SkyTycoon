package fr.popo.skytycoon;

import fr.popo.skytycoon.command.SkyTycoonCommand;
import fr.popo.skytycoon.config.LangManager;
import fr.popo.skytycoon.config.GlyphManager;
import fr.popo.skytycoon.machine.MachineListener;
import fr.popo.skytycoon.machine.MachineManager;
import fr.popo.skytycoon.player.IslandManager;
import fr.popo.skytycoon.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;

public class SkyTycoonPlugin extends JavaPlugin implements Listener {
    private static SkyTycoonPlugin instance;
    private MachineManager machineManager;
    private WorldManager worldManager;
    private IslandManager islandManager;
    private SkyTycoonCommand mainCommand;
    private LangManager langManager;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        // Sauvegarde les fichiers config par défaut si absent
        saveDefaultConfig();

        // Copier toutes les schematics du JAR si absentes
        copySchematicsFromJar();

        // Initialiser le GlyphManager
        GlyphManager glyphManager = new GlyphManager(getDataFolder());

        // Initialiser le LangManager en premier
        this.langManager = new LangManager(this);
        this.langManager.setGlyphManager(glyphManager);
        getLogger().info("LangManager initialisé avec support MiniMessage et GlyphManager");

        // Initialiser le système de commandes
        this.mainCommand = new SkyTycoonCommand(this);

        // Initialiser les managers dans le bon ordre
        this.machineManager = new MachineManager(this);

        // Configurer le LangManager pour les hologrammes
        machineManager.setLangManager(langManager);

        this.worldManager = new WorldManager(this);

        // Initialiser le monde de manière asynchrone
        worldManager.initializeWorld().thenAccept(world -> {
            if (world != null) {
                // Une fois le monde créé, initialiser l'IslandManager
                this.islandManager = new IslandManager(this, worldManager);
                getLogger().info("Système d'îles initialisé avec succès!");
            } else {
                getLogger().severe("Échec d'initialisation du monde, système d'îles désactivé");
            }
        });

        String version;
        try {
            version = Bukkit.getBukkitVersion();
        } catch (Exception ex) {
            // fallback
            version = "inconnu";
        }

        getLogger().info("SkyTycoon activé (Bukkit " + version + ")");
        
        // Listeners pour les machines
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new MachineListener(this), this);
    }

    @Override
    public void onDisable() {
        if (islandManager != null) {
            islandManager.shutdown();
        }
        getLogger().info("SkyTycoon désactivé");
    }

    public static SkyTycoonPlugin get() { return instance; }

    public MachineManager machines() { return machineManager; }
    public IslandManager islands() { return islandManager; }
    public WorldManager worldManager() { return worldManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("skytycoon")) return false;
        
        return mainCommand.execute(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("skytycoon")) {
            return mainCommand.tabComplete(sender, command, alias, args);
        }
        return new ArrayList<>();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        var item = event.getItemInHand();
        if (item != null && machineManager != null) {
            // Ici on pourrait ajouter de la logique pour les machines
            event.getPlayer().sendMessage("§aMachine placée !");
        }
    }
    
    // Getters pour accès aux managers
    public static SkyTycoonPlugin getInstance() {
        return instance;
    }
    
    public MachineManager getMachineManager() {
        return machineManager;
    }
    
    public WorldManager getWorldManager() {
        return worldManager;
    }
    
    public IslandManager getIslandManager() {
        return islandManager;
    }
    
    public LangManager getLangManager() {
        return langManager;
    }

    private void copySchematicsFromJar() {
        try {
            String path = "schematics/";
            URL jarUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
            String jarPath = URLDecoder.decode(jarUrl.getFile(), "UTF-8");
            try (JarFile jar = new JarFile(jarPath)) {
                Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith(path) && entry.getName().endsWith(".schem")) {
                        String fileName = entry.getName().substring(path.length());
                        java.io.File outFile = new java.io.File(getDataFolder(), path + fileName);
                        if (!outFile.exists()) {
                            outFile.getParentFile().mkdirs();
                            try (java.io.InputStream in = jar.getInputStream(entry);
                                 java.io.OutputStream out = new java.io.FileOutputStream(outFile)) {
                                byte[] buffer = new byte[4096];
                                int len;
                                while ((len = in.read(buffer)) > 0) {
                                    out.write(buffer, 0, len);
                                }
                            }
                            getLogger().info("Schematic copiée: " + fileName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("Erreur lors de la copie des schematics: " + e.getMessage());
        }
    }
}

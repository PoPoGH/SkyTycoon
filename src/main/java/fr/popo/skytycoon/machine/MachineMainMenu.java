package fr.popo.skytycoon.machine;

import fr.popo.skytycoon.SkyTycoonPlugin;
import fr.popo.skytycoon.config.LangManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MachineMainMenu {
    private final ActiveMachine machine;
    private final Player player;
    private final Inventory inventory;
    private final LangManager lang;
    private final SkyTycoonPlugin plugin;

    public MachineMainMenu(SkyTycoonPlugin plugin, Player player, ActiveMachine machine) {
        this.plugin = plugin;
        this.lang = plugin.getLangManager();
        this.player = player;
        this.machine = machine;
        // Utilise le nom dynamique depuis lang.yml (machine_names.<id>.name)
        this.inventory = Bukkit.createInventory(null, 27, lang.getMachineHologramName(machine.def().id(), machine.getLevel()));
        setupMenu();
    }

    private void setupMenu() {
        // Slot 11 : Catégorie Stockage (ouvre le sous-menu hopper)
        ItemStack storage = new ItemStack(Material.CHEST);
        ItemMeta storageMeta = storage.getItemMeta();
        storageMeta.displayName(lang.getMenuStorage().decoration(TextDecoration.ITALIC, false));
        storage.setItemMeta(storageMeta);
        inventory.setItem(11, storage);
        
        // Slot 13 : Catégorie Amélioration (placeholder)
        ItemStack upgrade = new ItemStack(Material.ANVIL);
        ItemMeta upgradeMeta = upgrade.getItemMeta();
        upgradeMeta.displayName(lang.getMenuUpgrade().decoration(TextDecoration.ITALIC, false));
        upgrade.setItemMeta(upgradeMeta);
        inventory.setItem(13, upgrade);
        
        // Slot 15 : Catégorie Paramètres (placeholder)
        ItemStack settings = new ItemStack(Material.COMPARATOR);
        ItemMeta settingsMeta = settings.getItemMeta();
        settingsMeta.displayName(lang.getMenuSettings().decoration(TextDecoration.ITALIC, false));
        settings.setItemMeta(settingsMeta);
        inventory.setItem(15, settings);
        
        // Slot 22 : Catégorie Informations (placeholder)
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(lang.getMenuInfo().decoration(TextDecoration.ITALIC, false));
        info.setItemMeta(infoMeta);
        inventory.setItem(22, info);
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        event.setCancelled(true);
        if (slot == 11) {
            // Ouvre le sous-menu stockage (hopper)
            new MachineMenu(plugin, player, machine).open();
        }
        // ...autres actions pour d'autres catégories ou boutons...
    }

    public void handleClose(InventoryCloseEvent event) {
        // Nettoyage si besoin
    }
}

package fr.popo.skytycoon.machine;

import fr.popo.skytycoon.SkyTycoonPlugin;
import fr.popo.skytycoon.config.LangManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MachineMenu {
    private final ActiveMachine machine;
    private final Player player;
    private final Inventory inventory;
    private final LangManager lang;
    private final SkyTycoonPlugin plugin;
    private final MachineMainMenu mainMenu;

    public MachineMenu(SkyTycoonPlugin plugin, Player player, ActiveMachine machine) {
        this.plugin = plugin;
        this.lang = plugin.getLangManager();
        this.player = player;
        this.machine = machine;
        this.mainMenu = new MachineMainMenu(plugin, player, machine);
        // Utilise un HOPPER 5 slots pour le sous-menu stockage
        this.inventory = Bukkit.createInventory(null, InventoryType.HOPPER,
            lang.getMachineHologramName(machine.def().id(), machine.getLevel()));
        setupMenu();
    }

    private void setupMenu() {
        // Slot 0 : Retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Retour"));
        back.setItemMeta(backMeta);
        inventory.setItem(0, back);

        // Slot 1 : Retirer 1
        ItemStack takeOne = new ItemStack(Material.HOPPER);
        ItemMeta takeOneMeta = takeOne.getItemMeta();
        takeOneMeta.displayName(Component.text("Retirer 1"));
        takeOne.setItemMeta(takeOneMeta);
        inventory.setItem(1, takeOne);

        // Slot 2 : Affichage stockage
        ItemStack info = new ItemStack(Material.CHEST);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text("Stockage: " + machine.getStorageDisplay()));
        info.setItemMeta(meta);
        inventory.setItem(2, info);

        // Slot 3 : Retirer un stack
        ItemStack takeStack = new ItemStack(Material.DROPPER);
        ItemMeta takeStackMeta = takeStack.getItemMeta();
        takeStackMeta.displayName(Component.text("Retirer un stack"));
        takeStack.setItemMeta(takeStackMeta);
        inventory.setItem(3, takeStack);

        // Slot 4 : Tout retirer
        ItemStack takeAll = new ItemStack(Material.BARREL);
        ItemMeta takeAllMeta = takeAll.getItemMeta();
        takeAllMeta.displayName(Component.text("Tout retirer"));
        takeAll.setItemMeta(takeAllMeta);
        inventory.setItem(4, takeAll);
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        event.setCancelled(true);
        switch (slot) {
            case 0 -> mainMenu.open(); // Retour
            case 1 -> plugin.machines().withdrawFromMachine(player, machine, 1);
            case 3 -> plugin.machines().withdrawFromMachine(player, machine, 64);
            case 4 -> plugin.machines().withdrawAllFromMachine(player, machine);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        // Nettoyage si besoin
    }
}

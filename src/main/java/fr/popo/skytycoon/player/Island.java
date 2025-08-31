package fr.popo.skytycoon.player;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Island {
    private final UUID owner;
    private final String worldName;
    private final int centerX;
    private final int centerZ;
    private final int size; // demi-taille (rayon carré)
    
    // Système de stockage automatique des ressources
    private final Map<Material, Integer> storage = new HashMap<>();

    public Island(UUID owner, String worldName, int centerX, int centerZ, int size) {
        this.owner = owner;
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size = size;
        initializeStorage();
    }
    
    private void initializeStorage() {
        // Initialiser avec 0 pour les ressources principales
        storage.put(Material.COBBLESTONE, 0);
        storage.put(Material.OAK_LOG, 0);
        storage.put(Material.COAL, 0);
        storage.put(Material.IRON_INGOT, 0);
    }

    public UUID owner() { return owner; }
    public String worldName() { return worldName; }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public int size() { return size; }

    public boolean contains(int x, int z) {
        return Math.abs(x - centerX) <= size && Math.abs(z - centerZ) <= size;
    }

    public Location spawnLocation() {
        World w = Bukkit.getWorld(worldName);
        System.out.println("[DEBUG] Island.spawnLocation() - worldName: '" + worldName + "', world found: " + (w != null ? w.getName() : "null"));
        
        if(w == null) {
            // Essayer de récupérer le monde par d'autres moyens si nécessaire
            System.out.println("[DEBUG] Monde introuvable, essai avec tous les mondes:");
            for (World world : Bukkit.getWorlds()) {
                System.out.println("[DEBUG] - Monde disponible: '" + world.getName() + "'");
            }
            return null;
        }
        return findSafeSpawnLocation(w);
    }
    
    /**
     * Version alternative qui utilise directement une référence au monde
     */
    public Location spawnLocation(World world) {
        if(world == null) return null;
        return findSafeSpawnLocation(world);
    }
    
    /**
     * Trouve un point de spawn sûr sur l'île en cherchant un bloc solide avec de l'air au-dessus
     */
    private Location findSafeSpawnLocation(World world) {
        System.out.println("[DEBUG] findSafeSpawnLocation() - monde: '" + world.getName() + "', centerX: " + centerX + ", centerZ: " + centerZ);
        
        // Commencer par le centre de l'île et chercher vers le haut à partir de Y=64
        int startY = 64; // Hauteur où la schématique est placée
        
        for (int y = startY; y <= startY + 20; y++) { // Chercher jusqu'à 20 blocs au-dessus
            Location loc = new Location(world, centerX + 0.5, y, centerZ + 0.5);
            
            try {
                // Vérifier si on a un bloc solide en dessous et de l'air au-dessus
                Location below = loc.clone().subtract(0, 1, 0);
                Location above = loc.clone().add(0, 1, 0);
                
                if (below.getBlock().getType().isSolid() && 
                    loc.getBlock().getType().isAir() && 
                    above.getBlock().getType().isAir()) {
                    System.out.println("[DEBUG] Safe spawn trouvé à Y=" + y);
                    return loc;
                }
            } catch (Exception e) {
                System.out.println("[ERROR] Erreur lors de la vérification du bloc à Y=" + y + ": " + e.getMessage());
                continue;
            }
        }
        
        // Si aucun endroit sûr n'est trouvé, utiliser Y=75 par défaut (au-dessus de la schématique)
        Location defaultLoc = new Location(world, centerX + 0.5, 75, centerZ + 0.5);
        System.out.println("[DEBUG] Utilisation de la position par défaut: " + defaultLoc);
        return defaultLoc;
    }
    
    // === SYSTÈME DE STOCKAGE ===
    
    /**
     * Ajoute des ressources au stockage de l'île
     */
    public void addToStorage(Material material, int amount) {
        storage.merge(material, amount, Integer::sum);
    }
    
    /**
     * Retire des ressources du stockage (si disponibles)
     */
    public boolean removeFromStorage(Material material, int amount) {
        int current = storage.getOrDefault(material, 0);
        if (current >= amount) {
            storage.put(material, current - amount);
            return true;
        }
        return false;
    }
    
    /**
     * Obtient la quantité stockée d'un matériau
     */
    public int getStorageAmount(Material material) {
        return storage.getOrDefault(material, 0);
    }
    
    /**
     * Obtient une copie du stockage complet
     */
    public Map<Material, Integer> getStorage() {
        return new HashMap<>(storage);
    }
    
    /**
     * Affiche les ressources stockées (pour debug/info)
     */
    public String getStorageInfo() {
        StringBuilder info = new StringBuilder("§e=== STOCKAGE ===\n");
        for (Map.Entry<Material, Integer> entry : storage.entrySet()) {
            if (entry.getValue() > 0) {
                info.append("§7").append(entry.getKey().name()).append(": §f").append(entry.getValue()).append("\n");
            }
        }
        return info.toString();
    }
}

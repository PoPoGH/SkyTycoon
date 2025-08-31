package fr.popo.skytycoon.machine;

public enum MachineType {
    BASIC_MINER,
    WOOD_CUTTER,
    CROP_FARM,
    MOB_GRINDER,
    SELL_STATION;
    
    /**
     * Convertit un ID de machine en MachineType
     */
    public static MachineType fromString(String id) {
        if (id == null) return null;
        
        switch (id.toLowerCase()) {
            case "basic_miner":
            case "basicminer":
            case "miner":
                return BASIC_MINER;
            case "wood_cutter":
            case "woodcutter":
            case "lumber":
                return WOOD_CUTTER;
            case "crop_farm":
            case "cropfarm":
            case "farm":
                return CROP_FARM;
            case "mob_grinder":
            case "mobgrinder":
            case "grinder":
                return MOB_GRINDER;
            case "sell_station":
            case "sellstation":
            case "shop":
                return SELL_STATION;
            default:
                return null;
        }
    }
}

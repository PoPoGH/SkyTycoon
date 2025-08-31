package fr.popo.skytycoon.machine;

import org.bukkit.Material;

public record MachineDefinition(
        MachineType type,
        String id,
        String displayName,
        Material blockMaterial,
        long baseIntervalTicks,
        int baseYield
) {}

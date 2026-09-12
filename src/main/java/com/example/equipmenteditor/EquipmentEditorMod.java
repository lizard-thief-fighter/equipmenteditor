package com.example.equipmenteditor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(EquipmentEditorMod.MOD_ID)
public final class EquipmentEditorMod {
    public static final String MOD_ID = "equipmenteditor";

    public EquipmentEditorMod(IEventBus modBus) {
        EquipmentConfig.load();
        NeoForge.EVENT_BUS.addListener(EquipmentModifier::modifyAttributes);
        NeoForge.EVENT_BUS.addListener(EquipmentModifier::modifyMiningSpeed);
    }
}

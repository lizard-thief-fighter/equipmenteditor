package com.example.equipmenteditor;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import java.util.Locale;
import java.util.Map;

@EventBusSubscriber(modid = EquipmentEditorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class EquipmentModifier {
    private static final String NAMESPACE = EquipmentEditorMod.MOD_ID;

    private EquipmentModifier() {}

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        EquipmentConfig.load();
    }

    @SubscribeEvent
    public static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (EquipmentConfig.Rule rule : EquipmentConfig.RULES) {
            Item item = BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.parse(rule.item))
                .orElse(null);

            if (item == null) continue;

            if (rule.durability != null) {
                int durability = Math.max(1, rule.durability);
                event.modify(item, builder ->
                    builder.set(DataComponents.MAX_DAMAGE, durability));
            }
        }
    }

    @SubscribeEvent
    public static void modifyAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        EquipmentConfig.Rule rule = findRule(itemId);
        if (rule == null) return;

        if (rule.attackDamage != null)
            replace(event, Attributes.ATTACK_DAMAGE, "attack_damage",
                rule.attackDamage - 1.0, EquipmentSlotGroup.MAINHAND);

        if (rule.attackSpeed != null)
            replace(event, Attributes.ATTACK_SPEED, "attack_speed",
                rule.attackSpeed - 4.0, EquipmentSlotGroup.MAINHAND);

        if (rule.armor != null)
            replace(event, Attributes.ARMOR, "armor",
                rule.armor, EquipmentSlotGroup.ANY);

        if (rule.armorToughness != null)
            replace(event, Attributes.ARMOR_TOUGHNESS, "armor_toughness",
                rule.armorToughness, EquipmentSlotGroup.ANY);

        if (rule.knockbackResistance != null)
            replace(event, Attributes.KNOCKBACK_RESISTANCE, "knockback_resistance",
                rule.knockbackResistance, EquipmentSlotGroup.ANY);

        if (rule.entityInteractionRange != null)
            replace(event, Attributes.ENTITY_INTERACTION_RANGE, "entity_interaction_range",
                rule.entityInteractionRange, EquipmentSlotGroup.ANY);

        if (rule.blockInteractionRange != null)
            replace(event, Attributes.BLOCK_INTERACTION_RANGE, "block_interaction_range",
                rule.blockInteractionRange, EquipmentSlotGroup.ANY);

        if (rule.movementSpeed != null)
            replace(event, Attributes.MOVEMENT_SPEED, "movement_speed",
                rule.movementSpeed, EquipmentSlotGroup.ANY);

        if (rule.attackKnockback != null)
            replace(event, Attributes.ATTACK_KNOCKBACK, "attack_knockback",
                rule.attackKnockback, EquipmentSlotGroup.MAINHAND);

        for (Map.Entry<String, Double> entry : rule.attributes.entrySet()) {
            Holder.Reference<Attribute> attribute =
                BuiltInRegistries.ATTRIBUTE
                    .getHolder(ResourceLocation.parse(entry.getKey()))
                    .orElse(null);

            if (attribute != null) {
                replace(event, attribute,
                    "attribute_" + entry.getKey(),
                    entry.getValue(),
                    EquipmentSlotGroup.ANY);
            }
        }
    }

    public static Double getMiningSpeedOverride(ItemStack stack) {
        EquipmentConfig.Rule rule = findRule(
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
        );
        return rule == null ? null : rule.miningSpeed;
    }

    public static Double getMiningSpeedMultiplier(ItemStack stack) {
        EquipmentConfig.Rule rule = findRule(
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
        );
        return rule == null ? null : rule.miningSpeedMultiplier;
    }

    public static int getEnchantability(ItemStack stack, int vanillaValue) {
        EquipmentConfig.Rule rule = findRule(
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
        );
        if (rule == null || rule.enchantability == null)
            return vanillaValue;
        return Math.max(0, rule.enchantability);
    }

    private static EquipmentConfig.Rule findRule(String itemId) {
        for (EquipmentConfig.Rule candidate : EquipmentConfig.RULES) {
            if (candidate.item.equals(itemId))
                return candidate;
        }
        return null;
    }

    private static void replace(
        ItemAttributeModifierEvent event,
        Holder<Attribute> attribute,
        String property,
        double amount,
        EquipmentSlotGroup slot
    ) {
        event.removeAllModifiersFor(attribute);

        AttributeModifier modifier = new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath(
                NAMESPACE,
                sanitize(property)
            ),
            amount,
            AttributeModifier.Operation.ADD_VALUE
        );

        event.addModifier(attribute, modifier, slot);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_./-]", "_");
    }
}

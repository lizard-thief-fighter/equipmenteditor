package com.example.equipmenteditor;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import java.util.Locale;
import java.util.Map;

@EventBusSubscriber(modid = EquipmentEditorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class EquipmentModifier {
    private static final String NAMESPACE = EquipmentEditorMod.MOD_ID;

    private EquipmentModifier() {}

    @SubscribeEvent
    public static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (EquipmentConfig.Rule rule : EquipmentConfig.RULES) {
            if (rule.durability == null && rule.unbreakable == null) continue;
            applyComponents(rule, event);
        }
    }

    private static void applyComponents(
        EquipmentConfig.Rule rule,
        ModifyDefaultComponentsEvent event
    ) {
        if (rule.item != null && !rule.item.isBlank()) {
            ResourceLocation id = ResourceLocation.tryParse(rule.item);
            if (id == null) return;

            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null) modifyItemComponents(item, rule, event);
            return;
        }

        if (rule.tag != null && !rule.tag.isBlank()) {
            TagKey<Item> tag = itemTag(rule.tag);
            if (tag == null) return;

            BuiltInRegistries.ITEM.getOrCreateTag(tag).forEach(holder ->
                modifyItemComponents(holder.value(), rule, event));
        }
    }

    private static void modifyItemComponents(
        Item item,
        EquipmentConfig.Rule rule,
        ModifyDefaultComponentsEvent event
    ) {
        event.modify(item, builder -> {
            if (rule.durability != null) {
                builder.set(DataComponents.MAX_DAMAGE, Math.max(1, rule.durability));
            }

            if (rule.unbreakable != null) {
                builder.set(
                    DataComponents.UNBREAKABLE,
                    rule.unbreakable ? new Unbreakable(true) : null
                );
            }
        });
    }

    @SubscribeEvent
    public static void modifyAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        EquipmentConfig.Rule rule = findRule(stack);
        if (rule == null) return;

        if (rule.attackDamage != null)
            replace(event, Attributes.ATTACK_DAMAGE, "attack_damage",
                rule.attackDamage - 1.0, EquipmentSlotGroup.MAINHAND);

        if (rule.attackSpeed != null)
            replace(event, Attributes.ATTACK_SPEED, "attack_speed",
                rule.attackSpeed - 4.0, EquipmentSlotGroup.MAINHAND);

        if (rule.armor != null)
            replace(event, Attributes.ARMOR, "armor", rule.armor, EquipmentSlotGroup.ANY);

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

        if (rule.attributes == null) return;

        for (Map.Entry<String, Double> entry : rule.attributes.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) continue;

            Holder.Reference<Attribute> attribute =
                BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);

            if (attribute != null) {
                replace(event, attribute,
                    "attribute_" + entry.getKey(),
                    entry.getValue(),
                    EquipmentSlotGroup.ANY);
            }
        }
    }

    public static Double getMiningSpeedOverride(ItemStack stack) {
        EquipmentConfig.Rule rule = findRule(stack);
        return rule == null ? null : rule.miningSpeed;
    }

    public static Double getMiningSpeedMultiplier(ItemStack stack) {
        EquipmentConfig.Rule rule = findRule(stack);
        return rule == null ? null : rule.miningSpeedMultiplier;
    }

    public static int getEnchantability(ItemStack stack, int vanillaValue) {
        EquipmentConfig.Rule rule = findRule(stack);
        if (rule == null || rule.enchantability == null) return vanillaValue;
        return Math.max(0, rule.enchantability);
    }

    private static EquipmentConfig.Rule findRule(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        for (EquipmentConfig.Rule candidate : EquipmentConfig.RULES) {
            if (candidate.item != null && candidate.item.equals(itemId)) return candidate;
        }

        for (EquipmentConfig.Rule candidate : EquipmentConfig.RULES) {
            if (candidate.tag != null && !candidate.tag.isBlank()) {
                TagKey<Item> tag = itemTag(candidate.tag);
                if (tag != null && stack.is(tag)) return candidate;
            }
        }

        return null;
    }

    private static TagKey<Item> itemTag(String tagId) {
        ResourceLocation id = ResourceLocation.tryParse(tagId);
        return id == null ? null : TagKey.create(Registries.ITEM, id);
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
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, sanitize(property)),
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

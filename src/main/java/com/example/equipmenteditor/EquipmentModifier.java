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
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@EventBusSubscriber(modid = EquipmentEditorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class EquipmentModifier {
    private static final String NAMESPACE = EquipmentEditorMod.MOD_ID;
    private static final String VANILLA_NAMESPACE = "minecraft";

    private static Map<String, EquipmentConfig.Rule> itemRules = Map.of();
    private static Map<TagKey<Item>, List<IndexedRule>> tagRules = Map.of();

    private EquipmentModifier() {}

    static void rebuildRuleIndex() {
        Map<String, EquipmentConfig.Rule> items = new HashMap<>();
        Map<TagKey<Item>, List<IndexedRule>> tags = new HashMap<>();

        for (int index = 0; index < EquipmentConfig.RULES.size(); index++) {
            EquipmentConfig.Rule rule = EquipmentConfig.RULES.get(index);
            if (rule == null) continue;

            if (rule.item != null && !rule.item.isBlank()) {
                items.putIfAbsent(rule.item, rule);
            }

            if (rule.tag != null && !rule.tag.isBlank()) {
                TagKey<Item> tag = itemTag(rule.tag);
                if (tag != null) {
                    tags.computeIfAbsent(tag, ignored -> new ArrayList<>())
                        .add(new IndexedRule(index, rule));
                }
            }
        }

        itemRules = Map.copyOf(items);
        tagRules = Map.copyOf(tags);
    }

    @SubscribeEvent
    public static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.getAllItems().forEach(item -> {
            ItemStack stack = new ItemStack(item);
            EquipmentConfig.Rule rule = resolveRule(stack);
            if (rule == null) return;

            event.modify(item, builder -> {
                if (rule.durability != null || rule.durabilityMultiplier != null) {
                    int baseDamage = stack.getMaxDamage();
                    double durability = rule.durability != null ? rule.durability : baseDamage;
                    if (rule.durabilityMultiplier != null) durability *= rule.durabilityMultiplier;
                    builder.set(DataComponents.MAX_DAMAGE, Math.max(1, (int) Math.round(durability)));
                }
                if (rule.unbreakable != null) {
                    builder.set(DataComponents.UNBREAKABLE,
                        rule.unbreakable ? new Unbreakable(true) : null);
                }
                if (rule.maxStackSize != null) {
                    builder.set(DataComponents.MAX_STACK_SIZE, Math.max(1, rule.maxStackSize));
                }
                if (rule.fireResistant != null) {
                    builder.set(DataComponents.FIRE_RESISTANT,
                        rule.fireResistant ? net.minecraft.util.Unit.INSTANCE : null);
                }
                if (rule.rarity != null) {
                    Rarity rarity = parseRarity(rule.rarity);
                    if (rarity != null) builder.set(DataComponents.RARITY, rarity);
                }
                if (rule.miningSpeed != null) {
                    Tool tool = stack.get(DataComponents.TOOL);
                    if (tool == null) {
                        builder.set(DataComponents.TOOL,
                            new Tool(List.of(), Math.max(0.0f, rule.miningSpeed.floatValue()), 1));
                    } else {
                        builder.set(DataComponents.TOOL,
                            new Tool(tool.rules(), Math.max(0.0f, rule.miningSpeed.floatValue()),
                                tool.damagePerBlock()));
                    }
                }
            });
        });
    }

    @SubscribeEvent
    public static void modifyAttributes(ItemAttributeModifierEvent event) {
        EquipmentConfig.Rule rule = resolveRule(event.getItemStack());
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

        if (rule.attributes != null) {
            for (Map.Entry<String, Double> entry : rule.attributes.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null) continue;
                Holder.Reference<Attribute> attribute =
                    BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
                if (attribute != null) {
                    replace(event, attribute, "attribute_" + entry.getKey(),
                        entry.getValue(), EquipmentSlotGroup.ANY);
                }
            }
        }
    }

    public static void modifyMiningSpeed(PlayerEvent.BreakSpeed event) {
        EquipmentConfig.Rule rule = resolveRule(event.getEntity().getMainHandItem());
        if (rule == null || rule.miningSpeedMultiplier == null) return;

        event.setNewSpeed(Math.max(0.0f,
            event.getNewSpeed() * Math.max(0.0f, rule.miningSpeedMultiplier.floatValue())));
    }

    private static EquipmentConfig.Rule resolveRule(ItemStack stack) {
        if (stack.isEmpty()) return null;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        EquipmentConfig.Rule exact = itemRules.get(itemId);
        List<IndexedRule> matches = new ArrayList<>();

        stack.getTags().forEach(tag -> {
            List<IndexedRule> rules = tagRules.get(tag);
            if (rules != null) matches.addAll(rules);
        });

        if (matches.isEmpty()) return exact;
        matches.sort((left, right) -> Integer.compare(left.index(), right.index()));

        EquipmentConfig.Rule resolved = new EquipmentConfig.Rule();
        for (IndexedRule match : matches) merge(resolved, match.rule());
        if (exact != null) merge(resolved, exact);
        return resolved;
    }

    private static void merge(EquipmentConfig.Rule target, EquipmentConfig.Rule source) {
        if (source.durability != null) target.durability = source.durability;
        if (source.unbreakable != null) target.unbreakable = source.unbreakable;
        if (source.maxStackSize != null) target.maxStackSize = source.maxStackSize;
        if (source.fireResistant != null) target.fireResistant = source.fireResistant;
        if (source.rarity != null) target.rarity = source.rarity;
        if (source.attackDamage != null) target.attackDamage = source.attackDamage;
        if (source.attackSpeed != null) target.attackSpeed = source.attackSpeed;
        if (source.armor != null) target.armor = source.armor;
        if (source.armorToughness != null) target.armorToughness = source.armorToughness;
        if (source.knockbackResistance != null) target.knockbackResistance = source.knockbackResistance;
        if (source.entityInteractionRange != null) target.entityInteractionRange = source.entityInteractionRange;
        if (source.blockInteractionRange != null) target.blockInteractionRange = source.blockInteractionRange;
        if (source.movementSpeed != null) target.movementSpeed = source.movementSpeed;
        if (source.attackKnockback != null) target.attackKnockback = source.attackKnockback;
        if (source.miningSpeed != null) target.miningSpeed = source.miningSpeed;
        if (source.miningSpeedMultiplier != null) target.miningSpeedMultiplier = source.miningSpeedMultiplier;
        if (source.durabilityMultiplier != null) target.durabilityMultiplier = source.durabilityMultiplier;
        if (source.attributes != null) target.attributes.putAll(source.attributes);
    }

    private static TagKey<Item> itemTag(String tagId) {
        ResourceLocation id = ResourceLocation.tryParse(tagId);
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }

    private static Rarity parseRarity(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> null;
        };
    }

    private static void replace(ItemAttributeModifierEvent event, Holder<Attribute> attribute,
                                String property, double amount, EquipmentSlotGroup slot) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, sanitize(property));
        event.removeIf(entry -> entry.attribute().equals(attribute)
            && (entry.modifier().id().getNamespace().equals(VANILLA_NAMESPACE)
                || entry.modifier().id().equals(id)));
        event.addModifier(attribute,
            new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE), slot);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }

    private record IndexedRule(int index, EquipmentConfig.Rule rule) {}
}

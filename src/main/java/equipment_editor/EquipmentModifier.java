package equipment_editor;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Equipable;
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
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = EquipmentEditorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class EquipmentModifier {
    private static final Logger LOGGER = EquipmentConfig.LOGGER;
    private static final String NAMESPACE = EquipmentEditorMod.MOD_ID;
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final int MAX_STACK_SIZE = 99;

    private static Map<String, EquipmentConfig.Rule> itemRules = Map.of();

    private EquipmentModifier() {}

    static void rebuildRuleIndex() {
        Map<String, EquipmentConfig.Rule> items = new HashMap<>();

        for (EquipmentConfig.Rule rule : EquipmentConfig.RULES) {
            if (rule == null || rule.items == null) continue;

            for (String item : rule.items) {
                if (item == null || item.isBlank()) continue;
                ResourceLocation id = ResourceLocation.tryParse(item.trim());
                if (id == null) {
                    LOGGER.warn("Invalid item ID \"{}\" in rules is ignored", item);
                    continue;
                }
                // Key by the canonical ID so "diamond_sword" and "minecraft:diamond_sword" match.
                items.merge(id.toString(), rule, EquipmentModifier::mergeRules);
            }
        }

        itemRules = Map.copyOf(items);
    }

    @SubscribeEvent
    public static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        Set<EquipmentConfig.Rule> checkedAttributes = Collections.newSetFromMap(new IdentityHashMap<>());

        itemRules.forEach((itemId, rule) -> {
            ResourceLocation id = ResourceLocation.parse(itemId);
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                LOGGER.warn("Unknown item \"{}\" in rules is ignored", itemId);
                return;
            }
            if (checkedAttributes.add(rule)) warnUnknownAttributes(rule);

            Item item = BuiltInRegistries.ITEM.get(id);
            event.modify(item, builder -> applyComponents(itemId, item.components(), rule, builder));
        });
    }

    private static void applyComponents(String itemId, DataComponentMap base, EquipmentConfig.Rule rule,
                                        DataComponentPatch.Builder builder) {
        int baseMaxDamage = base.getOrDefault(DataComponents.MAX_DAMAGE, 0);

        Integer maxDamage = null;
        if (rule.durability != null || rule.durabilityMultiplier != null) {
            double durability = rule.durability != null ? rule.durability : baseMaxDamage;
            if (rule.durabilityMultiplier != null) durability *= rule.durabilityMultiplier;
            long rounded = Math.round(durability);
            if (rounded >= 1) {
                maxDamage = (int) Math.min(rounded, Integer.MAX_VALUE);
            } else {
                LOGGER.warn("{}: durability resolves to {} (needs at least 1); ignored", itemId, rounded);
            }
        }
        boolean damageable = maxDamage != null || baseMaxDamage > 0;

        int stackSize = base.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
        if (rule.maxStackSize != null) {
            int requested = Mth.clamp(rule.maxStackSize, 1, MAX_STACK_SIZE);
            if (requested != rule.maxStackSize) {
                LOGGER.warn("{}: maxStackSize {} is outside 1-{}; using {}",
                    itemId, rule.maxStackSize, MAX_STACK_SIZE, requested);
            }
            if (requested > 1 && damageable) {
                LOGGER.warn("{}: items with durability cannot stack; maxStackSize ignored", itemId);
            } else {
                builder.set(DataComponents.MAX_STACK_SIZE, requested);
                stackSize = requested;
            }
        }

        if (maxDamage != null) {
            builder.set(DataComponents.MAX_DAMAGE, maxDamage);
            // An item only takes damage when it also has a DAMAGE component and cannot stack.
            if (!base.has(DataComponents.DAMAGE)) builder.set(DataComponents.DAMAGE, 0);
            if (stackSize > 1) builder.set(DataComponents.MAX_STACK_SIZE, 1);
        }

        if (rule.unbreakable != null) {
            if (rule.unbreakable) builder.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            else builder.remove(DataComponents.UNBREAKABLE);
        }
        if (rule.fireResistant != null) {
            if (rule.fireResistant) builder.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
            else builder.remove(DataComponents.FIRE_RESISTANT);
        }
        if (rule.rarity != null) {
            Rarity rarity = parseRarity(rule.rarity);
            if (rarity != null) builder.set(DataComponents.RARITY, rarity);
            else LOGGER.warn("{}: unknown rarity \"{}\"; ignored", itemId, rule.rarity);
        }
        if (rule.miningSpeed != null) {
            float speed = Math.max(0.0f, rule.miningSpeed.floatValue());
            Tool tool = base.get(DataComponents.TOOL);
            builder.set(DataComponents.TOOL,
                tool == null ? new Tool(List.of(), speed, 1) : withMiningSpeed(tool, speed));
        }
    }

    /**
     * Sets the speed a tool mines its proper blocks at. Tools mine those blocks at the speeds in their
     * rules, not the default speed, so the fastest rule becomes {@code speed} and the other rules
     * keep their proportions to it. A tool without rule speeds gets {@code speed} as its default.
     */
    private static Tool withMiningSpeed(Tool tool, float speed) {
        float fastest = 0.0f;
        for (Tool.Rule rule : tool.rules()) {
            if (rule.speed().isPresent()) fastest = Math.max(fastest, rule.speed().get());
        }
        if (fastest <= 0.0f) return new Tool(tool.rules(), speed, tool.damagePerBlock());

        float scale = speed / fastest;
        List<Tool.Rule> rules = tool.rules().stream()
            .map(rule -> rule.speed().isEmpty() ? rule
                : new Tool.Rule(rule.blocks(), rule.speed().map(s -> s * scale), rule.correctForDrops()))
            .toList();
        return new Tool(rules, tool.defaultMiningSpeed(), tool.damagePerBlock());
    }

    public static void modifyAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        EquipmentConfig.Rule rule = resolveRule(stack);
        if (rule == null) return;

        // Armor-type bonuses apply where the item is worn (main hand for tools and weapons).
        EquipmentSlotGroup worn = slotGroupFor(stack);
        String itemNamespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();

        // Attack damage and speed reuse vanilla's base IDs so tooltips show totals, as vanilla does.
        if (rule.attackDamage != null)
            replace(event, itemNamespace, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID,
                rule.attackDamage - 1.0, EquipmentSlotGroup.MAINHAND);
        if (rule.attackSpeed != null)
            replace(event, itemNamespace, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID,
                rule.attackSpeed - 4.0, EquipmentSlotGroup.MAINHAND);
        if (rule.attackKnockback != null)
            replace(event, itemNamespace, Attributes.ATTACK_KNOCKBACK, "attack_knockback",
                rule.attackKnockback, EquipmentSlotGroup.MAINHAND);
        if (rule.armor != null)
            replace(event, itemNamespace, Attributes.ARMOR, "armor", rule.armor, worn);
        if (rule.armorToughness != null)
            replace(event, itemNamespace, Attributes.ARMOR_TOUGHNESS, "armor_toughness",
                rule.armorToughness, worn);
        if (rule.knockbackResistance != null)
            replace(event, itemNamespace, Attributes.KNOCKBACK_RESISTANCE, "knockback_resistance",
                rule.knockbackResistance, worn);
        if (rule.entityInteractionRange != null)
            replace(event, itemNamespace, Attributes.ENTITY_INTERACTION_RANGE, "entity_interaction_range",
                rule.entityInteractionRange, worn);
        if (rule.blockInteractionRange != null)
            replace(event, itemNamespace, Attributes.BLOCK_INTERACTION_RANGE, "block_interaction_range",
                rule.blockInteractionRange, worn);
        if (rule.movementSpeed != null)
            replace(event, itemNamespace, Attributes.MOVEMENT_SPEED, "movement_speed",
                rule.movementSpeed, worn);

        if (rule.attributes != null) {
            for (Map.Entry<String, Double> entry : rule.attributes.entrySet()) {
                if (entry.getValue() == null) continue;
                Holder<Attribute> attribute = attributeHolder(entry.getKey());
                if (attribute != null) {
                    replace(event, itemNamespace, attribute, "attribute_" + entry.getKey(),
                        entry.getValue(), worn);
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
        return itemRules.get(itemId);
    }

    private static EquipmentSlotGroup slotGroupFor(ItemStack stack) {
        EquipmentSlot slot = stack.getEquipmentSlot();
        if (slot == null) {
            Equipable equipable = Equipable.get(stack);
            if (equipable != null) slot = equipable.getEquipmentSlot();
        }
        return slot == null ? EquipmentSlotGroup.MAINHAND : EquipmentSlotGroup.bySlot(slot);
    }

    private static Holder<Attribute> attributeHolder(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id == null ? null : BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
    }

    private static void warnUnknownAttributes(EquipmentConfig.Rule rule) {
        if (rule.attributes == null) return;
        for (String key : rule.attributes.keySet()) {
            if (attributeHolder(key) == null) {
                LOGGER.warn("Unknown attribute \"{}\" in rules is ignored", key);
            }
        }
    }

    private static EquipmentConfig.Rule mergeRules(EquipmentConfig.Rule first,
                                                    EquipmentConfig.Rule second) {
        EquipmentConfig.Rule merged = new EquipmentConfig.Rule();
        merge(merged, first);
        merge(merged, second);
        return merged;
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

    private static Rarity parseRarity(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> null;
        };
    }

    private static void replace(ItemAttributeModifierEvent event, String itemNamespace,
                                Holder<Attribute> attribute, String property, double amount,
                                EquipmentSlotGroup slot) {
        // One ID per slot group, like vanilla armor, so worn pieces stack instead of overwriting.
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE,
            sanitize(property) + "." + slot.getSerializedName());
        replace(event, itemNamespace, attribute, id, amount, slot);
    }

    /**
     * Replaces the item's own modifiers for {@code attribute}: vanilla's, the item's mod's, and ours.
     * Modifiers added by other mods are left alone.
     */
    private static void replace(ItemAttributeModifierEvent event, String itemNamespace,
                                Holder<Attribute> attribute, ResourceLocation id, double amount,
                                EquipmentSlotGroup slot) {
        event.removeIf(entry -> {
            if (!entry.attribute().equals(attribute)) return false;
            String namespace = entry.modifier().id().getNamespace();
            return namespace.equals(VANILLA_NAMESPACE) || namespace.equals(itemNamespace)
                || namespace.equals(NAMESPACE);
        });
        event.addModifier(attribute,
            new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE), slot);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }
}

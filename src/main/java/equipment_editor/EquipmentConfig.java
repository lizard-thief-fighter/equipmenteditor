package equipment_editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class EquipmentConfig {
    private EquipmentConfig() {}

    static final Logger LOGGER = LogUtils.getLogger();

    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("equipmenteditor.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> RULE_KEYS = Arrays.stream(Rule.class.getFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .map(Field::getName)
        .collect(Collectors.toUnmodifiableSet());

    public static List<Rule> RULES = new ArrayList<>();

    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                RULES = defaultRules();
                save();
            } else {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    if (!json.isJsonObject()) {
                        throw new JsonParseException("Expected an object with a \"rules\" array");
                    }
                    warnUnknownKeys(json.getAsJsonObject());
                    ConfigFile config = GSON.fromJson(json, ConfigFile.class);
                    RULES = config == null || config.rules == null ? new ArrayList<>() : config.rules;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Could not load {}", CONFIG_PATH, e);
            RULES = new ArrayList<>();
        }
        EquipmentModifier.rebuildRuleIndex();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(new ConfigFile(RULES), writer);
            }
        } catch (IOException e) {
            LOGGER.error("Could not save {}", CONFIG_PATH, e);
        }
    }

    /** Gson silently ignores unrecognized keys, so report them instead of letting typos do nothing. */
    private static void warnUnknownKeys(JsonObject root) {
        for (String key : root.keySet()) {
            if (!key.equals("rules")) LOGGER.warn("Unknown key \"{}\" in {} is ignored", key, CONFIG_PATH);
        }
        JsonElement rules = root.get("rules");
        if (rules == null || !rules.isJsonArray()) return;
        for (JsonElement rule : rules.getAsJsonArray()) {
            if (!rule.isJsonObject()) continue;
            for (String key : rule.getAsJsonObject().keySet()) {
                if (!RULE_KEYS.contains(key)) {
                    LOGGER.warn("Unknown rule key \"{}\" in {} is ignored", key, CONFIG_PATH);
                }
            }
        }
    }

    private static List<Rule> defaultRules() {
        List<Rule> rules = new ArrayList<>();
        Rule sword = new Rule(List.of("minecraft:diamond_sword"));
        sword.durability = 2500;
        sword.attackDamage = 10.0;
        sword.attackSpeed = 1.8;
        rules.add(sword);

        Rule chestplate = new Rule(List.of("minecraft:diamond_chestplate"));
        chestplate.durability = 1000;
        chestplate.armor = 10.0;
        chestplate.armorToughness = 5.0;
        rules.add(chestplate);
        return rules;
    }

    public static final class ConfigFile {
        public List<Rule> rules;
        public ConfigFile(List<Rule> rules) { this.rules = rules; }
    }

    public static final class Rule {
        public List<String> items = new ArrayList<>();
        public Integer durability;
        public Boolean unbreakable;
        public Integer maxStackSize;
        public Boolean fireResistant;
        public String rarity;
        public Double attackDamage;
        public Double attackSpeed;
        public Double armor;
        public Double armorToughness;
        public Double knockbackResistance;
        public Double entityInteractionRange;
        public Double blockInteractionRange;
        public Double movementSpeed;
        public Double attackKnockback;
        public Double miningSpeed;
        public Double miningSpeedMultiplier;
        public Double durabilityMultiplier;
        public Map<String, Double> attributes = new HashMap<>();

        public Rule(List<String> items) { this.items = items; }
        public Rule() {}
    }
}

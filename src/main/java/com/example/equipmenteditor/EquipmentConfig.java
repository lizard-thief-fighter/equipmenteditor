package com.example.equipmenteditor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EquipmentConfig {
    private EquipmentConfig() {}

    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("equipmenteditor.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static List<Rule> RULES = new ArrayList<>();

    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                RULES = defaultRules();
                save();
            } else {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    ConfigFile config = GSON.fromJson(reader, ConfigFile.class);
                    RULES = config == null || config.rules == null ? new ArrayList<>() : config.rules;
                }
            }
        } catch (Exception e) {
            System.err.println("[Equipment Editor] Could not load " + CONFIG_PATH);
            e.printStackTrace();
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
            System.err.println("[Equipment Editor] Could not save " + CONFIG_PATH);
            e.printStackTrace();
        }
    }

    private static List<Rule> defaultRules() {
        List<Rule> rules = new ArrayList<>();
        Rule sword = new Rule("minecraft:diamond_sword");
        sword.durability = 2500;
        sword.attackDamage = 10.0;
        sword.attackSpeed = 1.8;
        rules.add(sword);

        Rule chestplate = new Rule("minecraft:diamond_chestplate");
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
        public String item;
        public String tag;
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
        public Integer durabilityMultiplier;
        public Map<String, Double> attributes = new HashMap<>();

        public Rule(String item) { this.item = item; }
        public Rule() {}
    }
}

package com.example.equipmenteditor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class EquipmentConfig {
    private EquipmentConfig() {}

    public static final Path CONFIG_DIRECTORY = FMLPaths.CONFIGDIR.get().resolve("equipmenteditor");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static List<Rule> RULES = new ArrayList<>();

    public static void load() {
        List<Rule> rules = new ArrayList<>();
        try {
            Files.createDirectories(CONFIG_DIRECTORY);
            List<Path> files = discoverRuleFiles();
            if (files.isEmpty()) {
                Path defaults = CONFIG_DIRECTORY.resolve("default.json");
                try (Writer writer = Files.newBufferedWriter(defaults, StandardOpenOption.CREATE_NEW)) {
                    GSON.toJson(defaultRules(), writer);
                }
                files = List.of(defaults);
            }

            for (Path file : files) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    Rule[] fileRules = GSON.fromJson(reader, Rule[].class);
                    if (fileRules == null) {
                        throw new JsonParseException("Expected a JSON array of rule objects");
                    }
                    rules.addAll(Arrays.asList(fileRules));
                } catch (IOException | JsonParseException e) {
                    System.err.println("[Equipment Editor] Could not load rules from " + file);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("[Equipment Editor] Could not load rule directory " + CONFIG_DIRECTORY);
            e.printStackTrace();
        }
        RULES = rules;
        EquipmentModifier.rebuildRuleIndex();
    }

    private static List<Path> discoverRuleFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(CONFIG_DIRECTORY)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                // Normalize separators so ordering is the same on Windows and Unix.
                .sorted(Comparator.comparing(path -> CONFIG_DIRECTORY.relativize(path)
                    .toString().replace('\\', '/')))
                .toList();
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
        public Integer durabilityMultiplier;
        public Map<String, Double> attributes = new HashMap<>();

        public Rule(List<String> items) { this.items = items; }
        public Rule() {}
    }
}

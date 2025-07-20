package troy.autofish.gui;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import troy.autofish.FabricModAutofish;
import troy.autofish.config.Config;

import java.util.function.Function;

public class AutofishScreenBuilder {

    private static final Function<Boolean, Text> yesNoTextSupplier = bool -> {
        if (bool) return Text.translatable("options.autofish.toggle.on");
        else return Text.translatable("options.autofish.toggle.off");
    };

    public static Screen buildScreen(FabricModAutofish modAutofish, Screen parentScreen) {

        Config defaults = new Config();
        Config config = modAutofish.getConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parentScreen)
                .setTitle(Text.translatable("options.autofish.title"))
                .transparentBackground()
                .setDoesConfirmSave(true)
                .setSavingRunnable(() -> {
                    modAutofish.getConfig().enforceConstraints();
                    modAutofish.getConfigManager().writeConfig(true);
                });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory configCat = builder.getOrCreateCategory(Text.translatable("options.autofish.config"));

        // Saved Coordinates Field (manual entry)
        AbstractConfigListEntry<?> savedCoordsField = entryBuilder.startTextField(
                Text.literal("Saved Coordinates (x, y, z)"),
                String.format("%.2f, %.2f, %.2f", config.getSavedX(), config.getSavedY(), config.getSavedZ())
        )
        .setTooltip(Text.literal("Edit or paste coordinates here. Format: x, y, z"))
        .setSaveConsumer(str -> {
            String[] parts = str.split(",");
            if (parts.length == 3) {
                try {
                    double x = Double.parseDouble(parts[0].trim());
                    double y = Double.parseDouble(parts[1].trim());
                    double z = Double.parseDouble(parts[2].trim());
                    modAutofish.getConfig().setSavedCoords(x, y, z);
                } catch (NumberFormatException ignored) {}
            }
        })
        .build();

        // Set Saved Coordinates to Current Position Toggle
        AbstractConfigListEntry<?> setCoordsToCurrentToggle = entryBuilder.startBooleanToggle(
                Text.literal("Set Saved Coordinates to Current Position"),
                false
        )
        .setTooltip(Text.literal("Enable to set the saved coordinates to your current player position."))
        .setSaveConsumer(newValue -> {
            if (newValue && MinecraftClient.getInstance().player != null) {
                double x = MinecraftClient.getInstance().player.getX();
                double y = MinecraftClient.getInstance().player.getY();
                double z = MinecraftClient.getInstance().player.getZ();
                modAutofish.getConfig().setSavedCoords(x, y, z);
                modAutofish.getConfigManager().writeConfig(true);
            }
        })
        .setYesNoTextSupplier(yesNoTextSupplier)
        .build();

        // Only Autofish at Saved Coordinates Toggle
        AbstractConfigListEntry onlyAtCoordsToggle = entryBuilder.startBooleanToggle(
                Text.translatable("options.autofish.only_at_coords.title"),
                config.isOnlyAutofishAtSavedCoords())
                .setDefaultValue(false)
                .setTooltip(Text.translatable("options.autofish.only_at_coords.tooltip"))
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setOnlyAutofishAtSavedCoords(newValue);
                })
                .setYesNoTextSupplier(yesNoTextSupplier)
                .build();

        //Enable Autofish
        AbstractConfigListEntry toggleAutofish = entryBuilder.startBooleanToggle(Text.translatable("options.autofish.enable.title"), config.isAutofishEnabled())
                .setDefaultValue(defaults.isAutofishEnabled())
                .setTooltip(Text.translatable("options.autofish.enable.tooltip"))
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setAutofishEnabled(newValue);
                })
                .setYesNoTextSupplier(yesNoTextSupplier)
                .build();

        //Enable MultiRod
        AbstractConfigListEntry toggleMultiRod = entryBuilder.startBooleanToggle(Text.translatable("options.autofish.multirod.title"), config.isMultiRod())
                .setDefaultValue(defaults.isMultiRod())
                .setTooltip(
                        Text.translatable("options.autofish.multirod.tooltip_0"),
                        Text.translatable("options.autofish.multirod.tooltip_1"),
                        Text.translatable("options.autofish.multirod.tooltip_2")
                )
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setMultiRod(newValue);
                })
                .setYesNoTextSupplier(yesNoTextSupplier)
                .build();

        //Enable Open Water Detection
        AbstractConfigListEntry toggleOpenWaterDetection = entryBuilder.startBooleanToggle(Text.translatable("options.autofish.open_water_detection.title"), config.isOpenWaterDetectEnabled())
                .setDefaultValue(defaults.isOpenWaterDetectEnabled())
                .setTooltip(
                        Text.translatable("options.autofish.open_water_detection.tooltip_0"),
                        Text.translatable("options.autofish.open_water_detection.tooltip_1"),
                        Text.translatable("options.autofish.open_water_detection.tooltip_2")
                )
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setOpenWaterDetectEnabled(newValue);
                })
                .setYesNoTextSupplier(yesNoTextSupplier)
                .build();
        //Enable Break Protection
        AbstractConfigListEntry toggleBreakProtection = entryBuilder.startBooleanToggle(Text.translatable("options.autofish.break_protection.title"), config.isNoBreak())
                .setDefaultValue(defaults.isNoBreak())
                .setTooltip(
                        Text.translatable("options.autofish.break_protection.tooltip_0"),
                        Text.translatable("options.autofish.break_protection.tooltip_1")
                )
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setNoBreak(newValue);
                })
                .setYesNoTextSupplier(yesNoTextSupplier)
                .build();

        //Enable Persistent Mode
        AbstractConfigListEntry togglePersistentMode = entryBuilder.startBooleanToggle(Text.translatable("options.autofish.persistent.title"), config.isPersistentMode())
                .setDefaultValue(defaults.isPersistentMode())
                .setTooltip(
                        Text.translatable("options.autofish.persistent.tooltip_0"),
                        Text.translatable("options.autofish.persistent.tooltip_1"),
                        Text.translatable("options.autofish.persistent.tooltip_2"),
                        Text.translatable("options.autofish.persistent.tooltip_3"),
                        Text.translatable("options.autofish.persistent.tooltip_4"),
                        Text.translatable("options.autofish.persistent.tooltip_5")
                )
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setPersistentMode(newValue);
                })
                .setYesNoTextSupplier(yesNoTextSupplier)
                .build();


        //Enable Sound Detection
        AbstractConfigListEntry toggleSoundDetection = entryBuilder.startBooleanToggle(Text.translatable("options.autofish.sound.title"), config.isUseSoundDetection())
                .setDefaultValue(defaults.isUseSoundDetection())
                .setTooltip(
                        Text.translatable("options.autofish.sound.tooltip_0"),
                        Text.translatable("options.autofish.sound.tooltip_1"),
                        Text.translatable("options.autofish.sound.tooltip_2"),
                        Text.translatable("options.autofish.sound.tooltip_3"),
                        Text.translatable("options.autofish.sound.tooltip_4"),
                        Text.translatable("options.autofish.sound.tooltip_5"),
                        Text.translatable("options.autofish.sound.tooltip_6"),
                        Text.translatable("options.autofish.sound.tooltip_7"),
                        Text.translatable("options.autofish.sound.tooltip_8"),
                        Text.translatable("options.autofish.sound.tooltip_9")
                )
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setUseSoundDetection(newValue);
                    modAutofish.getAutofish().setDetection();
                })
                .setYesNoTextSupplier(yesNoTextSupplier)
                .build();

        //Enable Force MP Detection
        AbstractConfigListEntry toggleForceMPDetection = entryBuilder.startBooleanToggle(Text.translatable("options.autofish.multiplayer_compat.title"), config.isForceMPDetection())
                .setDefaultValue(defaults.isPersistentMode())
                .setTooltip(
                        Text.translatable("options.autofish.multiplayer_compat.tooltip_0"),
                        Text.translatable("options.autofish.multiplayer_compat.tooltip_1"),
                        Text.translatable("options.autofish.multiplayer_compat.tooltip_2")
                )
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setForceMPDetection(newValue);
                })
                .setYesNoTextSupplier(yesNoTextSupplier)
                .build();

        //Recast Delay
        AbstractConfigListEntry recastDelaySlider = entryBuilder.startLongSlider(Text.translatable("options.autofish.recast_delay.title"), config.getRecastDelay(), 500, 5000)
                .setDefaultValue(defaults.getRecastDelay())
                .setTooltip(
                        Text.translatable("options.autofish.recast_delay.tooltip_0"),
                        Text.translatable("options.autofish.recast_delay.tooltip_1")
                )
                .setTextGetter(value -> Text.translatable("options.autofish.recast_delay.value", value))
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setRecastDelay(newValue);
                })
                .build();
        AbstractConfigListEntry randomDelaySlider = entryBuilder.startLongSlider(Text.translatable("options.autofish.random_delay.title"), config.getRandomDelay(), 0, 75)
                .setDefaultValue(defaults.getRandomPercent())
                .setTooltip(
                        Text.translatable("options.autofish.random_delay.tooltip_0"),
                        Text.translatable("options.autofish.random_delay.tooltip_1"),
                        Text.translatable("options.autofish.random_delay.tooltip_2"),
                        Text.translatable("options.autofish.random_delay.tooltip_3")
                )
                .setTextGetter(value -> Text.translatable("options.autofish.random_delay.value", value))
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setRandomDelay(newValue);
                })
                .build();
        AbstractConfigListEntry reelInDelay = entryBuilder.startLongSlider(Text.translatable("options.autofish.reel_in_delay.title"), config.getReelInDelay(), 1, 2000)
                .setDefaultValue(defaults.getReelInDelay())
                .setTooltip(
                        Text.translatable("options.autofish.reel_in_delay.tooltip_0"),
                        Text.translatable("options.autofish.reel_in_delay.tooltip_1")
                )
                .setTextGetter(value -> Text.translatable("options.autofish.reel_in_delay.value", value))
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setReelInDelay(newValue);
                })
                .build();

        //ClearLag Regex
        AbstractConfigListEntry clearLagRegexField = entryBuilder.startTextField(Text.translatable("options.autofish.clear_regex.title"), config.getClearLagRegex())
                .setDefaultValue(defaults.getClearLagRegex())
                .setTooltip(
                        Text.translatable("options.autofish.clear_regex.tooltip_0"),
                        Text.translatable("options.autofish.clear_regex.tooltip_1"),
                        Text.translatable("options.autofish.clear_regex.tooltip_2")
                )
                .setSaveConsumer(newValue -> {
                    modAutofish.getConfig().setClearLagRegex(newValue);
                })
                .build();


        SubCategoryBuilder subCatBuilderBasic = entryBuilder.startSubCategory(Text.translatable("options.autofish.basic.title"));
        subCatBuilderBasic.add(toggleAutofish);
        subCatBuilderBasic.add(toggleMultiRod);
        subCatBuilderBasic.add(toggleOpenWaterDetection);
        subCatBuilderBasic.add(toggleBreakProtection);
        subCatBuilderBasic.add(togglePersistentMode);
        subCatBuilderBasic.add(savedCoordsField);
        subCatBuilderBasic.add(setCoordsToCurrentToggle);
        subCatBuilderBasic.add(onlyAtCoordsToggle);
        subCatBuilderBasic.setExpanded(true);

        SubCategoryBuilder subCatBuilderAdvanced = entryBuilder.startSubCategory(Text.translatable("options.autofish.advanced.title"));
        // Auto Toss Items Toggle
        AbstractConfigListEntry<?> autoTossToggle = entryBuilder.startBooleanToggle(
                Text.literal("Enable Auto Toss Items"),
                config.isAutoTossEnabled()
        )
        .setDefaultValue(false)
        .setTooltip(Text.literal("Automatically tosses specified items if your inventory is full. Only affects main inventory, not hotbar or held items."))
        .setSaveConsumer(newValue -> {
            modAutofish.getConfig().setAutoTossEnabled(newValue);
        })
        .setYesNoTextSupplier(yesNoTextSupplier)
        .build();

        // Auto Toss Items List Field
        AbstractConfigListEntry<?> autoTossItemsField = entryBuilder.startTextField(
                Text.literal("Auto Toss Items (comma-separated)"),
                config.getAutoTossItems()
        )
        .setTooltip(Text.literal("List of items to toss when inventory is full. Use Minecraft item IDs, e.g.: minecraft:pufferfish,minecraft:bow,minecraft:enchanted_book,minecraft:fishing_rod"))
        .setSaveConsumer(newValue -> {
            modAutofish.getConfig().setAutoTossItems(newValue);
        })
        .build();

        subCatBuilderAdvanced.add(toggleSoundDetection);
        subCatBuilderAdvanced.add(autoTossToggle);
        subCatBuilderAdvanced.add(autoTossItemsField);
        subCatBuilderAdvanced.add(toggleForceMPDetection);
        subCatBuilderAdvanced.add(recastDelaySlider);
        subCatBuilderAdvanced.add(randomDelaySlider);
        subCatBuilderAdvanced.add(reelInDelay);
        subCatBuilderAdvanced.add(clearLagRegexField);
        subCatBuilderAdvanced.setExpanded(true);

        configCat.addEntry(subCatBuilderBasic.build());
        configCat.addEntry(subCatBuilderAdvanced.build());

        return builder.build();

    }
}

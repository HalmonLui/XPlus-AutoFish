package troy.autofish.config;

import com.google.gson.annotations.Expose;

public class Config {

    @Expose boolean isAutofishEnabled = true;
    @Expose boolean multiRod = false;
    @Expose boolean isOpenWaterDetectEnabled = true;
    @Expose boolean noBreak = false;
    @Expose boolean persistentMode = false;
    @Expose boolean useSoundDetection = false;
    @Expose boolean forceMPDetection = false;
    @Expose long recastDelay = 1500;
    @Expose long randomPercent = 50;
    @Expose long reelInDelay = 1;
    @Expose String clearLagRegex = "[ClearLag] Removed [0-9]+ Entities!";

    // New fields for saved coordinates and toggle
    @Expose private double savedX = 0;
    @Expose private double savedY = 0;
    @Expose private double savedZ = 0;
    @Expose private boolean onlyAutofishAtSavedCoords = false;

        // Auto Toss Items
    @Expose private boolean autoTossEnabled = false;
    @Expose private String autoTossItems = "pufferfish,bow,enchanted book,fishing rod";

    // Auto Toss Stack Behind
    @Expose private boolean autoTossStackBehindEnabled = false;
    @Expose private String autoTossStackBehindItems = "pufferfish,bow,enchanted book,fishing rod";

    // Blossom/Auto Flower
    @Expose private boolean autoFlowerEnabled = false;

    public boolean isAutoFlowerEnabled() { return autoFlowerEnabled; }
    public void setAutoFlowerEnabled(boolean value) { this.autoFlowerEnabled = value; }

    public boolean isAutoTossEnabled() { return autoTossEnabled; }
    public void setAutoTossEnabled(boolean value) { this.autoTossEnabled = value; }
    public String getAutoTossItems() { return autoTossItems; }
    public void setAutoTossItems(String value) { this.autoTossItems = value; }

    public boolean isAutoTossStackBehindEnabled() { return autoTossStackBehindEnabled; }
    public void setAutoTossStackBehindEnabled(boolean value) { this.autoTossStackBehindEnabled = value; }
    public String getAutoTossStackBehindItems() { return autoTossStackBehindItems; }
    public void setAutoTossStackBehindItems(String value) { this.autoTossStackBehindItems = value; }

    // Saved coordinates
    public void setSavedCoords(double x, double y, double z) {
        this.savedX = x;
        this.savedY = y;
        this.savedZ = z;
    }
    public double getSavedX() { return savedX; }
    public double getSavedY() { return savedY; }
    public double getSavedZ() { return savedZ; }

    // Only autofish at saved coords
    public boolean isOnlyAutofishAtSavedCoords() { return onlyAutofishAtSavedCoords; }
    public void setOnlyAutofishAtSavedCoords(boolean value) { this.onlyAutofishAtSavedCoords = value; }

    public boolean isAutofishEnabled() {
        return isAutofishEnabled;
    }
    public boolean isOpenWaterDetectEnabled() {
        return isOpenWaterDetectEnabled;
    }

    public boolean isMultiRod() {
        return multiRod;
    }

    public boolean isNoBreak() {
        return noBreak;
    }

    public boolean isPersistentMode() { return persistentMode; }

    public boolean isUseSoundDetection() {
        return useSoundDetection;
    }

    public boolean isForceMPDetection() { return forceMPDetection; }

    public long getRecastDelay() {
        return recastDelay;
    }

    public long getRandomDelay(){
        return randomPercent;
    }

    public String getClearLagRegex() {
        return clearLagRegex;
    }

    public void setAutofishEnabled(boolean autofishEnabled) { isAutofishEnabled = autofishEnabled; }

    public void setMultiRod(boolean multiRod) {
        this.multiRod = multiRod;
    }

    public void setNoBreak(boolean noBreak) {
        this.noBreak = noBreak;
    }

    public void setPersistentMode(boolean persistentMode) { this.persistentMode = persistentMode; }

    public void setUseSoundDetection(boolean useSoundDetection) {
        this.useSoundDetection = useSoundDetection;
    }

    public void setForceMPDetection(boolean forceMPDetection) { this.forceMPDetection = forceMPDetection; }

    public void setRecastDelay(long recastDelay) {
        this.recastDelay = recastDelay;
    }
    public void setRandomDelay(long randomPercent){
        this.randomPercent = randomPercent;
    }

    public void setClearLagRegex(String clearLagRegex) {
        this.clearLagRegex = clearLagRegex;
    }

    public void setOpenWaterDetectEnabled(boolean openWaterDetectEnabled) {
        isOpenWaterDetectEnabled = openWaterDetectEnabled;
    }

    public long getRandomPercent() {
        return randomPercent;
    }

    public void setRandomPercent(long randomPercent) {
        this.randomPercent = randomPercent;
    }

    public long getReelInDelay() {
        return reelInDelay;
    }

    public void setReelInDelay(long reelInDelay) {
        this.reelInDelay = reelInDelay;
    }

    /**
     * @return true if anything was changed
     */
    public boolean enforceConstraints() {
        boolean changed = false;
        if (recastDelay < 500) {
            recastDelay = 500;
            changed = true;
        }
        if (clearLagRegex == null) {
            clearLagRegex = "";
            changed = true;
        }
        return changed;
    }
}

package com.cometmod.config;

/**
 * Configuration for per-tier settings like timeout and spawn radius.
 */
public class TierSettings {

    private int timeoutSeconds;
    private double minRadius;
    private double maxRadius;

    // Default values for each tier
    public static final TierSettings TIER1_DEFAULTS = new TierSettings(90, 3.0, 5.0);
    public static final TierSettings TIER2_DEFAULTS = new TierSettings(150, 4.0, 6.0);
    public static final TierSettings TIER3_DEFAULTS = new TierSettings(180, 5.0, 7.0);
    public static final TierSettings TIER4_DEFAULTS = new TierSettings(240, 6.0, 8.0);

    public TierSettings() {
        this.timeoutSeconds = 90;
        this.minRadius = 3.0;
        this.maxRadius = 5.0;
    }

    public TierSettings(int timeoutSeconds, double minRadius, double maxRadius) {
        this.timeoutSeconds = timeoutSeconds;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
    }

    // Getters
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public long getTimeoutMillis() {
        return timeoutSeconds * 1000L;
    }

    public double getMinRadius() {
        return minRadius;
    }

    public double getMaxRadius() {
        return maxRadius;
    }

    // Setters
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
    }

    public void setMinRadius(double minRadius) {
        this.minRadius = Math.max(1.0, minRadius);
    }

    public void setMaxRadius(double maxRadius) {
        this.maxRadius = Math.max(this.minRadius + 1.0, maxRadius);
    }

    /**
     * Get default settings for a tier
     * 
     * @param tier The tier number (1-4)
     * @return Default TierSettings for that tier
     */
    public static TierSettings getDefaultForTier(int tier) {
        switch (tier) {
            case 1:
                return TIER1_DEFAULTS;
            case 2:
                return TIER2_DEFAULTS;
            case 3:
                return TIER3_DEFAULTS;
            case 4:
                return TIER4_DEFAULTS;
            default:
                return TIER1_DEFAULTS;
        }
    }

    @Override
    public String toString() {
        return "TierSettings{timeout=" + timeoutSeconds + "s, radius=" + minRadius + "-" + maxRadius + "}";
    }
}

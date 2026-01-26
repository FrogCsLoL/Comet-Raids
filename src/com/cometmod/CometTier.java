package com.cometmod;

public enum CometTier {
    UNCOMMON("Uncommon"),
    EPIC("Epic"),
    RARE("Rare"),
    LEGENDARY("Legendary");
    
    private final String name;
    
    CometTier(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public static CometTier fromString(String tierName) {
        if (tierName == null) {
            return UNCOMMON; // Default
        }
        
        String normalized = tierName.trim().toLowerCase();
        for (CometTier tier : values()) {
            if (tier.name.toLowerCase().equals(normalized)) {
                return tier;
            }
        }
        
        return UNCOMMON; // Default to Uncommon if not found
    }
    
    public String getAssetSuffix() {
        return "_" + name;
    }
    
    public String getBlockId(String baseName) {
        return baseName + getAssetSuffix();
    }
    
    public String getShardId() {
        return getBlockId("Comet_Shard");
    }
    
    public String getLootTableName() {
        return "Comet_Rewards" + getAssetSuffix();
    }
    
    public String getFallingProjectileConfig() {
        return "Comet_Falling" + getAssetSuffix();
    }
    
    public String getExplosionParticleSystem() {
        return "Comet_Explosion_Large" + getAssetSuffix();
    }
    
    public String getBeamParticleSystem() {
        return "Comet_Beam" + getAssetSuffix();
    }
}

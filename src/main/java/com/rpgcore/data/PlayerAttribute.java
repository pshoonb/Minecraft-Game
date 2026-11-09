package com.rpgcore.data;

/** The eight freely assignable RPG attributes shown by the character panel. */
public enum PlayerAttribute {
    STRENGTH(0, "strength"),
    DEFENSE(1, "defense"),
    HEALTH(2, "health"),
    MINING_SPEED(3, "mining_speed"),
    SPEED(4, "speed"),
    JUMP_HEIGHT(5, "jump_height"),
    LUCK(6, "luck"),
    EXPERIENCE_LOOT(7, "experience_loot");

    private final int networkId;
    private final String serializedName;

    PlayerAttribute(int networkId, String serializedName) {
        this.networkId = networkId;
        this.serializedName = serializedName;
    }

    public int networkId() { return networkId; }
    public String serializedName() { return serializedName; }

    public static PlayerAttribute byNetworkId(int id) {
        for (PlayerAttribute attribute : values()) {
            if (attribute.networkId == id) return attribute;
        }
        return null;
    }
}

package com.rpgcore.data;

/** The seven freely assignable RPG attributes. */
public enum PlayerAttribute {
    STRENGTH(0, "strength"),
    HEALTH(1, "health"),
    DEFENSE(2, "defense"),
    MINING_SPEED(3, "mining_speed"),
    LUCK(4, "luck"),
    SPEED(5, "speed"),
    JUMP_HEIGHT(6, "jump_height");

    private final int networkId;
    private final String serializedName;

    PlayerAttribute(int networkId, String serializedName) {
        this.networkId = networkId;
        this.serializedName = serializedName;
    }

    public int networkId() {
        return networkId;
    }

    public String serializedName() {
        return serializedName;
    }

    public static PlayerAttribute byNetworkId(int id) {
        for (PlayerAttribute attribute : values()) {
            if (attribute.networkId == id) {
                return attribute;
            }
        }
        return null;
    }
}

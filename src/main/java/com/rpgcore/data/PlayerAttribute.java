package com.rpgcore.data;

/**
 * The freely assignable character attributes.
 *
 * <p>These values are intentionally kept separate from Minecraft combat
 * attributes until the gameplay conversion rules are finalized.</p>
 */
public enum PlayerAttribute {
    STRENGTH(0, "strength"),
    AGILITY(1, "agility"),
    CONSTITUTION(2, "constitution"),
    INTELLIGENCE(3, "intelligence"),
    SPIRIT(4, "spirit"),
    EXPERIENCE_LOOT(5, "experience_loot");

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

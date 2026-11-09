package com.rpgcore.data;

/**
 * The five freely assignable character attributes.
 *
 * <p>Agility and Constitution currently affect vanilla player attributes.
 * The other values remain saved and allocatable until their gameplay roles
 * have been finalized.</p>
 */
public enum PlayerAttribute {
    STRENGTH(0, "strength"),
    AGILITY(1, "agility"),
    CONSTITUTION(2, "constitution"),
    INTELLIGENCE(3, "intelligence"),
    SPIRIT(4, "spirit");

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

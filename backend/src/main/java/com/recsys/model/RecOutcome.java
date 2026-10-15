package com.recsys.model;

/** What eventually happened to a recommendation that was shown to a user. */
public enum RecOutcome {
    DISMISSED(-1),
    SHOWN(0),
    CLICK(1),
    LIKE(2),
    PURCHASE(3);

    private final int rank;

    RecOutcome(int rank) {
        this.rank = rank;
    }

    /** Maps a user interaction to the recommendation outcome it implies (null = no effect). */
    public static RecOutcome from(InteractionType type) {
        return switch (type) {
            case CLICK -> CLICK;
            case LIKE -> LIKE;
            case PURCHASE -> PURCHASE;
            case DISMISS -> DISMISSED;
            case VIEW -> null;
        };
    }

    /** True when this outcome should replace {@code current} (outcomes only ever "improve"). */
    public boolean supersedes(RecOutcome current) {
        if (this == DISMISSED) return current == SHOWN;
        return rank > current.rank;
    }

    public boolean isClick() { return this == CLICK || this == LIKE || this == PURCHASE; }
}

package com.ecotale.economy;

public enum TransactionType {
    GIVE("Admin give"),
    TAKE("Admin take"),
    SET("Admin set"),
    RESET("Admin reset"),
    PAY("Player transfer"),
    EARN("Earnings"),
    SPEND("Spending");

    private final String displayName;

    private TransactionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}


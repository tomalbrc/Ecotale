package com.ecotale.economy;

import com.ecotale.economy.TransactionType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record TransactionEntry(Instant timestamp, String formattedTime, TransactionType type, UUID sourcePlayer, UUID targetPlayer, double amount, String playerName) {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    public static TransactionEntry single(TransactionType type, UUID player, String playerName, double amount) {
        Instant now = Instant.now();
        return new TransactionEntry(now, TIME_FORMATTER.format(now), type, player, null, amount, playerName);
    }

    public static TransactionEntry transfer(UUID from, String fromName, UUID to, String toName, double amount) {
        Instant now = Instant.now();
        return new TransactionEntry(now, TIME_FORMATTER.format(now), TransactionType.PAY, from, to, amount, fromName + " \u2192 " + toName);
    }

    public String toDisplayString() {
        return String.format("[%s] %s: %s %s", this.formattedTime, this.type.getDisplayName(), this.playerName, this.formatAmount());
    }

    private String formatAmount() {
        if (this.type == TransactionType.TAKE || this.type == TransactionType.SPEND) {
            return String.format("-$%.0f", this.amount);
        }
        if (this.type == TransactionType.SET || this.type == TransactionType.RESET) {
            return String.format("=$%.0f", this.amount);
        }
        return String.format("+$%.0f", this.amount);
    }

    public boolean involvesPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        return playerUuid.equals(this.sourcePlayer) || playerUuid.equals(this.targetPlayer);
    }
}


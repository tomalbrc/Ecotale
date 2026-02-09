package com.ecotale.economy;

import java.util.UUID;

public record TopBalanceEntry(UUID uuid, String name, double balance, double trend) {
}


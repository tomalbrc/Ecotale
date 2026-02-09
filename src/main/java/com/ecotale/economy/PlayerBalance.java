package com.ecotale.economy;

import com.ecotale.Main;
import com.ecotale.config.EcotaleConfig;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import java.util.UUID;

public class PlayerBalance {
    
    public static final BuilderCodec<PlayerBalance> CODEC = BuilderCodec.builder(PlayerBalance.class, PlayerBalance::new)
        .append(new KeyedCodec<>("Uuid", Codec.STRING),
            (p, v, extraInfo) -> p.playerUuid = UUID.fromString(v), 
            (p, extraInfo) -> p.playerUuid.toString()).add()
        .append(new KeyedCodec<>("Balance", Codec.DOUBLE),
            (p, v, extraInfo) -> p.balance = v, 
            (p, extraInfo) -> p.balance).add()
        .append(new KeyedCodec<>("TotalEarned", Codec.DOUBLE),
            (p, v, extraInfo) -> p.totalEarned = v, 
            (p, extraInfo) -> p.totalEarned).add()
        .append(new KeyedCodec<>("TotalSpent", Codec.DOUBLE),
            (p, v, extraInfo) -> p.totalSpent = v, 
            (p, extraInfo) -> p.totalSpent).add()
        .append(new KeyedCodec<>("LastTransaction", Codec.STRING),
            (p, v, extraInfo) -> p.lastTransaction = v, 
            (p, extraInfo) -> p.lastTransaction).add()
        .append(new KeyedCodec<>("LastTransactionTime", Codec.LONG),
            (p, v, extraInfo) -> p.lastTransactionTime = v, 
            (p, extraInfo) -> p.lastTransactionTime).add()
        .build();
    
    public static final ArrayCodec<PlayerBalance> ARRAY_CODEC = new ArrayCodec<>(CODEC, PlayerBalance[]::new, PlayerBalance::new);
    
    private UUID playerUuid;
    private double balance = 0.0;
    private double totalEarned = 0.0;
    private double totalSpent = 0.0;
    private String lastTransaction = "";
    private long lastTransactionTime = 0L;

    public PlayerBalance() {
    }

    public PlayerBalance(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public boolean deposit(double amount, String reason) {
        if (amount <= 0.0) {
            return false;
        }
        double maxBalance = ((EcotaleConfig)Main.CONFIG.get()).getMaxBalance();
        if (this.balance + amount > maxBalance) {
            return false;
        }
        this.balance += amount;
        this.totalEarned += amount;
        this.lastTransaction = "+" + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
        return true;
    }

    public boolean withdraw(double amount, String reason) {
        if (amount <= 0.0 || this.balance < amount) {
            return false;
        }
        this.balance -= amount;
        this.totalSpent += amount;
        this.lastTransaction = "-" + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
        return true;
    }

    public void setBalance(double amount, String reason) {
        this.balance = Math.max(0.0, amount);
        this.lastTransaction = "Set to " + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
    }

    void depositInternal(double amount, String reason) {
        this.balance += amount;
        this.totalEarned += amount;
        this.lastTransaction = "+" + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
    }

    void withdrawInternal(double amount, String reason) {
        this.balance -= amount;
        this.totalSpent += amount;
        this.lastTransaction = "-" + amount + " (" + reason + ")";
        this.lastTransactionTime = System.currentTimeMillis();
    }

    public UUID getPlayerUuid() {
        return this.playerUuid;
    }

    public double getBalance() {
        return this.balance;
    }

    public double getTotalEarned() {
        return this.totalEarned;
    }

    public double getTotalSpent() {
        return this.totalSpent;
    }

    public String getLastTransaction() {
        return this.lastTransaction;
    }

    public long getLastTransactionTime() {
        return this.lastTransactionTime;
    }

    public boolean hasBalance(double amount) {
        return this.balance >= amount;
    }
}


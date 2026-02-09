package com.ecotale.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.text.DecimalFormat;

public class EcotaleConfig {
    public static final BuilderCodec<EcotaleConfig> CODEC = BuilderCodec.builder(EcotaleConfig.class, EcotaleConfig::new).append(new KeyedCodec<>("CurrencySymbol", Codec.STRING), (c, v, e) -> {
        c.currencySymbol = v;
    }, (c, e) -> c.currencySymbol).add().append(new KeyedCodec<>("HudPrefix", Codec.STRING), (c, v, e) -> {
        c.hudPrefix = v;
    }, (c, e) -> c.hudPrefix).add().append(new KeyedCodec<>("StartingBalance", Codec.DOUBLE), (c, v, e) -> {
        c.startingBalance = v;
    }, (c, e) -> c.startingBalance).add().append(new KeyedCodec<>("MaxBalance", Codec.DOUBLE), (c, v, e) -> {
        c.maxBalance = v;
    }, (c, e) -> c.maxBalance).add().append(new KeyedCodec<>("TransferFee", Codec.DOUBLE), (c, v, e) -> {
        c.transferFee = v;
    }, (c, e) -> c.transferFee).add().append(new KeyedCodec<>("MinimumTransaction", Codec.DOUBLE), (c, v, e) -> {
        c.minimumTransaction = v;
    }, (c, e) -> c.minimumTransaction).add().append(new KeyedCodec<>("RateLimitBurst", Codec.INTEGER), (c, v, e) -> {
        c.rateLimitBurst = v;
    }, (c, e) -> c.rateLimitBurst).add().append(new KeyedCodec<>("RateLimitRefill", Codec.INTEGER), (c, v, e) -> {
        c.rateLimitRefill = v;
    }, (c, e) -> c.rateLimitRefill).add().append(new KeyedCodec<>("StorageProvider", Codec.STRING), (c, v, e) -> {
        c.storageProvider = v;
    }, (c, e) -> c.storageProvider).add().append(new KeyedCodec<>("EnableBackups", Codec.BOOLEAN), (c, v, e) -> {
        c.enableBackups = v;
    }, (c, e) -> c.enableBackups).add().append(new KeyedCodec<>("MysqlHost", Codec.STRING), (c, v, e) -> {
        c.mysqlHost = v;
    }, (c, e) -> c.mysqlHost).add().append(new KeyedCodec<>("MysqlPort", Codec.INTEGER), (c, v, e) -> {
        c.mysqlPort = v;
    }, (c, e) -> c.mysqlPort).add().append(new KeyedCodec<>("MysqlDatabase", Codec.STRING), (c, v, e) -> {
        c.mysqlDatabase = v;
    }, (c, e) -> c.mysqlDatabase).add().append(new KeyedCodec<>("MysqlUsername", Codec.STRING), (c, v, e) -> {
        c.mysqlUsername = v;
    }, (c, e) -> c.mysqlUsername).add().append(new KeyedCodec<>("MysqlPassword", Codec.STRING), (c, v, e) -> {
        c.mysqlPassword = v;
    }, (c, e) -> c.mysqlPassword).add().append(new KeyedCodec<>("MysqlTablePrefix", Codec.STRING), (c, v, e) -> {
        c.mysqlTablePrefix = v;
    }, (c, e) -> c.mysqlTablePrefix).add().append(new KeyedCodec<>("MongoUri", Codec.STRING), (c, v, e) -> {
        c.mongoUri = v;
    }, (c, e) -> c.mongoUri).add().append(new KeyedCodec<>("MongoDatabase", Codec.STRING), (c, v, e) -> {
        c.mongoDatabase = v;
    }, (c, e) -> c.mongoDatabase).add().append(new KeyedCodec<>("AutoSaveInterval", Codec.INTEGER), (c, v, e) -> {
        c.autoSaveInterval = v;
    }, (c, e) -> c.autoSaveInterval).add().append(new KeyedCodec<>("TopBalanceSnapshotTime", Codec.STRING), (c, v, e) -> {
        c.topBalanceSnapshotTime = v;
    }, (c, e) -> c.topBalanceSnapshotTime).add().append(new KeyedCodec<>("TopBalanceSnapshotTimeZone", Codec.STRING), (c, v, e) -> {
        c.topBalanceSnapshotTimeZone = v;
    }, (c, e) -> c.topBalanceSnapshotTimeZone).add().append(new KeyedCodec<>("EnableHudDisplay", Codec.BOOLEAN), (c, v, e) -> {
        c.enableHudDisplay = v;
    }, (c, e) -> c.enableHudDisplay).add().append(new KeyedCodec<>("EnableHudAnimation", Codec.BOOLEAN), (c, v, e) -> {
        c.enableHudAnimation = v;
    }, (c, e) -> c.enableHudAnimation).add().append(new KeyedCodec<>("UseHudTranslation", Codec.BOOLEAN), (c, v, e) -> {
        c.useHudTranslation = v;
    }, (c, e) -> c.useHudTranslation).add().append(new KeyedCodec<>("SymbolOnRight", Codec.BOOLEAN), (c, v, e) -> {
        c.symbolOnRight = v;
    }, (c, e) -> c.symbolOnRight).add().append(new KeyedCodec<>("DecimalPlaces", Codec.INTEGER), (c, v, e) -> {
        c.decimalPlaces = v;
    }, (c, e) -> c.decimalPlaces).add().append(new KeyedCodec<>("Language", Codec.STRING), (c, v, e) -> {
        c.language = v;
    }, (c, e) -> c.language).add().append(new KeyedCodec<>("UsePlayerLanguage", Codec.BOOLEAN), (c, v, e) -> {
        c.usePlayerLanguage = v;
    }, (c, e) -> c.usePlayerLanguage).add().append(new KeyedCodec<>("DebugMode", Codec.BOOLEAN), (c, v, e) -> {
        c.debugMode = v;
    }, (c, e) -> c.debugMode).add().build();
    private String currencySymbol = "$";
    private String hudPrefix = "Bank";
    private double startingBalance = 100.0;
    private double maxBalance = 1.0E9;
    private double transferFee = 0.05;
    private double minimumTransaction = 1.0;
    private int rateLimitBurst = 50;
    private int rateLimitRefill = 10;
    private String storageProvider = "h2";
    private boolean enableBackups = true;
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "ecotale";
    private String mysqlUsername = "root";
    private String mysqlPassword = "";
    private String mysqlTablePrefix = "eco_";
    private String mongoUri = "mongodb://localhost:27017";
    private String mongoDatabase = "ecotale";
    private int autoSaveInterval = 300;
    private String topBalanceSnapshotTime = "03:00";
    private String topBalanceSnapshotTimeZone = "System";
    private boolean enableHudDisplay = true;
    private boolean enableHudAnimation = false;
    private boolean useHudTranslation = false;
    private boolean symbolOnRight = false;
    private int decimalPlaces = 2;
    private String language = "en-US";
    private boolean usePlayerLanguage = true;
    private boolean debugMode = false;

    public String getCurrencySymbol() {
        return this.currencySymbol;
    }

    public void setCurrencySymbol(String symbol) {
        this.currencySymbol = symbol;
    }

    public String getHudPrefix() {
        return this.hudPrefix;
    }

    public void setHudPrefix(String prefix) {
        this.hudPrefix = prefix;
    }

    public double getStartingBalance() {
        return this.startingBalance;
    }

    public void setStartingBalance(double balance) {
        this.startingBalance = balance;
    }

    public double getMaxBalance() {
        return this.maxBalance;
    }

    public void setMaxBalance(double balance) {
        this.maxBalance = balance;
    }

    public double getTransferFee() {
        return this.transferFee;
    }

    public void setTransferFee(double fee) {
        this.transferFee = fee;
    }

    public double getMinimumTransaction() {
        return this.minimumTransaction;
    }

    public int getRateLimitBurst() {
        return this.rateLimitBurst;
    }

    public int getRateLimitRefill() {
        return this.rateLimitRefill;
    }

    public String getStorageProvider() {
        return this.storageProvider;
    }

    public boolean isEnableBackups() {
        return this.enableBackups;
    }

    public String getMysqlHost() {
        return this.mysqlHost;
    }

    public int getMysqlPort() {
        return this.mysqlPort;
    }

    public String getMysqlDatabase() {
        return this.mysqlDatabase;
    }

    public String getMysqlUsername() {
        return this.mysqlUsername;
    }

    public String getMysqlPassword() {
        return this.mysqlPassword;
    }

    public String getMysqlTablePrefix() {
        return this.mysqlTablePrefix;
    }

    public String getMongoUri() {
        return this.mongoUri;
    }

    public String getMongoDatabase() {
        return this.mongoDatabase;
    }

    public int getAutoSaveInterval() {
        return this.autoSaveInterval;
    }

    public String getTopBalanceSnapshotTime() {
        return this.topBalanceSnapshotTime;
    }

    public String getTopBalanceSnapshotTimeZone() {
        return this.topBalanceSnapshotTimeZone;
    }

    public boolean isEnableHudDisplay() {
        return this.enableHudDisplay;
    }

    public void setEnableHudDisplay(boolean enabled) {
        this.enableHudDisplay = enabled;
    }

    public int getDecimalPlaces() {
        return this.decimalPlaces;
    }

    public void setDecimalPlaces(int places) {
        this.decimalPlaces = places;
    }

    public boolean isEnableHudAnimation() {
        return this.enableHudAnimation;
    }

    public void setEnableHudAnimation(boolean enabled) {
        this.enableHudAnimation = enabled;
    }

    public boolean isUseHudTranslation() {
        return this.useHudTranslation;
    }

    public void setUseHudTranslation(boolean enabled) {
        this.useHudTranslation = enabled;
    }

    public boolean isSymbolOnRight() {
        return this.symbolOnRight;
    }

    public void setSymbolOnRight(boolean onRight) {
        this.symbolOnRight = onRight;
    }

    public String getLanguage() {
        return this.language;
    }

    public void setLanguage(String lang) {
        this.language = lang;
    }

    public boolean isUsePlayerLanguage() {
        return this.usePlayerLanguage;
    }

    public void setUsePlayerLanguage(boolean perPlayer) {
        this.usePlayerLanguage = perPlayer;
    }

    public boolean isDebugMode() {
        return this.debugMode;
    }

    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    public String format(double amount) {
        StringBuilder pattern = new StringBuilder("#,##0");
        if (this.decimalPlaces > 0) {
            pattern.append(".");
            pattern.append("0".repeat(this.decimalPlaces));
        }
        DecimalFormat df = new DecimalFormat(pattern.toString());
        String formatted = df.format(amount);
        return this.symbolOnRight ? formatted + " " + this.currencySymbol : this.currencySymbol + " " + formatted;
    }

    public String formatShort(double amount) {
        String formatted = amount >= 1.0E9 ? String.format("%.1fB", amount / 1.0E9) : (amount >= 1000000.0 ? String.format("%.1fM", amount / 1000000.0) : (amount >= 10000.0 ? String.format("%.1fK", amount / 1000.0) : String.valueOf(Math.round(amount))));
        return this.symbolOnRight ? formatted + " " + this.currencySymbol : this.currencySymbol + " " + formatted;
    }

    public String formatShortNoSymbol(double amount) {
        if (amount >= 1.0E9) {
            return String.format("%.1fB", amount / 1.0E9);
        }
        if (amount >= 1000000.0) {
            return String.format("%.1fM", amount / 1000000.0);
        }
        if (amount >= 10000.0) {
            return String.format("%.1fK", amount / 1000.0);
        }
        return String.valueOf(Math.round(amount));
    }
}


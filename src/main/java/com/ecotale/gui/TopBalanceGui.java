/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.hypixel.hytale.codec.Codec
 *  com.hypixel.hytale.codec.KeyedCodec
 *  com.hypixel.hytale.codec.builder.BuilderCodec
 *  com.hypixel.hytale.codec.builder.BuilderCodec$Builder
 *  com.hypixel.hytale.component.Ref
 *  com.hypixel.hytale.component.Store
 *  com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
 *  com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
 *  com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
 *  com.hypixel.hytale.server.core.ui.builder.EventData
 *  com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
 *  com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
 *  com.hypixel.hytale.server.core.universe.PlayerRef
 *  com.hypixel.hytale.server.core.universe.Universe
 *  com.hypixel.hytale.server.core.universe.world.storage.EntityStore
 *  org.checkerframework.checker.nullness.compatqual.NonNullDecl
 */
package com.ecotale.gui;

import com.ecotale.Main;
import com.ecotale.config.EcotaleConfig;
import com.ecotale.economy.PlayerBalance;
import com.ecotale.economy.TopBalanceEntry;
import com.ecotale.storage.H2StorageProvider;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class TopBalanceGui
extends InteractiveCustomUIPage<TopBalanceGui.TopBalanceData> {
    private static final int PAGE_SIZE = 10;
    private static final int WEEK_DAYS = 7;
    private static final int MONTH_DAYS = 30;
    private final PlayerRef playerRef;
    private int currentPage = 0;
    private Mode mode = Mode.ALL_TIME;

    public TopBalanceGui(@NonNullDecl PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, TopBalanceData.CODEC);
        this.playerRef = playerRef;
    }

    public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder cmd, @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        cmd.append("Pages/Ecotale_TopBalancePage.ui");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of((String)"Action", (String)"Close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevButton", EventData.of((String)"Action", (String)"Prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", EventData.of((String)"Action", (String)"Next"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabAllTime", EventData.of((String)"Action", (String)"TabAllTime"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabWeekly", EventData.of((String)"Action", (String)"TabWeekly"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabMonthly", EventData.of((String)"Action", (String)"TabMonthly"), false);
        double myBalance = Main.getInstance().getEconomyManager().getBalance(this.playerRef.getUuid());
        cmd.set("#YourBalanceValue.Text", ((EcotaleConfig)Main.CONFIG.get()).format(myBalance));
        cmd.set("#YourRankValue.Text", "-");
        cmd.set("#TotalBalanceDisplay.Text", ((EcotaleConfig)Main.CONFIG.get()).formatShort(myBalance));
        cmd.clear("#TopList");
        cmd.appendInline("#TopList", "Label { Text: \"Loading...\"; Style: (FontSize: 12, TextColor: #888888); Padding: (Top: 12); }");
        cmd.set("#PageLabel.Text", "Page " + (this.currentPage + 1));
        cmd.set("#CountLabel.Text", "");
        this.loadPage(ref, store, myBalance);
    }

    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, @NonNullDecl TopBalanceData data) {
        super.handleDataEvent(ref, store, data);
        if (data.action != null) {
            switch (data.action) {
                case "Close": {
                    this.close();
                    return;
                }
                case "Prev": {
                    if (this.currentPage <= 0) break;
                    --this.currentPage;
                    break;
                }
                case "Next": {
                    ++this.currentPage;
                    break;
                }
                case "TabAllTime": {
                    this.mode = Mode.ALL_TIME;
                    this.currentPage = 0;
                    break;
                }
                case "TabWeekly": {
                    this.mode = Mode.WEEKLY;
                    this.currentPage = 0;
                    break;
                }
                case "TabMonthly": {
                    this.mode = Mode.MONTHLY;
                    this.currentPage = 0;
                }
            }
        }
        double myBalance = Main.getInstance().getEconomyManager().getBalance(this.playerRef.getUuid());
        this.refreshUI(ref, store, myBalance);
    }

    private void refreshUI(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, double myBalance) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.clear("#TopList");
        cmd.appendInline("#TopList", "Label { Text: \"Loading...\"; Style: (FontSize: 12, TextColor: #888888); Padding: (Top: 12); }");
        cmd.set("#PageLabel.Text", "Page " + (this.currentPage + 1));
        cmd.set("#YourBalanceValue.Text", ((EcotaleConfig)Main.CONFIG.get()).format(myBalance));
        this.sendUpdate(cmd, new UIEventBuilder(), false);
        this.loadPage(ref, store, myBalance);
    }

    private void loadPage(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, double myBalance) {
        H2StorageProvider h2 = Main.getInstance().getEconomyManager().getH2Storage();
        int offset = this.currentPage * 10;
        if (h2 != null) {
            CachedPage cached = this.getCachedEntries(offset);
            if (!cached.entries.isEmpty()) {
                this.updateList(cached.entries, cached.totalCount, myBalance, CompletableFuture.completedFuture(null));
            }
            CompletableFuture<List<TopBalanceEntry>> listFuture = switch (this.mode.ordinal()) {
                default -> throw new MatchException(null, null);
                case 0 -> this.invokeTopQuery(h2, 10, offset);
                case 1 -> this.invokeTopPeriodQuery(h2, 10, offset, 7);
                case 2 -> this.invokeTopPeriodQuery(h2, 10, offset, 30);
            };
            CompletableFuture<Integer> countFuture = this.invokeCountPlayers(h2);
            CompletableFuture<Integer> rankFuture = this.invokeCountRank(h2, myBalance);
            listFuture.thenCombineAsync(countFuture, (entries, total) -> {
                if (entries == null || entries.isEmpty()) {
                    return null;
                }
                this.updateList(entries, total, myBalance, rankFuture);
                return null;
            });
            return;
        }
        CachedPage cached = this.getCachedEntries(offset);
        this.updateList(cached.entries, cached.totalCount, myBalance, CompletableFuture.completedFuture(null));
    }

    private CachedPage getCachedEntries(int offset) {
        Map<UUID, PlayerBalance> allBalances = Main.getInstance().getEconomyManager().getAllBalances();
        List sorted = allBalances.entrySet().stream().sorted((a, b) -> Double.compare(((PlayerBalance)b.getValue()).getBalance(), ((PlayerBalance)a.getValue()).getBalance())).toList();
        int start = Math.min(offset, sorted.size());
        int end = Math.min(offset + 10, sorted.size());
        ArrayList<TopBalanceEntry> entries = new ArrayList<TopBalanceEntry>();
        for (int i = start; i < end; ++i) {
            Map.Entry entry = (Map.Entry)sorted.get(i);
            UUID uuid = (UUID)entry.getKey();
            PlayerBalance balance = (PlayerBalance)entry.getValue();
            String name = this.resolveName(uuid, null);
            entries.add(new TopBalanceEntry(uuid, name, balance.getBalance(), 0.0));
        }
        return new CachedPage(entries, sorted.size());
    }

    private void updateList(List<TopBalanceEntry> entries, int totalCount, double myBalance, CompletableFuture<Integer> rankFuture) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.clear("#TopList");
        if (entries.isEmpty()) {
            cmd.appendInline("#TopList", "Label { Text: \"No data available\"; Style: (FontSize: 12, TextColor: #888888); Padding: (Top: 12); }");
        } else {
            int baseRank = this.currentPage * 10;
            for (int i = 0; i < entries.size(); ++i) {
                TopBalanceEntry entry = entries.get(i);
                int rank2 = baseRank + i + 1;
                String name = this.resolveName(entry.uuid(), entry.name());
                boolean online = Universe.get().getPlayer(entry.uuid()) != null;
                cmd.append("#TopList", "Pages/Ecotale_TopBalanceEntry.ui");
                cmd.set("#TopList[" + i + "] #Rank.Text", "#" + rank2);
                cmd.set("#TopList[" + i + "] #PlayerName.Text", name);
                cmd.set("#TopList[" + i + "] #Balance.Text", ((EcotaleConfig)Main.CONFIG.get()).format(entry.balance()));
                cmd.set("#TopList[" + i + "] #Trend.Text", this.formatTrend(entry.trend()));
                cmd.set("#TopList[" + i + "] #StatusText.Text", online ? "Online" : "Offline");
            }
        }
        cmd.set("#PageLabel.Text", "Page " + (this.currentPage + 1));
        if (totalCount > 0) {
            cmd.set("#CountLabel.Text", totalCount + " players");
        } else {
            cmd.set("#CountLabel.Text", "");
        }
        cmd.set("#PrevButton.Visible", this.currentPage > 0);
        cmd.set("#NextButton.Visible", entries.size() >= 10);
        rankFuture.thenAcceptAsync(rank -> {
            UICommandBuilder rankCmd = new UICommandBuilder();
            if (rank != null) {
                rankCmd.set("#YourRankValue.Text", "#" + (rank + 1));
            }
            this.sendUpdate(rankCmd, new UIEventBuilder(), false);
        });
        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private String formatTrend(double trend) {
        if (this.mode == Mode.ALL_TIME) {
            return "-";
        }
        if (Math.abs(trend) < 0.01) {
            return "0";
        }
        String sign = trend > 0.0 ? "+" : "-";
        return sign + ((EcotaleConfig)Main.CONFIG.get()).formatShort(Math.abs(trend));
    }

    private String resolveName(UUID uuid, String cachedName) {
        PlayerRef online = Universe.get().getPlayer(uuid);
        if (online != null) {
            return online.getUsername();
        }
        if (cachedName != null && !cachedName.isBlank()) {
            return cachedName;
        }
        return uuid.toString().substring(0, 8) + "...";
    }

    private CompletableFuture<List<TopBalanceEntry>> invokeTopQuery(H2StorageProvider h2, int limit, int offset) {
        try {
            Method method = h2.getClass().getMethod("queryTopBalancesAsync", Integer.TYPE, Integer.TYPE);
            Object result = method.invoke((Object)h2, limit, offset);
            if (result instanceof CompletableFuture) {
                CompletableFuture future = (CompletableFuture)result;
                return future;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return CompletableFuture.completedFuture(new ArrayList());
    }

    private CompletableFuture<List<TopBalanceEntry>> invokeTopPeriodQuery(H2StorageProvider h2, int limit, int offset, int daysAgo) {
        try {
            Method method = h2.getClass().getMethod("queryTopBalancesPeriodAsync", Integer.TYPE, Integer.TYPE, Integer.TYPE);
            Object result = method.invoke((Object)h2, limit, offset, daysAgo);
            if (result instanceof CompletableFuture) {
                CompletableFuture future = (CompletableFuture)result;
                return future;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return CompletableFuture.completedFuture(new ArrayList());
    }

    private CompletableFuture<Integer> invokeCountPlayers(H2StorageProvider h2) {
        try {
            Method method = h2.getClass().getMethod("countPlayersAsync", new Class[0]);
            Object result = method.invoke((Object)h2, new Object[0]);
            if (result instanceof CompletableFuture) {
                CompletableFuture future = (CompletableFuture)result;
                return future;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return CompletableFuture.completedFuture(0);
    }

    private CompletableFuture<Integer> invokeCountRank(H2StorageProvider h2, double balance) {
        try {
            Method method = h2.getClass().getMethod("countPlayersWithBalanceGreaterAsync", Double.TYPE);
            Object result = method.invoke(h2, balance);
            if (result instanceof CompletableFuture future) {
                return future;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return CompletableFuture.completedFuture(null);
    }

    public static class TopBalanceData {
        private static final String KEY_ACTION = "Action";
        public String action;
        public static final BuilderCodec<TopBalanceData> CODEC = ((BuilderCodec.Builder)BuilderCodec.builder(TopBalanceData.class, TopBalanceData::new).append(new KeyedCodec("Action", (Codec)Codec.STRING), (d, v, e) -> {
            d.action = v;
        }, (d, e) -> d.action).add()).build();
    }

    private static enum Mode {
        ALL_TIME,
        WEEKLY,
        MONTHLY;

    }

    private record CachedPage(List<TopBalanceEntry> entries, int totalCount) {
    }
}


package com.ecotale.economy;

import com.ecotale.economy.PlayerBalance;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class BalanceStorage {
    public static final BuilderCodec<BalanceStorage> CODEC = ((BuilderCodec.Builder)BuilderCodec.builder(BalanceStorage.class, BalanceStorage::new).append(new KeyedCodec("Balances", PlayerBalance.ARRAY_CODEC), (s, v, extraInfo) -> {
        s.balances = v;
    }, (s, extraInfo) -> s.balances).add()).build();
    private PlayerBalance[] balances = new PlayerBalance[0];

    public BalanceStorage() {
    }

    public BalanceStorage(PlayerBalance[] balances) {
        this.balances = balances;
    }

    public PlayerBalance[] getBalances() {
        return this.balances;
    }
}


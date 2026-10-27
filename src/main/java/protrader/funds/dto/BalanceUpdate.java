package protrader.funds.dto;

import java.time.Instant;

public record BalanceUpdate(long accountId, long accountSeq, Instant ts,
        long cashFreeMinor, long cashLockedMinor, long realizedPnlMinor, String transferId) {
}

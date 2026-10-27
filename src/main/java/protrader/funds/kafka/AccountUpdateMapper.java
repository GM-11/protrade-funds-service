package protrader.funds.kafka;

import java.time.Instant;

import com.protrader.proto.exchange.v1.AccountUpdate;

import protrader.funds.dto.BalanceUpdate;

/**
 * AccountUpdate (protobuf) → BalanceUpdate. Cash only: positions and order
 * details are ignored.
 */
public final class AccountUpdateMapper {

    private AccountUpdateMapper() {
    }

    /**
     * A record that can't be used throws, so the reader stops instead of skipping
     * it.
     */
    static BalanceUpdate toBalanceUpdate(AccountUpdate u) {
        if (u.getAccountId() == 0) {
            throw new IllegalArgumentException("account update without account_id");
        }
        if (u.getAccountSeq() == 0) {
            throw new IllegalArgumentException("account update without account_seq, account " + u.getAccountId());
        }
        if (!u.hasBalances()) {
            throw new IllegalArgumentException("account update without balances, account " + u.getAccountId()
                    + " seq " + u.getAccountSeq());
        }
        var b = u.getBalances();
        String transferId = u.hasTransfer() ? u.getTransfer().getTransferId() : null;
        return new BalanceUpdate(u.getAccountId(), u.getAccountSeq(), Instant.ofEpochMilli(u.getTs()),
                b.getCashFreeMinor(), b.getCashLockedMinor(), b.getRealizedPnlMinor(), transferId);
    }
}

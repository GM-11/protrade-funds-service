package protrader.funds.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import protrader.funds.dto.BalanceUpdate;

import com.protrader.proto.exchange.v1.AccountUpdate;
import com.protrader.proto.exchange.v1.Balances;
import com.protrader.proto.exchange.v1.OrderEvent;
import com.protrader.proto.exchange.v1.TransferEvent;

/**
 * AccountUpdate → BalanceUpdate: fields copied, ts in ms, transfer id only for
 * transfers, bad records throw.
 */
class AccountUpdateMapperTest {

    private static final Balances BALANCES = Balances.newBuilder()
            .setCashFreeMinor(900_00).setCashLockedMinor(100_00).setRealizedPnlMinor(-50).build();

    private static AccountUpdate.Builder base() {
        return AccountUpdate.newBuilder().setAccountId(7).setAccountSeq(3).setTs(1_761_552_000_123L)
                .setBalances(BALANCES);
    }

    @Test
    void orderEventHasNoTransferId() {
        AccountUpdate u = base().setOrderEvent(OrderEvent.newBuilder().setKind(OrderEvent.Kind.KIND_NEW)).build();

        BalanceUpdate b = AccountUpdateMapper.toBalanceUpdate(u);

        assertEquals(new BalanceUpdate(7, 3, Instant.ofEpochMilli(1_761_552_000_123L), 900_00, 100_00, -50, null), b);
    }

    @Test
    void transferUpdateCarriesTheTransferId() {
        AccountUpdate u = base().setTransfer(TransferEvent.newBuilder().setTransferId("wd-1").setAmountMinor(500))
                .build();

        assertEquals("wd-1", AccountUpdateMapper.toBalanceUpdate(u).transferId());
    }

    @Test
    void tsIsMilliseconds() {
        AccountUpdate u = base().setTs(1_000).build();

        assertEquals(Instant.ofEpochSecond(1), AccountUpdateMapper.toBalanceUpdate(u).ts());
    }

    @Test
    void updateWithoutCauseHasNoTransferId() {
        assertNull(AccountUpdateMapper.toBalanceUpdate(base().build()).transferId());
    }

    @Test
    void badRecordsThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountUpdateMapper.toBalanceUpdate(base().clearBalances().build()));
        assertThrows(IllegalArgumentException.class,
                () -> AccountUpdateMapper.toBalanceUpdate(base().setAccountId(0).build()));
        assertThrows(IllegalArgumentException.class,
                () -> AccountUpdateMapper.toBalanceUpdate(base().setAccountSeq(0).build()));
    }
}

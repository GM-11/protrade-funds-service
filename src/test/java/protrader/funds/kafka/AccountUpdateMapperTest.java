package protrader.funds.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import protrader.funds.dto.BalanceUpdate;
import protrader.funds.entity.OrderKey;
import protrader.funds.entity.OrderRecord;
import protrader.funds.entity.OrderSide;
import protrader.funds.entity.OrderTimeInForce;
import protrader.funds.entity.OrderType;

import com.protrader.proto.exchange.v1.AccountUpdate;
import com.protrader.proto.exchange.v1.Balances;
import com.protrader.proto.exchange.v1.CashRejectReason;
import com.protrader.proto.exchange.v1.OrderEvent;
import com.protrader.proto.exchange.v1.OrderState;
import com.protrader.proto.exchange.v1.OrderStatus;
import com.protrader.proto.exchange.v1.RejectReason;
import com.protrader.proto.exchange.v1.Side;
import com.protrader.proto.exchange.v1.TimeInForce;
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

    // --- piece 5: the order photo

    private static OrderState.Builder photo() {
        return OrderState.newBuilder().setAccountId(7).setClientOrderId("abc").setSymbol(1).setOrderId(812)
                .setSide(Side.SIDE_BUY).setType(com.protrader.proto.exchange.v1.OrderType.ORDER_TYPE_LIMIT)
                .setTimeInForce(TimeInForce.TIME_IN_FORCE_GTC).setPriceMinor(100_00).setQuantity(10)
                .setFilledQuantity(4).setFilledNotionalMinor(400_00)
                .setStatus(OrderStatus.ORDER_STATUS_PARTIALLY_FILLED);
    }

    private static AccountUpdate withOrder(OrderState.Builder order) {
        return base().setOrderEvent(OrderEvent.newBuilder().setKind(OrderEvent.Kind.KIND_PARTIAL_FILL)
                .setOrder(order)).build();
    }

    @Test
    void orderPhotoIsCopiedAndZeroTimesFallBackToTheUpdateTs() {
        BalanceUpdate b = AccountUpdateMapper.toBalanceUpdate(withOrder(photo()));

        Instant ts = Instant.ofEpochMilli(1_761_552_000_123L);
        assertEquals(new OrderRecord(new OrderKey(7, "abc"), 1, 812, OrderSide.BUY, OrderType.LIMIT,
                OrderTimeInForce.GTC, 100_00, null, 10, 4, 400_00,
                protrader.funds.entity.OrderStatus.PARTIALLY_FILLED, null, ts, ts, 3), b.order());
        assertNull(b.transferId());
    }

    @Test
    void realOrderTimesAreKeptWhenSent() {
        OrderRecord o = AccountUpdateMapper.toBalanceUpdate(withOrder(photo().setCreatedTs(1_000).setUpdatedTs(2_000)))
                .order();

        assertEquals(Instant.ofEpochMilli(1_000), o.getCreatedAt());
        assertEquals(Instant.ofEpochMilli(2_000), o.getUpdatedAt());
    }

    @Test
    void rejectReasonOnlyForRejectedOrders() {
        OrderRecord engine = AccountUpdateMapper.toBalanceUpdate(withOrder(photo()
                .setStatus(OrderStatus.ORDER_STATUS_REJECTED).setEngineRejectReason(RejectReason.REJECT_REASON_PRICE_BAND)))
                .order();
        OrderRecord cash = AccountUpdateMapper.toBalanceUpdate(withOrder(photo()
                .setStatus(OrderStatus.ORDER_STATUS_REJECTED)
                .setCashRejectReason(CashRejectReason.CASH_REJECT_REASON_INSUFFICIENT_CASH))).order();
        OrderRecord unknown = AccountUpdateMapper.toBalanceUpdate(withOrder(photo()
                .setStatus(OrderStatus.ORDER_STATUS_REJECTED))).order();
        OrderRecord open = AccountUpdateMapper.toBalanceUpdate(withOrder(photo()
                .setEngineRejectReason(RejectReason.REJECT_REASON_PRICE_BAND))).order();

        assertEquals("price_band", engine.getRejectReason());
        assertEquals("insufficient_cash", cash.getRejectReason());
        assertEquals("rejected", unknown.getRejectReason());
        assertNull(open.getRejectReason());
    }

    @Test
    void stopPriceOnlyWhenSet() {
        assertEquals(95_00L, AccountUpdateMapper.toBalanceUpdate(withOrder(photo().setStopPriceMinor(95_00))).order()
                .getStopPriceMinor());
    }

    @Test
    void unusableOrderPhotosThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountUpdateMapper.toBalanceUpdate(withOrder(photo().setStatus(OrderStatus.ORDER_STATUS_UNSPECIFIED))));
        assertThrows(IllegalArgumentException.class,
                () -> AccountUpdateMapper.toBalanceUpdate(withOrder(photo().setSide(Side.SIDE_UNSPECIFIED))));
        assertThrows(IllegalArgumentException.class,
                () -> AccountUpdateMapper.toBalanceUpdate(withOrder(photo().setClientOrderId(""))));
        assertThrows(IllegalArgumentException.class,
                () -> AccountUpdateMapper.toBalanceUpdate(withOrder(photo().setAccountId(8))));
    }

    @Test
    void transferUpdatesHaveNoOrder() {
        AccountUpdate u = base().setTransfer(TransferEvent.newBuilder().setTransferId("wd-1").setAmountMinor(500))
                .build();

        assertNull(AccountUpdateMapper.toBalanceUpdate(u).order());
    }
}

package protrader.funds.kafka;

import java.time.Instant;

import com.protrader.proto.exchange.v1.AccountUpdate;
import com.protrader.proto.exchange.v1.CashRejectReason;
import com.protrader.proto.exchange.v1.OrderState;
import com.protrader.proto.exchange.v1.RejectReason;

import protrader.funds.dto.BalanceUpdate;
import protrader.funds.entity.OrderKey;
import protrader.funds.entity.OrderRecord;
import protrader.funds.entity.OrderSide;
import protrader.funds.entity.OrderStatus;
import protrader.funds.entity.OrderTimeInForce;
import protrader.funds.entity.OrderType;

/**
 * AccountUpdate (protobuf) → BalanceUpdate: balances, the transfer id, and the order photo. Positions are ignored.
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
        Instant ts = Instant.ofEpochMilli(u.getTs());
        String transferId = u.hasTransfer() ? u.getTransfer().getTransferId() : null;
        OrderRecord order = u.hasOrderEvent() && u.getOrderEvent().hasOrder()
                ? toOrder(u.getOrderEvent().getOrder(), u.getAccountId(), u.getAccountSeq(), ts)
                : null;
        return new BalanceUpdate(u.getAccountId(), u.getAccountSeq(), ts,
                b.getCashFreeMinor(), b.getCashLockedMinor(), b.getRealizedPnlMinor(), transferId, order);
    }

    /**
     * The order photo → a row. The cash owner sends created_ts / updated_ts as 0 today, so 0 falls back to the
     * update's ts (the upsert keeps the first row's created_at, so created_at = time of the first photo, NEW).
     */
    static OrderRecord toOrder(OrderState o, long accountId, long accountSeq, Instant ts) {
        if (o.getAccountId() != accountId) {
            throw new IllegalArgumentException("order photo for account " + o.getAccountId()
                    + " inside an update of account " + accountId + " seq " + accountSeq);
        }
        if (o.getClientOrderId().isEmpty()) {
            throw new IllegalArgumentException("order photo without client_order_id, account " + accountId
                    + " seq " + accountSeq);
        }
        OrderStatus status = status(o.getStatus(), accountId, accountSeq);
        return new OrderRecord(
                new OrderKey(accountId, o.getClientOrderId()),
                o.getSymbol(),
                o.getOrderId(),
                side(o.getSide(), accountId, accountSeq),
                o.getType() == com.protrader.proto.exchange.v1.OrderType.ORDER_TYPE_MARKET ? OrderType.MARKET
                        : OrderType.LIMIT,
                switch (o.getTimeInForce()) {
                    case TIME_IN_FORCE_IOC -> OrderTimeInForce.IOC;
                    case TIME_IN_FORCE_DAY -> OrderTimeInForce.DAY;
                    default -> OrderTimeInForce.GTC;
                },
                o.getPriceMinor(),
                o.hasStopPriceMinor() ? o.getStopPriceMinor() : null,
                o.getQuantity(),
                o.getFilledQuantity(),
                o.getFilledNotionalMinor(),
                status,
                status == OrderStatus.REJECTED ? rejectReason(o) : null,
                o.getCreatedTs() > 0 ? Instant.ofEpochMilli(o.getCreatedTs()) : ts,
                o.getUpdatedTs() > 0 ? Instant.ofEpochMilli(o.getUpdatedTs()) : ts,
                accountSeq);
    }

    private static OrderSide side(com.protrader.proto.exchange.v1.Side s, long accountId, long accountSeq) {
        return switch (s) {
            case SIDE_BUY -> OrderSide.BUY;
            case SIDE_SELL -> OrderSide.SELL;
            default -> throw new IllegalArgumentException("order photo without side, account " + accountId
                    + " seq " + accountSeq);
        };
    }

    private static OrderStatus status(com.protrader.proto.exchange.v1.OrderStatus s, long accountId, long accountSeq) {
        return switch (s) {
            case ORDER_STATUS_PENDING_NEW -> OrderStatus.PENDING_NEW;
            case ORDER_STATUS_NEW -> OrderStatus.NEW;
            case ORDER_STATUS_PARTIALLY_FILLED -> OrderStatus.PARTIALLY_FILLED;
            case ORDER_STATUS_FILLED -> OrderStatus.FILLED;
            case ORDER_STATUS_CANCELED -> OrderStatus.CANCELED;
            case ORDER_STATUS_REJECTED -> OrderStatus.REJECTED;
            case ORDER_STATUS_EXPIRED -> OrderStatus.EXPIRED;
            case ORDER_STATUS_PENDING_CANCEL -> OrderStatus.PENDING_CANCEL;
            case ORDER_STATUS_PENDING_REPLACE -> OrderStatus.PENDING_REPLACE;
            default -> throw new IllegalArgumentException("order photo with status " + s + ", account " + accountId
                    + " seq " + accountSeq);
        };
    }

    /** Same public words as the gateway's wire.rejectReason / EngineRejectName. */
    static String rejectReason(OrderState o) {
        if (o.getCashRejectReason() == CashRejectReason.CASH_REJECT_REASON_INSUFFICIENT_CASH) {
            return "insufficient_cash";
        }
        if (o.getCashRejectReason() == CashRejectReason.CASH_REJECT_REASON_INSUFFICIENT_HOLDINGS) {
            return "insufficient_holdings";
        }
        RejectReason r = o.getEngineRejectReason();
        return switch (r) {
            case REJECT_REASON_INVALID_PRICE -> "invalid_price";
            case REJECT_REASON_INVALID_QUANTITY -> "invalid_quantity";
            case REJECT_REASON_SELF_TRADE -> "self_trade";
            case REJECT_REASON_SYMBOL_HALTED -> "symbol_halted";
            case REJECT_REASON_PRICE_BAND -> "price_band";
            case REJECT_REASON_PRICE_COLLAR -> "price_collar";
            case REJECT_REASON_STOP_WOULD_TRIGGER -> "stop_would_trigger";
            default -> "rejected";
        };
    }
}

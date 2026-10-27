package protrader.funds.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import protrader.funds.config.Instruments;
import protrader.funds.entity.OrderRecord;
import protrader.funds.utils.Money;
import protrader.funds.utils.Timestamps;

/**
 * The contract's Order object, field for field the same as the order gateway's
 * wire.Order: money "101.50" strings,
 * quantities as strings, order_id "<symbol>-<engine id>", symbol as its ticker.
 */
public record OrderResponse(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("side") String side,
        @JsonProperty("type") String type,
        @JsonProperty("time_in_force") String timeInForce,
        @JsonProperty("price") String price,
        @JsonProperty("stop_price") String stopPrice,
        @JsonProperty("qty") String qty,
        @JsonProperty("filled_qty") String filledQty,
        @JsonProperty("filled_avg_price") String filledAvgPrice,
        @JsonProperty("status") String status,
        @JsonProperty("reject_reason") String rejectReason,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt) {

    public static OrderResponse of(OrderRecord o, Instruments instruments) {
        return new OrderResponse(
                Integer.toUnsignedString(o.getSymbol()) + "-" + Long.toUnsignedString(o.getOrderId()),
                o.getClientOrderId(),
                instruments.ticker(o.getSymbol()),
                o.getSide().json(),
                o.getOrderType().json(),
                o.getTimeInForce().json(),
                Money.format(o.getPriceMinor()),
                o.getStopPriceMinor() == null ? null : Money.format(o.getStopPriceMinor()),
                Long.toString(o.getQuantity()),
                Long.toString(o.getFilledQuantity()),
                // notional / filled rounded down to the paisa; null until the first fill (same
                // as the gateway)
                o.getFilledQuantity() <= 0 ? null : Money.format(o.getFilledNotionalMinor() / o.getFilledQuantity()),
                o.getStatus().json(),
                o.getRejectReason(),
                Timestamps.format(o.getCreatedAt()),
                Timestamps.format(o.getUpdatedAt()));
    }
}

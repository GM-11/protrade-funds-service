package protrader.funds.controllers;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import protrader.funds.config.AuthFilter;
import protrader.funds.config.Instruments;
import protrader.funds.dto.OrderListResponse;
import protrader.funds.dto.OrderResponse;
import protrader.funds.entity.OrderRecord;
import protrader.funds.entity.OrderStatus;
import protrader.funds.repositories.BalanceRepository;
import protrader.funds.repositories.OrderRepository;
import protrader.funds.repositories.OrderRepository.Cursor;
import protrader.funds.utils.ApiException;
import protrader.funds.utils.Inputs;
import protrader.funds.utils.PageTokens;

/**
 * Order history (contract §3.4, added 2026-10-27). Read-only copy built from
 * cash.account-updates.v1; may lag the
 * cash owner by a moment. Live screens use the trading stream; these are for
 * history pages.
 */
@RestController
@RequestMapping("/v2/funds")
public class OrderController {

    private final OrderRepository orders;
    private final BalanceRepository balances;
    private final Instruments instruments;

    public OrderController(OrderRepository orders, BalanceRepository balances, Instruments instruments) {
        this.orders = orders;
        this.balances = balances;
        this.instruments = instruments;
    }

    /**
     * GET /v2/funds/orders?status=open|closed|all (default all)&limit&page_token —
     * newest first.
     */
    @GetMapping("/orders")
    OrderListResponse list(@RequestAttribute(AuthFilter.ACCOUNT_ID) long accountId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "page_token", required = false) String pageToken) {
        List<String> statuses = statuses(status);
        int size = Inputs.limit(limit);
        Cursor after = PageTokens.decodeOrders(pageToken);
        // ask for one extra row to know whether another page exists
        List<OrderRecord> rows = orders.page(accountId, statuses, size + 1, after);
        boolean more = rows.size() > size;
        List<OrderRecord> page = more ? rows.subList(0, size) : rows;
        String next = null;
        if (more) {
            OrderRecord last = page.get(page.size() - 1);
            next = PageTokens.encode(new Cursor(last.getCreatedAt(), last.getClientOrderId()));
        }
        boolean complete = balances.find(accountId).map(b -> b.getHistoryGapSeq() == null).orElse(true);
        return new OrderListResponse(page.stream().map(o -> OrderResponse.of(o, instruments)).toList(), next,
                complete);
    }

    /**
     * GET /v2/funds/orders/{client_order_id} — one order of the caller's account.
     */
    @GetMapping("/orders/{clientOrderId}")
    OrderResponse one(@RequestAttribute(AuthFilter.ACCOUNT_ID) long accountId, @PathVariable String clientOrderId) {
        Inputs.clientOrderId(clientOrderId);
        return orders.find(accountId, clientOrderId)
                .map(o -> OrderResponse.of(o, instruments))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "order_not_found", "no such order", null));
    }

    /** open / closed use OrderStatus.isOpen (core); all = every status. */
    private static List<String> statuses(String status) {
        if (status == null || status.isEmpty() || status.equals("all")) {
            return Arrays.stream(OrderStatus.values()).map(Enum::name).toList();
        }
        boolean open;
        if (status.equals("open")) {
            open = true;
        } else if (status.equals("closed")) {
            open = false;
        } else {
            throw ApiException.invalidField("status", "status must be \"open\", \"closed\" or \"all\"");
        }
        return Arrays.stream(OrderStatus.values()).filter(s -> s.isOpen() == open).map(Enum::name).toList();
    }
}

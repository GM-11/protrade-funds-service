package protrader.funds.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /v2/funds/orders. next_page_token is null on the last page. history_complete = false when a seq jump was seen
 * for this account (option C): some orders may show an older state than they really have.
 */
public record OrderListResponse(
		@JsonProperty("orders") List<OrderResponse> orders,
		@JsonProperty("next_page_token") String nextPageToken,
		@JsonProperty("history_complete") boolean historyComplete) {
}

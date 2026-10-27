package protrader.funds.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import protrader.funds.config.AuthFilter;
import protrader.funds.dto.BalanceResponse;
import protrader.funds.repositories.BalanceRepository;

/** The balance copy (contract §3.4). Display only: may lag the cash owner, never used to decide anything. */
@RestController
@RequestMapping("/v2/funds")
public class BalanceController {

	private final BalanceRepository balances;

	public BalanceController(BalanceRepository balances) {
		this.balances = balances;
	}

	@GetMapping("/balance")
	BalanceResponse balance(@RequestAttribute(AuthFilter.ACCOUNT_ID) long accountId) {
		return balances.find(accountId).map(BalanceResponse::of).orElseGet(() -> BalanceResponse.empty(accountId));
	}
}

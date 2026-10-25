package protrader.funds.grpc;

import protrader.funds.entity.Transfer;

public interface CashOwnerClient {

    /**
     * Asks the cash owner to apply this transfer (Deposit or Withdraw by kind). Safe to repeat: the cash owner
     * applies each transfer id once.
     *
     * @throws CashOwnerUnavailableException when there is no definite answer
     */
    TransferAnswer apply(Transfer transfer);
}

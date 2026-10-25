package protrader.funds.grpc;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.protrader.proto.exchange.v1.CashOwnerGrpc;
import com.protrader.proto.exchange.v1.TransferAck;
import com.protrader.proto.exchange.v1.TransferRequest;

import io.grpc.StatusRuntimeException;
import protrader.funds.entity.Transfer;

/**
 * Talks to the cash owner over gRPC. Only a clear APPLIED / DUPLICATE / INSUFFICIENT_CASH / UNKNOWN_ACCOUNT
 * is an answer; anything else (timeout, error, unspecified status) is "no answer".
 */
public class GrpcCashOwnerClient implements CashOwnerClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcCashOwnerClient.class);

    private final CashOwnerGrpc.CashOwnerBlockingStub stub;
    private final Duration deadline;

    public GrpcCashOwnerClient(CashOwnerGrpc.CashOwnerBlockingStub stub, Duration deadline) {
        this.stub = stub;
        this.deadline = deadline;
    }

    @Override
    public TransferAnswer apply(Transfer transfer) {
        TransferRequest request = TransferRequest.newBuilder()
                .setTransferId(transfer.transferId())
                .setAccountId(transfer.accountId())
                .setAmountMinor(transfer.amountMinor())
                .build();
        CashOwnerGrpc.CashOwnerBlockingStub withDeadline = stub.withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS);
        TransferAck ack;
        try {
            ack = switch (transfer.kind()) {
                case DEPOSIT -> withDeadline.deposit(request);
                case WITHDRAW -> withDeadline.withdraw(request);
            };
        } catch (StatusRuntimeException e) {
            log.warn("cash owner gave no answer: account={} transfer={} status={}", transfer.accountId(),
                    transfer.transferId(), e.getStatus());
            throw new CashOwnerUnavailableException("cash owner call failed: " + e.getStatus(), e);
        }
        return toAnswer(ack);
    }

    static TransferAnswer toAnswer(TransferAck ack) {
        return switch (ack.getStatus()) {
            case TRANSFER_STATUS_APPLIED -> TransferAnswer.of(TransferAnswer.Outcome.APPLIED);
            case TRANSFER_STATUS_DUPLICATE -> TransferAnswer.of(TransferAnswer.Outcome.DUPLICATE);
            case TRANSFER_STATUS_INSUFFICIENT_CASH ->
                new TransferAnswer(TransferAnswer.Outcome.INSUFFICIENT_CASH, ack.getShortfallMinor());
            case TRANSFER_STATUS_UNKNOWN_ACCOUNT -> TransferAnswer.of(TransferAnswer.Outcome.UNKNOWN_ACCOUNT);
            default -> throw new CashOwnerUnavailableException("cash owner sent an unusable status: " + ack.getStatus());
        };
    }
}

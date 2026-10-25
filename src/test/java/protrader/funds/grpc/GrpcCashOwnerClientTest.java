package protrader.funds.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.protrader.proto.exchange.v1.CashOwnerGrpc;
import com.protrader.proto.exchange.v1.TransferAck;
import com.protrader.proto.exchange.v1.TransferRequest;
import com.protrader.proto.exchange.v1.TransferStatus;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import protrader.funds.entity.Transfer;
import protrader.funds.entity.TransferKind;
import protrader.funds.grpc.TransferAnswer.Outcome;

/**
 * GrpcCashOwnerClient against an in-process fake cash owner (no Spring, no network).
 * The server runs on its own threads (not directExecutor) so the deadline can fire while a handler sleeps.
 */
class GrpcCashOwnerClientTest {

    private record Call(String method, TransferRequest request) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile TransferAck ack = TransferAck.getDefaultInstance();
    private volatile Status error;
    private volatile long delayMillis;

    private Server server;
    private ManagedChannel channel;
    private GrpcCashOwnerClient client;

    @BeforeEach
    void start() throws Exception {
        String name = "cash-owner-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(name)
                .executor(Executors.newCachedThreadPool())
                .addService(new CashOwnerGrpc.CashOwnerImplBase() {
                    @Override
                    public void deposit(TransferRequest request, StreamObserver<TransferAck> out) {
                        handle("deposit", request, out);
                    }

                    @Override
                    public void withdraw(TransferRequest request, StreamObserver<TransferAck> out) {
                        handle("withdraw", request, out);
                    }
                })
                .build().start();
        channel = InProcessChannelBuilder.forName(name).build();
        client = new GrpcCashOwnerClient(CashOwnerGrpc.newBlockingStub(channel), Duration.ofMillis(300));
    }

    private void handle(String method, TransferRequest request, StreamObserver<TransferAck> out) {
        calls.add(new Call(method, request));
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (error != null) {
            out.onError(error.asRuntimeException());
            return;
        }
        out.onNext(ack);
        out.onCompleted();
    }

    @AfterEach
    void stop() throws Exception {
        channel.shutdownNow();
        server.shutdownNow();
        server.awaitTermination(2, TimeUnit.SECONDS);
    }

    private static Transfer transfer(TransferKind kind) {
        return new Transfer(new protrader.funds.entity.TransferKey(1000, "wd-1"), kind, 30_000,
                protrader.funds.entity.TransferStatus.PENDING, null, null, Instant.parse("2026-10-25T10:00:00Z"), null);
    }

    private static TransferAck ack(TransferStatus status, long shortfall) {
        return TransferAck.newBuilder().setStatus(status).setShortfallMinor(shortfall).build();
    }

    @Test
    void withdrawCallsWithdrawWithTheTransfer() {
        ack = ack(TransferStatus.TRANSFER_STATUS_APPLIED, 0);

        TransferAnswer answer = client.apply(transfer(TransferKind.WITHDRAW));

        assertEquals(TransferAnswer.of(Outcome.APPLIED), answer);
        assertEquals(1, calls.size());
        assertEquals("withdraw", calls.get(0).method());
        assertEquals("wd-1", calls.get(0).request().getTransferId());
        assertEquals(1000, calls.get(0).request().getAccountId());
        assertEquals(30_000, calls.get(0).request().getAmountMinor());
    }

    @Test
    void depositCallsDeposit() {
        ack = ack(TransferStatus.TRANSFER_STATUS_APPLIED, 0);

        client.apply(transfer(TransferKind.DEPOSIT));

        assertEquals(1, calls.size());
        assertEquals("deposit", calls.get(0).method());
    }

    @Test
    void duplicateIsDuplicate() {
        ack = ack(TransferStatus.TRANSFER_STATUS_DUPLICATE, 0);
        assertEquals(TransferAnswer.of(Outcome.DUPLICATE), client.apply(transfer(TransferKind.WITHDRAW)));
    }

    @Test
    void unknownAccountIsUnknownAccount() {
        ack = ack(TransferStatus.TRANSFER_STATUS_UNKNOWN_ACCOUNT, 0);
        assertEquals(TransferAnswer.of(Outcome.UNKNOWN_ACCOUNT), client.apply(transfer(TransferKind.WITHDRAW)));
    }

    @Test
    void insufficientCashCarriesTheShortfall() {
        ack = ack(TransferStatus.TRANSFER_STATUS_INSUFFICIENT_CASH, 12_000);
        assertEquals(new TransferAnswer(Outcome.INSUFFICIENT_CASH, 12_000), client.apply(transfer(TransferKind.WITHDRAW)));
    }

    @Test
    void anUnspecifiedStatusIsNoAnswer() {
        ack = TransferAck.getDefaultInstance();
        assertThrows(CashOwnerUnavailableException.class, () -> client.apply(transfer(TransferKind.WITHDRAW)));
    }

    @Test
    void grpcErrorsAreNoAnswer() {
        for (Status status : List.of(Status.UNAVAILABLE, Status.INTERNAL, Status.UNKNOWN)) {
            error = status;
            CashOwnerUnavailableException e = assertThrows(CashOwnerUnavailableException.class,
                    () -> client.apply(transfer(TransferKind.WITHDRAW)), status.toString());
            assertInstanceOf(io.grpc.StatusRuntimeException.class, e.getCause());
        }
    }

    @Test
    void aSlowCashOwnerIsNoAnswerAfterTheDeadline() {
        delayMillis = 2_000;
        long started = System.nanoTime();

        assertThrows(CashOwnerUnavailableException.class, () -> client.apply(transfer(TransferKind.WITHDRAW)));

        long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(tookMillis < 1_500, "gave up after " + tookMillis + " ms");
    }
}

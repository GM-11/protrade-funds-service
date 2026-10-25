package protrader.funds.grpc;

/** The cash owner's definite answer to one transfer. shortfall is set only for INSUFFICIENT_CASH, else 0. */
public record TransferAnswer(Outcome outcome, long shortfallMinor) {

    public enum Outcome {
        APPLIED, DUPLICATE, INSUFFICIENT_CASH, UNKNOWN_ACCOUNT
    }

    public static TransferAnswer of(Outcome outcome) {
        return new TransferAnswer(outcome, 0);
    }
}

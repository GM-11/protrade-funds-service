package protrader.funds.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under funds.* in application.properties. */
@ConfigurationProperties(prefix = "funds")
public record FundsProperties(
		int internalPort,
		String internalBindAddress,
		long maxTransferMinor,
		Duration cashOwnerDeadline,
		String instrumentsFile,
		Auth auth,
		Kafka kafka) {

	public record Auth(
			String jwksUrl,
			String jwtIssuer,
			String jwtAudience,
			Duration jwksRefresh,
			String apiKeysSource,
			Duration apiKeysPoll) {
	}

	public record Kafka(String accountUpdatesTopic) {
	}
}

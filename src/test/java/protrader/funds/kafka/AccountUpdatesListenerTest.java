package protrader.funds.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.protrader.proto.exchange.v1.AccountUpdate;
import com.protrader.proto.exchange.v1.Balances;

import protrader.funds.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * Own context: embedded Kafka and the reader started.
 *  - updates seq 1, 2, 2 for one account → the DB ends on seq 2
 *  - garbage bytes → the reader stops and /readyz shows account_updates: false (and stays 200)
 */
@EmbeddedKafka(topics = "cash.account-updates.v1", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class AccountUpdatesListenerTest extends IntegrationTest {

	private static final String TOPIC = "cash.account-updates.v1";
	private static final long ACCOUNT = 77;

	@DynamicPropertySource
	static void readerOn(DynamicPropertyRegistry registry) {
		// this class gets its own Spring context, next to the shared one: it needs its own internal port
		registry.add("funds.internal-port", () -> {
			try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
				return Integer.toString(socket.getLocalPort());
			} catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	@Autowired
	EmbeddedKafkaBroker broker;

	/** IntegrationTest turns auto-startup off (its property wins), so this test starts the reader itself. */
	@Autowired
	KafkaListenerEndpointRegistry registry;

	private static byte[] update(long seq, long free) {
		return AccountUpdate.newBuilder().setAccountId(ACCOUNT).setAccountSeq(seq).setTs(1_761_552_000_000L + seq)
				.setBalances(Balances.newBuilder().setCashFreeMinor(free)).build().toByteArray();
	}

	private static void waitFor(String what, BooleanSupplier done) {
		long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (System.nanoTime() < end) {
			if (done.getAsBoolean()) {
				return;
			}
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}
		fail("timed out waiting for " + what);
	}

	private Optional<Long> storedSeq() {
		return jdbc.sql("select last_applied_seq from balances where account_id = :a").param("a", ACCOUNT)
				.query(Long.class).optional();
	}

	@BeforeEach
	void startReader() {
		MessageListenerContainer reader = registry.getListenerContainer("account-updates");
		if (!reader.isRunning()) {
			reader.start();
		}
	}

	@Test
	void updatesAreStoredThenGarbageStopsTheReader() {
		try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(
				KafkaTestUtils.producerProps(broker), new StringSerializer(), new ByteArraySerializer())) {
			String key = Long.toString(ACCOUNT);
			producer.send(new ProducerRecord<>(TOPIC, key, update(1, 100)));
			producer.send(new ProducerRecord<>(TOPIC, key, update(2, 200)));
			producer.send(new ProducerRecord<>(TOPIC, key, update(2, 999)));
			producer.flush();

			waitFor("seq 2 in the DB", () -> storedSeq().orElse(0L) == 2);
			assertEquals(200, jdbc.sql("select cash_free_minor from balances where account_id = :a").param("a", ACCOUNT)
					.query(Long.class).single());
			assertEquals(true, readyz().get("account_updates").asBoolean());

			producer.send(new ProducerRecord<>(TOPIC, key, new byte[] { 0x0a, (byte) 0xff, (byte) 0xff, 0x13, 0x07 }));
			producer.flush();

			waitFor("the reader to stop", () -> !readyz().get("account_updates").asBoolean());
		}
	}

	private JsonNode readyz() {
		var r = withHeaders(Map.of()).get("/readyz");
		assertEquals(200, r.status(), r.body());
		return r.json();
	}
}

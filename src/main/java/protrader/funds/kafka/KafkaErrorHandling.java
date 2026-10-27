package protrader.funds.kafka;

import java.util.List;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

@Configuration
public class KafkaErrorHandling {

	private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandling.class);

	/** Stops the reader on any error (nothing is skipped); the failed offset stays uncommitted. */
	@Bean
	public CommonErrorHandler stopOnErrorHandler() {
		return new CommonContainerStoppingErrorHandler() {

			@Override
			public void handleOtherException(Exception thrownException, Consumer<?, ?> consumer,
					MessageListenerContainer container, boolean batchListener) {
				announce(thrownException);
				super.handleOtherException(thrownException, consumer, container, batchListener);
			}

			@Override
			public boolean handleOne(Exception thrownException, ConsumerRecord<?, ?> record, Consumer<?, ?> consumer,
					MessageListenerContainer container) {
				announce(thrownException);
				return super.handleOne(thrownException, record, consumer, container);
			}

			@Override
			public void handleRemaining(Exception thrownException, List<ConsumerRecord<?, ?>> records,
					Consumer<?, ?> consumer, MessageListenerContainer container) {
				announce(thrownException);
				super.handleRemaining(thrownException, records, consumer, container);
			}

			@Override
			public void handleBatch(Exception thrownException, ConsumerRecords<?, ?> data, Consumer<?, ?> consumer,
					MessageListenerContainer container, Runnable invokeListener) {
				announce(thrownException);
				super.handleBatch(thrownException, data, consumer, container, invokeListener);
			}
		};
	}

	private static void announce(Exception e) {
		log.error("ACCOUNT UPDATES READER STOPPED: {}", e.toString());
	}
}

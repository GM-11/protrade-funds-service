package protrader.funds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.protrader.proto.exchange.v1.CashOwnerGrpc;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ImportGrpcClients(target = "cash-owner", types = CashOwnerGrpc.CashOwnerBlockingStub.class)
public class FundsApplication {

	public static void main(String[] args) {
		SpringApplication.run(FundsApplication.class, args);
	}

}

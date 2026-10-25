package protrader.funds.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FakeCashOwnerConfig {

    @Bean
    @Primary
    FakeCashOwner fakeCashOwner() {
        return new FakeCashOwner();
    }
}

package edu.escuelaing.citysim.engine.config;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HazelcastClientConfig {

    @Value("${hazelcast.addresses:localhost:5701}")
    private String hazelcastAddresses;

    @Bean
    public HazelcastInstance hazelcastInstance() {
        ClientConfig config = new ClientConfig();
        config.setClusterName("city-cluster");

        String[] addresses = hazelcastAddresses.split(",");
        for (String addr : addresses) {
            config.getNetworkConfig().addAddress(addr.trim());
        }

        config.getNetworkConfig().setConnectionTimeout(10000);
        config.getConnectionStrategyConfig()
              .getConnectionRetryConfig()
              .setClusterConnectTimeoutMillis(60000);

        return HazelcastClient.newHazelcastClient(config);
    }
}

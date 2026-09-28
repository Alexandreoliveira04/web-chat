package br.edu.webchat.storage.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcsConfig {

    @Value("${gcs.host:http://localhost:4443}")
    private String gcsHost;

    @Bean
    public Storage storage() {
        return StorageOptions.newBuilder()
                .setHost(gcsHost)
                .setProjectId("test-project")
                .build()
                .getService();
    }
}

package br.edu.webchat.storage.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente do Cloud Storage. No desenvolvimento aponta para o emulador (fake-gcs-server);
 * no Google Cloud, GCS_HOST=https://storage.googleapis.com e as credenciais vem da conta
 * de servico do Cloud Run, sem arquivo de chave.
 */
@Configuration
public class GcsConfig {

    @Value("${gcs.host:http://localhost:4443}")
    private String gcsHost;

    @Value("${gcs.project-id:test-project}")
    private String projectId;

    @Bean
    public Storage storage() {
        return StorageOptions.newBuilder()
                .setHost(gcsHost)
                .setProjectId(projectId)
                .build()
                .getService();
    }
}

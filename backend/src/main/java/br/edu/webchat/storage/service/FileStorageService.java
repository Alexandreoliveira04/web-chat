package br.edu.webchat.storage.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Servico do modulo storage: recebe o arquivo enviado pelo front e grava no
 * Google Cloud Storage (no desenvolvimento, o emulador fake-gcs-server do Docker).
 *
 * O banco de dados nunca recebe o arquivo: o servico devolve apenas a URL, que o
 * front envia como uma mensagem comum.
 *
 * As configuracoes chegam pelo construtor (e nao por campos com @Value) para que
 * o servico possa ser testado sem subir o Spring: basta passar um Storage falso.
 */
@Service
public class FileStorageService {

    private final Storage storage;
    private final String bucketName;
    private final String gcsPublicUrl;

    // Liga/desliga a criacao do bucket na subida. Nos testes (perfil "test") fica
    // desligado: assim a aplicacao sobe sem precisar do emulador rodando.
    private final boolean initBucket;

    public FileStorageService(Storage storage,
                              @Value("${gcs.bucket:webchat-files}") String bucketName,
                              @Value("${gcs.public-url:http://localhost:4443}") String gcsPublicUrl,
                              @Value("${gcs.init-bucket:true}") boolean initBucket) {
        this.storage = storage;
        this.bucketName = bucketName;
        this.gcsPublicUrl = gcsPublicUrl;
        this.initBucket = initBucket;
    }

    /**
     * Roda uma vez, logo depois que o Spring cria o servico: garante que o bucket existe.
     * E a unica chamada de rede feita na subida da aplicacao.
     */
    @PostConstruct
    public void init() {
        if (!initBucket) {
            return;
        }
        try {
            if (storage.get(bucketName) == null) {
                storage.create(BucketInfo.newBuilder(bucketName).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inicializar o bucket do GCS: " + e.getMessage(), e);
        }
    }

    public String uploadFile(MultipartFile file) {
        try {
            // Mantem so a extensao do nome original (ex.: ".png"); o resto vira um UUID,
            // para nao haver colisao de nomes nem caracteres estranhos vindos do usuario.
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            
            String fileName = UUID.randomUUID().toString() + extension;

            BlobId blobId = BlobId.of(bucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();

            storage.create(blobInfo, file.getBytes());

            // URL publica para o frontend acessar a imagem
            return String.format("%s/storage/v1/b/%s/o/%s?alt=media", gcsPublicUrl, bucketName, fileName);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao fazer upload do arquivo: " + e.getMessage(), e);
        }
    }
}

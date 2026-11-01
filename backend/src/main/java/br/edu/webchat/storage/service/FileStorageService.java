package br.edu.webchat.storage.service;

import br.edu.webchat.shared.exception.BadRequestException;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Servico do modulo storage: recebe o arquivo enviado pelo front e grava no
 * Google Cloud Storage (no desenvolvimento, o emulador fake-gcs-server do Docker).
 *
 * O banco de dados nunca recebe o arquivo: o servico devolve apenas a URL, que o
 * front envia como uma mensagem comum. O nome original do arquivo fica na URL.
 *
 * As configuracoes chegam pelo construtor (e nao por campos com @Value) para que
 * o servico possa ser testado sem subir o Spring: basta passar um Storage falso.
 */
@Service
public class FileStorageService {

    private static final int MAX_NAME_LENGTH = 150;

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
            // O nome original e preservado dentro de uma "pasta" com UUID: dois arquivos
            // com o mesmo nome nao colidem, e quem baixa recebe o nome que foi enviado.
            String originalName = safeFileName(file.getOriginalFilename());
            String objectName = UUID.randomUUID() + "/" + originalName;

            BlobId blobId = BlobId.of(bucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .setContentDisposition("inline; filename*=UTF-8''" + encode(originalName))
                    .build();

            storage.create(blobInfo, file.getBytes());

            // Na API do Cloud Storage o nome do objeto vai codificado num unico segmento
            // da URL (a "/" vira %2F).
            return String.format("%s/storage/v1/b/%s/o/%s?alt=media", gcsPublicUrl, bucketName, encode(objectName));

        } catch (Exception e) {
            throw new RuntimeException("Erro ao fazer upload do arquivo: " + e.getMessage(), e);
        }
    }

    /** Upload que so aceita imagens: usado nas fotos de perfil e de grupo. */
    public String uploadImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (file.isEmpty() || contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("A foto precisa ser uma imagem");
        }
        return uploadFile(file);
    }

    /** Remove caminhos e caracteres de controle do nome enviado, mantendo o resto como veio. */
    static String safeFileName(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();

        if (name.length() > MAX_NAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, MAX_NAME_LENGTH - extension.length()) + extension;
        }

        return name.isEmpty() || name.equals(".") || name.equals("..") ? "arquivo" : name;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

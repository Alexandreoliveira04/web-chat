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

@Service
public class FileStorageService {

    private final Storage storage;

    @Value("${gcs.bucket:webchat-files}")
    private String bucketName;

    @Value("${gcs.host:http://localhost:4443}")
    private String gcsHost;

    @Value("${gcs.public-url:http://localhost:4443}")
    private String gcsPublicUrl;

    public FileStorageService(Storage storage) {
        this.storage = storage;
    }

    @PostConstruct
    public void init() {
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

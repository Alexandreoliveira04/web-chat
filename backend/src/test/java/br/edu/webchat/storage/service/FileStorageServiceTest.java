package br.edu.webchat.storage.service;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes de unidade do upload. O Storage do Google e um mock: nenhum teste
 * precisa do emulador nem de rede, e todos rodam sem subir o Spring.
 */
@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

	private static final String BUCKET = "webchat-files";
	private static final String PUBLIC_URL = "http://localhost:4443";
	private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	@Mock
	private Storage storage;

	private FileStorageService service(boolean initBucket) {
		return new FileStorageService(storage, BUCKET, PUBLIC_URL, initBucket);
	}

	// ---------- upload ----------

	@Test
	void uploadDeveGravarComNomeUuidMantendoExtensaoETipo() {
		byte[] conteudo = {1, 2, 3};
		MockMultipartFile foto = new MockMultipartFile("file", "minha foto.png", "image/png", conteudo);

		String url = service(false).uploadFile(foto);

		ArgumentCaptor<BlobInfo> blob = ArgumentCaptor.forClass(BlobInfo.class);
		verify(storage).create(blob.capture(), eq(conteudo));

		// O nome do usuario some: fica so um UUID com a extensao original.
		String nomeGravado = blob.getValue().getName();
		assertThat(nomeGravado).matches(UUID_REGEX + "\\.png");
		assertThat(blob.getValue().getBucket()).isEqualTo(BUCKET);
		assertThat(blob.getValue().getContentType()).isEqualTo("image/png");

		// A URL devolvida aponta para o mesmo arquivo gravado.
		assertThat(url).isEqualTo(PUBLIC_URL + "/storage/v1/b/" + BUCKET + "/o/" + nomeGravado + "?alt=media");
	}

	@Test
	void uploadSemExtensaoDeveGerarNomeSoComUuid() {
		MockMultipartFile arquivo = new MockMultipartFile("file", "arquivo", "application/octet-stream", new byte[] {9});

		service(false).uploadFile(arquivo);

		ArgumentCaptor<BlobInfo> blob = ArgumentCaptor.forClass(BlobInfo.class);
		verify(storage).create(blob.capture(), any(byte[].class));
		assertThat(blob.getValue().getName()).matches(UUID_REGEX);
	}

	@Test
	void uploadComFalhaNoStorageDeveLancarExcecaoComMotivo() {
		when(storage.create(any(BlobInfo.class), any(byte[].class)))
				.thenThrow(new StorageException(503, "emulador fora do ar"));
		MockMultipartFile video = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[] {1});

		assertThatThrownBy(() -> service(false).uploadFile(video))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Erro ao fazer upload do arquivo")
				.hasMessageContaining("emulador fora do ar");
	}

	// ---------- criacao do bucket na subida ----------

	@Test
	void initDeveCriarBucketQuandoNaoExiste() {
		when(storage.get(BUCKET)).thenReturn(null);

		service(true).init();

		ArgumentCaptor<BucketInfo> bucket = ArgumentCaptor.forClass(BucketInfo.class);
		verify(storage).create(bucket.capture());
		assertThat(bucket.getValue().getName()).isEqualTo(BUCKET);
	}

	@Test
	void initNaoDeveRecriarBucketExistente() {
		when(storage.get(BUCKET)).thenReturn(mock(Bucket.class));

		service(true).init();

		verify(storage, never()).create(any(BucketInfo.class));
	}

	@Test
	void initDesligadoNaoDeveAcessarOStorage() {
		// E o que acontece no perfil "test": a aplicacao sobe sem o emulador.
		service(false).init();

		verifyNoInteractions(storage);
	}
}

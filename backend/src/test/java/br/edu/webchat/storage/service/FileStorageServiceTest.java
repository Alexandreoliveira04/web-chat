package br.edu.webchat.storage.service;

import br.edu.webchat.shared.exception.BadRequestException;
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
	void uploadDeveGuardarONomeOriginalDentroDeUmaPastaUuid() {
		byte[] conteudo = {1, 2, 3};
		MockMultipartFile foto = new MockMultipartFile("file", "minha foto.png", "image/png", conteudo);

		String url = service(false).uploadFile(foto);

		ArgumentCaptor<BlobInfo> blob = ArgumentCaptor.forClass(BlobInfo.class);
		verify(storage).create(blob.capture(), eq(conteudo));

		// O UUID evita colisao entre arquivos com o mesmo nome; o nome do usuario e mantido.
		String nomeGravado = blob.getValue().getName();
		assertThat(nomeGravado).matches(UUID_REGEX + "/minha foto\\.png");
		assertThat(blob.getValue().getBucket()).isEqualTo(BUCKET);
		assertThat(blob.getValue().getContentType()).isEqualTo("image/png");
		assertThat(blob.getValue().getContentDisposition()).isEqualTo("inline; filename*=UTF-8''minha%20foto.png");

		// A URL aponta para o mesmo objeto, com o nome codificado num unico segmento.
		String uuid = nomeGravado.substring(0, nomeGravado.indexOf('/'));
		assertThat(url).isEqualTo(PUBLIC_URL + "/storage/v1/b/" + BUCKET + "/o/" + uuid + "%2Fminha%20foto.png?alt=media");
	}

	@Test
	void nomeDoArquivoPerdeCaminhoECaracteresDeControleMasMantemAcentos() {
		assertThat(FileStorageService.safeFileName("C:\\Users\\eu\\Relatório final.pdf")).isEqualTo("Relatório final.pdf");
		assertThat(FileStorageService.safeFileName("../../etc/passwd")).isEqualTo("passwd");
		assertThat(FileStorageService.safeFileName("a\u0000b.txt")).isEqualTo("ab.txt");
		assertThat(FileStorageService.safeFileName(null)).isEqualTo("arquivo");
		assertThat(FileStorageService.safeFileName("..")).isEqualTo("arquivo");

		String longo = FileStorageService.safeFileName("x".repeat(300) + ".docx");
		assertThat(longo).hasSize(150).endsWith(".docx");
	}

	@Test
	void uploadSemExtensaoMantemONome() {
		MockMultipartFile arquivo = new MockMultipartFile("file", "arquivo", "application/octet-stream", new byte[] {9});

		service(false).uploadFile(arquivo);

		ArgumentCaptor<BlobInfo> blob = ArgumentCaptor.forClass(BlobInfo.class);
		verify(storage).create(blob.capture(), any(byte[].class));
		assertThat(blob.getValue().getName()).matches(UUID_REGEX + "/arquivo");
	}

	@Test
	void uploadDeImagemSoAceitaImagens() {
		MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1});
		MockMultipartFile vazio = new MockMultipartFile("file", "foto.png", "image/png", new byte[0]);

		assertThatThrownBy(() -> service(false).uploadImage(pdf)).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> service(false).uploadImage(vazio)).isInstanceOf(BadRequestException.class);
		verifyNoInteractions(storage);

		MockMultipartFile foto = new MockMultipartFile("file", "grupo.png", "image/png", new byte[] {1});
		assertThat(service(false).uploadImage(foto)).contains("%2Fgrupo.png?alt=media");
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

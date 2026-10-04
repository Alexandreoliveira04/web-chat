package br.edu.webchat.storage.controller;

import br.edu.webchat.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
// addFilters = false: este teste verifica o controller, nao a seguranca
// (a rota /api/v1/files exige login pela regra anyRequest().authenticated()).
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FileStorageService fileStorageService;

	@Test
	void uploadDeveDevolverAUrlDoArquivo() throws Exception {
		String url = "http://localhost:4443/storage/v1/b/webchat-files/o/abc.png?alt=media";
		when(fileStorageService.uploadFile(any())).thenReturn(url);

		MockMultipartFile foto = new MockMultipartFile("file", "foto.png", "image/png", new byte[] {1, 2, 3});

		mockMvc.perform(multipart("/api/v1/files/upload").file(foto))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value(url));
	}
}

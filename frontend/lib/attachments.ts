/**
 * Um arquivo enviado no chat vira uma mensagem cujo conteúdo é só a URL do arquivo
 * no Cloud Storage. Estas funções reconhecem essas mensagens e dizem o que elas são.
 */

export type AttachmentKind = "image" | "video" | "document";

export interface Attachment {
  kind: AttachmentKind;
  url: string;
  name: string;
  extension: string;
}

const IMAGE_EXTENSIONS = ["jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "svg"];
const VIDEO_EXTENSIONS = ["mp4", "webm", "ogg", "mov", "m4v"];
const DOCUMENT_EXTENSIONS = [
  "pdf", "doc", "docx", "odt", "rtf", "txt", "md",
  "xls", "xlsx", "ods", "csv",
  "ppt", "pptx", "odp",
  "zip", "rar", "7z",
];

// Caminho da API do Cloud Storage (e do emulador) usado pelo upload do backend.
const STORAGE_PATH = "/storage/v1/b/";

export function attachmentOf(content: string): Attachment | null {
  const texto = content.trim();
  if (!/^https?:\/\/\S+$/i.test(texto)) return null;

  let url: URL;
  try {
    url = new URL(texto);
  } catch {
    return null;
  }

  const name = fileNameOf(url);
  const dot = name.lastIndexOf(".");
  const extension = dot > 0 ? name.slice(dot + 1).toLowerCase() : "";

  if (IMAGE_EXTENSIONS.includes(extension)) return { kind: "image", url: texto, name, extension };
  if (VIDEO_EXTENSIONS.includes(extension)) return { kind: "video", url: texto, name, extension };
  if (DOCUMENT_EXTENSIONS.includes(extension) || url.pathname.includes(STORAGE_PATH)) {
    return { kind: "document", url: texto, name, extension };
  }
  return null;
}

/**
 * Nome do arquivo a partir da URL. No Cloud Storage o objeto fica em ".../o/<uuid>%2F<nome>",
 * então o nome é o último trecho depois de decodificar.
 */
function fileNameOf(url: URL): string {
  const marcador = url.pathname.indexOf("/o/");
  const caminho = marcador >= 0 ? url.pathname.slice(marcador + 3) : url.pathname;

  let decodificado = caminho;
  try {
    decodificado = decodeURIComponent(caminho);
  } catch {
    // mantém como veio se a URL tiver uma sequência % inválida
  }

  return decodificado.split("/").filter(Boolean).pop() ?? "arquivo";
}

export function linksIn(text: string): string[] {
  return text.match(/https?:\/\/[^\s]+/gi) ?? [];
}

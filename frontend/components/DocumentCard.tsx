import type { Attachment } from "@/lib/attachments";

interface Props {
  attachment: Attachment;
  compact?: boolean;
}

/** Documento enviado no chat: ícone com a extensão, nome original e link para abrir. */
export default function DocumentCard({ attachment, compact = false }: Props) {
  return (
    <a
      href={attachment.url}
      target="_blank"
      rel="noreferrer"
      title={`Abrir ${attachment.name}`}
      className={`flex items-center gap-3 rounded-lg bg-black/20 transition hover:bg-black/30 ${compact ? "p-2" : "mt-1 p-3"}`}
    >
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-emerald-600/20 text-[10px] font-bold uppercase text-emerald-300">
        {attachment.extension ? attachment.extension.slice(0, 4) : "arq"}
      </span>
      <span className="min-w-0">
        <span className="block truncate text-sm font-medium text-slate-100">{attachment.name}</span>
        <span className="block text-xs text-slate-400">
          {attachment.extension ? `Documento ${attachment.extension.toUpperCase()}` : "Arquivo"} · abrir
        </span>
      </span>
    </a>
  );
}

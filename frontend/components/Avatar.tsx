import { initials } from "@/lib/format";
import { getAssetUrl } from "@/lib/api";

interface Props {
  name: string;
  url?: string;
  online?: boolean;
  size?: "sm" | "md" | "lg";
}

export default function Avatar({ name, url, online = false, size = "md" }: Props) {
  let dimensao = "h-11 w-11 text-sm";
  if (size === "sm") dimensao = "h-9 w-9 text-xs";
  if (size === "lg") dimensao = "h-32 w-32 text-3xl";

  return (
    <div className={`relative shrink-0 ${dimensao}`}>
      <div
        className={`h-full w-full flex items-center justify-center rounded-full bg-slate-700 font-semibold text-slate-100 overflow-hidden avatar-container`}
      >
        {url ? (
          <img src={getAssetUrl(url)} alt={name} className="h-full w-full object-cover" />
        ) : (
          initials(name)
        )}
      </div>
      {online && (
        <span className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border-2 border-slate-900 bg-emerald-500" />
      )}
    </div>
  );
}

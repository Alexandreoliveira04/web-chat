import { initials } from "@/lib/format";

interface Props {
  name: string;
  online?: boolean;
  size?: "sm" | "md";
}

export default function Avatar({ name, online = false, size = "md" }: Props) {
  const dimensao = size === "sm" ? "h-9 w-9 text-xs" : "h-11 w-11 text-sm";

  return (
    <div className="relative shrink-0">
      <div
        className={`${dimensao} flex items-center justify-center rounded-full bg-slate-700 font-semibold text-slate-100`}
      >
        {initials(name)}
      </div>
      {online && (
        <span className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border-2 border-slate-900 bg-emerald-500" />
      )}
    </div>
  );
}

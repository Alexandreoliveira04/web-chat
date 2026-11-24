"use client";

import { useCallback, useRef, useState } from "react";
import Cropper, { type Area } from "react-easy-crop";
import Avatar from "./Avatar";
import getCroppedImg from "@/lib/cropImage";

interface Props {
  name: string;
  url?: string;
  /** Recebe a imagem já recortada; o componente mostra "Salvando..." enquanto a promessa roda. */
  onSave: (file: File) => Promise<void>;
}

/**
 * Foto redonda clicável: escolher uma imagem abre o recorte com zoom, e "Salvar foto" envia
 * o recorte. Usado no perfil e na foto do grupo.
 */
export default function AvatarPicker({ name, url, onSave }: Props) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [imageSrc, setImageSrc] = useState<string | null>(null);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [area, setArea] = useState<Area | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const onCropComplete = useCallback((_: Area, pixels: Area) => setArea(pixels), []);

  function escolher(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    const reader = new FileReader();
    reader.addEventListener("load", () => {
      setErro(null);
      setZoom(1);
      setCrop({ x: 0, y: 0 });
      setImageSrc(reader.result as string);
    });
    reader.readAsDataURL(file);
  }

  async function salvar() {
    if (!imageSrc || !area) return;
    setSalvando(true);
    setErro(null);
    try {
      const recorte = await getCroppedImg(imageSrc, area);
      if (recorte) await onSave(recorte);
      setImageSrc(null);
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível salvar a foto");
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="flex w-full flex-col items-center">
      {imageSrc ? (
        <>
          <div className="relative mb-4 h-64 w-full overflow-hidden rounded-lg bg-slate-900">
            <Cropper
              image={imageSrc}
              crop={crop}
              zoom={zoom}
              aspect={1}
              cropShape="round"
              showGrid={false}
              onCropChange={setCrop}
              onCropComplete={onCropComplete}
              onZoomChange={setZoom}
            />
          </div>
          <div className="flex w-full items-center gap-4 px-2">
            <span className="text-xs text-slate-400">Zoom</span>
            <input
              type="range"
              value={zoom}
              min={1}
              max={3}
              step={0.1}
              aria-label="Zoom"
              onChange={(event) => setZoom(Number(event.target.value))}
              className="h-1 w-full cursor-pointer appearance-none rounded-lg bg-slate-700"
            />
          </div>
          <div className="mt-4 flex gap-3">
            <button
              type="button"
              onClick={() => setImageSrc(null)}
              disabled={salvando}
              className="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-300 hover:border-slate-500"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={salvar}
              disabled={salvando || !area}
              className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
            >
              {salvando ? "Salvando..." : "Salvar foto"}
            </button>
          </div>
        </>
      ) : (
        <button
          type="button"
          title="Alterar foto"
          className="group relative overflow-hidden rounded-full"
          onClick={() => fileInputRef.current?.click()}
        >
          <Avatar name={name} url={url} size="lg" />
          <span className="absolute inset-0 hidden items-center justify-center bg-black/50 text-xs text-white group-hover:flex">
            Alterar foto
          </span>
        </button>
      )}

      <input type="file" accept="image/*" className="hidden" ref={fileInputRef} onChange={escolher} />
      {erro && <p className="mt-2 text-sm text-red-400">{erro}</p>}
    </div>
  );
}

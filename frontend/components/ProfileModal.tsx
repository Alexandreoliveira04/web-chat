"use client";

import { useState, useCallback, useRef } from "react";
import type { User } from "@/lib/types";
import { usersApi } from "@/lib/api";
import Modal from "./Modal";
import Avatar from "./Avatar";
import Cropper from "react-easy-crop";
import getCroppedImg from "@/lib/cropImage";

interface Props {
  me: User;
  onClose: () => void;
  onUpdate: (updated: User) => void;
}

export default function ProfileModal({ me, onClose, onUpdate }: Props) {
  const [name, setName] = useState(me.name);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Avatar Upload State
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [imageSrc, setImageSrc] = useState<string | null>(null);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [croppedAreaPixels, setCroppedAreaPixels] = useState<any>(null);

  const onCropComplete = useCallback((croppedArea: any, croppedAreaPixels: any) => {
    setCroppedAreaPixels(croppedAreaPixels);
  }, []);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      const imageDataUrl = await readFile(file);
      setImageSrc(imageDataUrl);
    }
  };

  const readFile = (file: File): Promise<string> => {
    return new Promise((resolve) => {
      const reader = new FileReader();
      reader.addEventListener('load', () => resolve(reader.result as string), false);
      reader.readAsDataURL(file);
    });
  };

  async function handleSave() {
    if (!name.trim()) {
      setError("O nome não pode ficar vazio.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      let updatedUser = me;
      
      // Update name if changed
      if (name.trim() !== me.name) {
        updatedUser = await usersApi.update(name.trim());
      }
      
      // Upload cropped avatar if there's one
      if (imageSrc && croppedAreaPixels) {
        const croppedImage = await getCroppedImg(imageSrc, croppedAreaPixels);
        if (croppedImage) {
          updatedUser = await usersApi.uploadAvatar(croppedImage);
        }
      }

      onUpdate(updatedUser);
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro ao atualizar perfil");
      setLoading(false);
    }
  }

  function handleDeleteAccount() {
    const firstConfirm = confirm("ATENÇÃO: Você tem certeza que deseja EXCLUIR sua conta?\n(Lembre-se: O backend atual não possui rota de exclusão no MVP.)");
    if (firstConfirm) {
      const secondConfirm = prompt("Para confirmar a exclusão, digite a palavra 'EXCLUIR':");
      if (secondConfirm === 'EXCLUIR') {
        alert("Sua conta foi removida com sucesso. (Simulação Frontend)");
      } else if (secondConfirm !== null) {
        alert("Palavra incorreta. A exclusão foi cancelada.");
      }
    }
  }

  return (
    <Modal title="Editar Perfil" onClose={onClose}>
      <div className="flex flex-col items-center gap-6 pb-2">
        {imageSrc ? (
          <div className="flex flex-col items-center w-full">
            <div className="relative w-full h-64 bg-slate-900 rounded-lg overflow-hidden mb-4">
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
            <div className="w-full flex items-center gap-4 px-2">
              <span className="text-xs text-slate-400">Zoom</span>
              <input
                type="range"
                value={zoom}
                min={1}
                max={3}
                step={0.1}
                aria-labelledby="Zoom"
                onChange={(e) => setZoom(Number(e.target.value))}
                className="w-full h-1 bg-slate-700 rounded-lg appearance-none cursor-pointer"
              />
            </div>
            <button 
              onClick={() => setImageSrc(null)}
              className="mt-4 text-xs text-slate-400 hover:text-slate-200"
            >
              Cancelar imagem
            </button>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-2">
             <button 
               className="relative group rounded-full overflow-hidden"
               onClick={() => fileInputRef.current?.click()}
             >
               <Avatar name={name} url={me.avatarUrl} size="lg" />
               <div className="absolute inset-0 bg-black/50 hidden group-hover:flex items-center justify-center text-xs text-white">
                 Alterar Foto
               </div>
             </button>
             <input 
               type="file" 
               accept="image/*" 
               className="hidden" 
               ref={fileInputRef}
               onChange={handleFileChange} 
             />
          </div>
        )}

        <div className="w-full">
          <label className="mb-2 block text-sm font-medium text-slate-300">
            Nome / Apelido
          </label>
          <input
            type="text"
            className="w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 outline-none focus:border-indigo-500"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>

        {error && <p className="text-sm text-red-500">{error}</p>}

        <div className="flex w-full gap-3 mt-2">
          <button
            type="button"
            onClick={handleDeleteAccount}
            className="rounded-lg border border-red-500/50 px-4 py-3 text-sm font-medium text-red-400 transition hover:bg-red-500/10"
          >
            Excluir Conta
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={loading}
            className="flex-1 rounded-lg bg-indigo-600 px-4 py-3 font-medium text-white transition hover:bg-indigo-700 disabled:opacity-50"
          >
            {loading ? "Salvando..." : "Salvar Alterações"}
          </button>
        </div>
      </div>
    </Modal>
  );
}

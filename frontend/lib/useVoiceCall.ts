"use client";

import { useCallback, useEffect, useRef, useState, type RefObject } from "react";
import type { Client } from "@stomp/stompjs";
import { callsApi } from "./api";
import { publishCallAction, publishCallSignal } from "./realtime";
import { playRingtone } from "./ringtone";
import type { CallEndReason, CallEvent, CallSignal } from "./types";

/**
 * idle: sem chamada
 * outgoing: liguei e está tocando do outro lado
 * incoming: estão me ligando
 * connecting: atendida, os navegadores estão negociando o áudio (WebRTC)
 * active: áudio fluindo
 */
export type CallStatus = "idle" | "outgoing" | "incoming" | "connecting" | "active";

export interface CallState {
  status: CallStatus;
  chatId: number | null;
  peerId: number | null;
  muted: boolean;
  startedAt: number | null;
}

interface Options {
  clientRef: RefObject<Client | null>;
  meId: number | null;
  nameOf: (userId: number) => string;
  onNotice: (message: string) => void;
}

const IDLE: CallState = { status: "idle", chatId: null, peerId: null, muted: false, startedAt: null };

// Os servidores STUN/TURN vêm do backend (GET /calls/ice-servers), porque o TURN tem
// credenciais. O STUN só descobre o endereço público; quando a conexão direta é bloqueada
// (4G, firewall), o áudio passa pelo TURN. Este STUN fica só de reserva, se a busca falhar.
const STUN_RESERVA: RTCIceServer[] = [{ urls: "stun:stun.l.google.com:19302" }];

const TEMPO_TOCANDO_MS = 30_000;

export function useVoiceCall({ clientRef, meId, nameOf, onNotice }: Options) {
  const [state, setState] = useState<CallState>(IDLE);

  const stateRef = useRef<CallState>(IDLE);
  const meIdRef = useRef(meId);
  const nameOfRef = useRef(nameOf);
  const onNoticeRef = useRef(onNotice);
  useEffect(() => {
    meIdRef.current = meId;
    nameOfRef.current = nameOf;
    onNoticeRef.current = onNotice;
  });

  const pcRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);
  const pendingIceRef = useRef<RTCIceCandidateInit[]>([]);
  const stopRingtoneRef = useRef<(() => void) | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const acceptedHereRef = useRef(false);
  const iceServersRef = useRef<RTCIceServer[] | null>(null);

  const update = useCallback((next: CallState) => {
    stateRef.current = next;
    setState(next);
  }, []);

  const notice = useCallback((message: string) => onNoticeRef.current(message), []);

  const stopRingtone = useCallback(() => {
    stopRingtoneRef.current?.();
    stopRingtoneRef.current = null;
  }, []);

  const cleanup = useCallback(() => {
    stopRingtone();
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    timeoutRef.current = null;

    pcRef.current?.close();
    pcRef.current = null;
    localStreamRef.current?.getTracks().forEach((track) => track.stop());
    localStreamRef.current = null;
    if (remoteAudioRef.current) remoteAudioRef.current.srcObject = null;
    pendingIceRef.current = [];
    acceptedHereRef.current = false;

    update(IDLE);
  }, [stopRingtone, update]);

  const sendSignal = useCallback(
    (chatId: number, type: "OFFER" | "ANSWER" | "ICE", data: unknown) => {
      publishCallSignal(clientRef.current, chatId, type, data);
    },
    [clientRef],
  );

  const getMicrophone = useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error("O navegador só libera o microfone em HTTPS ou localhost");
    }
    try {
      localStreamRef.current = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
    } catch {
      throw new Error("Permita o acesso ao microfone para fazer chamadas");
    }
  }, []);

  /** Busca uma vez por sessão; se falhar, liga só com o STUN de reserva e tenta de novo na próxima. */
  const carregarIceServers = useCallback(async () => {
    if (iceServersRef.current) return;
    try {
      iceServersRef.current = await callsApi.iceServers();
    } catch {
      console.warn("Sem a lista de servidores TURN; a chamada pode não conectar entre redes diferentes");
    }
  }, []);

  const createPeer = useCallback(
    (chatId: number) => {
      const pc = new RTCPeerConnection({ iceServers: iceServersRef.current ?? STUN_RESERVA });
      pcRef.current = pc;

      localStreamRef.current?.getTracks().forEach((track) => pc.addTrack(track, localStreamRef.current!));

      pc.onicecandidate = (event) => {
        if (event.candidate) sendSignal(chatId, "ICE", event.candidate.toJSON());
      };

      pc.ontrack = (event) => {
        if (!remoteAudioRef.current) remoteAudioRef.current = new Audio();
        remoteAudioRef.current.srcObject = event.streams[0];
        remoteAudioRef.current.play().catch(() => {});
      };

      pc.onconnectionstatechange = () => {
        if (pcRef.current !== pc) return;

        if (pc.connectionState === "connected" && stateRef.current.status === "connecting") {
          update({ ...stateRef.current, status: "active", startedAt: Date.now() });
        }
        if (pc.connectionState === "failed") {
          notice("Não foi possível conectar o áudio da chamada");
          publishCallAction(clientRef.current, chatId, "end");
          cleanup();
        }
      };

      return pc;
    },
    [clientRef, cleanup, notice, sendSignal, update],
  );

  const flushPendingIce = useCallback(async (pc: RTCPeerConnection) => {
    const pendentes = pendingIceRef.current;
    pendingIceRef.current = [];
    for (const candidate of pendentes) {
      await pc.addIceCandidate(candidate).catch(() => {});
    }
  }, []);

  const start = useCallback(
    async (chatId: number, peerId: number) => {
      if (stateRef.current.status !== "idle") {
        notice("Você já está em uma chamada");
        return;
      }

      update({ ...IDLE, status: "outgoing", chatId, peerId });
      try {
        await getMicrophone();
      } catch (falha) {
        notice((falha as Error).message);
        cleanup();
        return;
      }
      await carregarIceServers();

      if (!publishCallAction(clientRef.current, chatId, "start")) {
        notice("Sem conexão em tempo real");
        cleanup();
        return;
      }

      stopRingtoneRef.current = playRingtone(400, 0.05);
      timeoutRef.current = setTimeout(() => {
        if (stateRef.current.status === "outgoing") {
          notice(`${nameOfRef.current(peerId)} não atendeu`);
          publishCallAction(clientRef.current, chatId, "end");
          cleanup();
        }
      }, TEMPO_TOCANDO_MS);
    },
    [carregarIceServers, cleanup, clientRef, getMicrophone, notice, update],
  );

  const accept = useCallback(async () => {
    const atual = stateRef.current;
    if (atual.status !== "incoming" || atual.chatId === null) return;
    const chatId = atual.chatId;

    stopRingtone();
    try {
      await getMicrophone();
    } catch (falha) {
      notice((falha as Error).message);
      publishCallAction(clientRef.current, chatId, "end");
      cleanup();
      return;
    }
    await carregarIceServers();

    acceptedHereRef.current = true;
    createPeer(chatId);
    update({ ...stateRef.current, status: "connecting" });
    publishCallAction(clientRef.current, chatId, "accept");
  }, [carregarIceServers, cleanup, clientRef, createPeer, getMicrophone, notice, stopRingtone, update]);

  const hangUp = useCallback(() => {
    const chatId = stateRef.current.chatId;
    if (chatId === null) return;
    publishCallAction(clientRef.current, chatId, "end");
    cleanup();
  }, [cleanup, clientRef]);

  const toggleMute = useCallback(() => {
    const track = localStreamRef.current?.getAudioTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    update({ ...stateRef.current, muted: !track.enabled });
  }, [update]);

  const handleEvent = useCallback(
    async (event: CallEvent) => {
      const atual = stateRef.current;
      const souQuemLigou = event.callerId === meIdRef.current;
      const outro = souQuemLigou ? event.calleeId : event.callerId;

      if (event.type === "RINGING") {
        // Quem ligou já está em "outgoing"; outras abas minhas ignoram.
        if (!souQuemLigou && atual.status === "idle") {
          update({ ...IDLE, status: "incoming", chatId: event.chatId, peerId: event.callerId });
          stopRingtoneRef.current = playRingtone(660, 0.15);
        }
        return;
      }

      if (atual.chatId !== event.chatId) return;

      if (event.type === "ACCEPTED") {
        if (souQuemLigou && atual.status === "outgoing") {
          stopRingtone();
          if (timeoutRef.current) clearTimeout(timeoutRef.current);
          update({ ...atual, status: "connecting" });

          const pc = createPeer(event.chatId);
          const offer = await pc.createOffer();
          await pc.setLocalDescription(offer);
          sendSignal(event.chatId, "OFFER", offer);
        } else if (!souQuemLigou && !acceptedHereRef.current) {
          // Atendida em outra aba deste mesmo usuário.
          cleanup();
        }
        return;
      }

      const aviso = endNotice(event.reason, souQuemLigou, nameOfRef.current(outro));
      if (aviso) notice(aviso);
      cleanup();
    },
    [cleanup, createPeer, notice, sendSignal, stopRingtone, update],
  );

  const handleSignal = useCallback(
    async (signal: CallSignal) => {
      const pc = pcRef.current;
      if (!pc || stateRef.current.chatId !== signal.chatId) return;

      try {
        if (signal.type === "OFFER") {
          await pc.setRemoteDescription(signal.data as RTCSessionDescriptionInit);
          await flushPendingIce(pc);
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          sendSignal(signal.chatId, "ANSWER", answer);
        } else if (signal.type === "ANSWER") {
          await pc.setRemoteDescription(signal.data as RTCSessionDescriptionInit);
          await flushPendingIce(pc);
        } else if (pc.remoteDescription) {
          await pc.addIceCandidate(signal.data as RTCIceCandidateInit);
        } else {
          // O candidato chegou antes da descrição remota: guarda até ela chegar.
          pendingIceRef.current.push(signal.data as RTCIceCandidateInit);
        }
      } catch (falha) {
        console.error("Falha na negociação WebRTC", falha);
      }
    },
    [flushPendingIce, sendSignal],
  );

  /** A conexão STOMP caiu e voltou: o servidor já encerrou a chamada daquela sessão. */
  const reset = useCallback(() => {
    if (stateRef.current.status !== "idle") {
      notice("A chamada caiu");
      cleanup();
    }
  }, [cleanup, notice]);

  useEffect(() => cleanup, [cleanup]);

  return { state, start, accept, hangUp, toggleMute, handleEvent, handleSignal, reset };
}

function endNotice(reason: CallEndReason | null, souQuemLigou: boolean, nome: string): string | null {
  switch (reason) {
    case "UNAVAILABLE":
      return `${nome} está offline`;
    case "BUSY":
      return `${nome} está em outra chamada`;
    case "DECLINED":
      return souQuemLigou ? `${nome} recusou a chamada` : null;
    case "CANCELED":
      return souQuemLigou ? null : `Chamada perdida de ${nome}`;
    case "HUNG_UP":
      return `${nome} encerrou a chamada`;
    case "DISCONNECTED":
      return "A chamada caiu";
    default:
      return null;
  }
}

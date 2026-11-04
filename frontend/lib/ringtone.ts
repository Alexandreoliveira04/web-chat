/**
 * Toque gerado com Web Audio, sem arquivo de som: dois bipes curtos a cada 2 segundos.
 * Devolve a função que para o toque.
 */
export function playRingtone(frequencia: number, volume: number): () => void {
  try {
    const ctx = new AudioContext();
    let parado = false;

    const tocar = () => {
      if (parado) return;
      const inicio = ctx.currentTime;
      for (const atraso of [0, 0.4]) {
        const oscilador = ctx.createOscillator();
        const ganho = ctx.createGain();
        oscilador.frequency.value = frequencia;
        ganho.gain.setValueAtTime(volume, inicio + atraso);
        ganho.gain.setValueAtTime(0, inicio + atraso + 0.3);
        oscilador.connect(ganho).connect(ctx.destination);
        oscilador.start(inicio + atraso);
        oscilador.stop(inicio + atraso + 0.3);
      }
    };

    tocar();
    const intervalo = setInterval(tocar, 2000);

    return () => {
      parado = true;
      clearInterval(intervalo);
      ctx.close().catch(() => {});
    };
  } catch {
    return () => {};
  }
}

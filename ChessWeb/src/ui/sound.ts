export type SoundCue = 'move' | 'capture' | 'end'

let ctx: AudioContext | null = null

/**
 * Short synthesised effects (no audio files). Browsers only allow audio after a user gesture; every cue is triggered by one.
 * Failures (no Web Audio, blocked autoplay) are ignored: the game must never depend on sound.
 */
export function playSound(cue: SoundCue): void {
  try {
    ctx ??= new AudioContext()
    if (ctx.state === 'suspended') void ctx.resume()
    const now = ctx.currentTime
    const tones: Array<[number, number, number, number]> =
      cue === 'move' ? [[190, 0.5, 0.12, 0]]
        : cue === 'capture' ? [[240, 0.55, 0.18, 0], [120, 0.45, 0.18, 0]]
          : [[523, 0.3, 0.5, 0], [659, 0.26, 0.5, 0.12], [784, 0.26, 0.5, 0.24]]
    for (const [freq, gain, dur, start] of tones) {
      const osc = ctx.createOscillator()
      const g = ctx.createGain()
      osc.frequency.setValueAtTime(freq, now + start)
      if (cue !== 'end') osc.frequency.exponentialRampToValueAtTime(freq / 2, now + start + dur)
      g.gain.setValueAtTime(0.0001, now + start)
      g.gain.exponentialRampToValueAtTime(gain * 0.5, now + start + 0.004)
      g.gain.exponentialRampToValueAtTime(0.0001, now + start + dur)
      osc.connect(g).connect(ctx.destination)
      osc.start(now + start)
      osc.stop(now + start + dur + 0.02)
    }
  } catch {
    /* ignore */
  }
}

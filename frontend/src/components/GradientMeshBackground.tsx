// Sitewide ambient background for the marketing landing page.
//
// Two layers, both purely decorative (aria-hidden, pointer-events-none):
//   1. A canvas "gradient mesh" — a handful of large, slowly drifting radial
//      colour blobs composited with `lighter` so they blend into soft aurora
//      washes. Kept in canvas rather than CSS so the blobs can drift on
//      independent Lissajous paths without a stack of blur filters (which are
//      expensive to composite at this size).
//   2. A particle network canvas — nodes drift, nearby nodes are linked, and
//      the cursor gently attracts nearby particles. Evokes the AI/graph theme.
//
// Both respect `prefers-reduced-motion`: the mesh renders one static frame and
// the particle field stops animating.

import { useEffect, useRef, type RefObject } from 'react'

type Blob = {
  hue: [number, number, number]
  radius: number
  cx: number
  cy: number
  ax: number
  ay: number
  sx: number
  sy: number
  phase: number
  alpha: number
}

// KPMG Blue / Cobalt aurora — all blobs stay within the brand palette.
const BLOBS: Blob[] = [
  { hue: [71, 138, 255], radius: 0.42, cx: 0.18, cy: 0.16, ax: 0.07, ay: 0.06, sx: 0.11, sy: 0.08, phase: 0.0, alpha: 0.5 }, // kpmg-400
  { hue: [71, 194, 255], radius: 0.38, cx: 0.82, cy: 0.24, ax: 0.08, ay: 0.05, sx: 0.09, sy: 0.13, phase: 1.7, alpha: 0.45 }, // cobalt-400
  { hue: [0, 51, 141], radius: 0.34, cx: 0.62, cy: 0.72, ax: 0.06, ay: 0.07, sx: 0.13, sy: 0.1, phase: 3.1, alpha: 0.32 }, // kpmg-600
  { hue: [0, 145, 218], radius: 0.28, cx: 0.28, cy: 0.78, ax: 0.05, ay: 0.05, sx: 0.15, sy: 0.12, phase: 4.4, alpha: 0.28 }, // cobalt-600
  { hue: [136, 178, 252], radius: 0.3, cx: 0.5, cy: 0.4, ax: 0.09, ay: 0.04, sx: 0.07, sy: 0.16, phase: 5.6, alpha: 0.24 }, // kpmg-300
]

const PARTICLE_DENSITY = 1 / 16000 // particles per css pixel
const MAX_PARTICLES = 110
const LINK_DISTANCE = 140
const CURSOR_RADIUS = 190

// Keeps a canvas's backing store in sync with its CSS box. Takes the ref object
// (not `ref.current`) because on the first render `current` is still null and
// assigning it never re-runs the effect.
function useCanvasSize(ref: RefObject<HTMLCanvasElement>) {
  const size = useRef({ w: 0, h: 0, dpr: 1 })
  useEffect(() => {
    const canvas = ref.current
    if (!canvas) return
    const fit = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      const w = canvas.clientWidth
      const h = canvas.clientHeight
      canvas.width = Math.max(1, Math.floor(w * dpr))
      canvas.height = Math.max(1, Math.floor(h * dpr))
      size.current = { w, h, dpr }
    }
    fit()
    window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [ref])
  return size
}

export default function GradientMeshBackground({ className = '' }: { className?: string }) {
  const meshRef = useRef<HTMLCanvasElement>(null)
  const dotsRef = useRef<HTMLCanvasElement>(null)
  const meshSize = useCanvasSize(meshRef)
  const dotsSize = useCanvasSize(dotsRef)

  // ---- layer 1: drifting gradient mesh -------------------------------------
  useEffect(() => {
    const canvas = meshRef.current
    const ctx = canvas?.getContext('2d')
    if (!canvas || !ctx) return

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    let raf = 0
    const start = performance.now()

    const draw = (now: number) => {
      const { w, h, dpr } = meshSize.current
      if (!w || !h) {
        raf = requestAnimationFrame(draw)
        return
      }
      const t = reduced ? 0 : (now - start) / 1000

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, w, h)
      ctx.globalCompositeOperation = 'lighter'

      const base = Math.max(w, h)
      for (const b of BLOBS) {
        const x = (b.cx + Math.sin(t * b.sx + b.phase) * b.ax) * w
        const y = (b.cy + Math.cos(t * b.sy + b.phase) * b.ay) * h
        const r = b.radius * base
        const g = ctx.createRadialGradient(x, y, 0, x, y, r)
        const [rr, gg, bb] = b.hue
        g.addColorStop(0, `rgba(${rr},${gg},${bb},${b.alpha})`)
        g.addColorStop(0.55, `rgba(${rr},${gg},${bb},${b.alpha * 0.28})`)
        g.addColorStop(1, `rgba(${rr},${gg},${bb},0)`)
        ctx.fillStyle = g
        ctx.beginPath()
        ctx.arc(x, y, r, 0, Math.PI * 2)
        ctx.fill()
      }
      ctx.globalCompositeOperation = 'source-over'

      if (!reduced) raf = requestAnimationFrame(draw)
    }

    raf = requestAnimationFrame(draw)
    return () => cancelAnimationFrame(raf)
  }, [meshSize])

  // ---- layer 2: particle network -------------------------------------------
  useEffect(() => {
    const canvas = dotsRef.current
    const ctx = canvas?.getContext('2d')
    if (!canvas || !ctx) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    type P = { x: number; y: number; vx: number; vy: number; r: number }
    let particles: P[] = []
    let raf = 0
    const pointer = { x: -9999, y: -9999 }

    const seed = () => {
      const { w, h } = dotsSize.current
      const count = Math.min(MAX_PARTICLES, Math.round(w * h * PARTICLE_DENSITY))
      particles = Array.from({ length: count }, () => ({
        x: Math.random() * w,
        y: Math.random() * h,
        vx: (Math.random() - 0.5) * 0.22,
        vy: (Math.random() - 0.5) * 0.22,
        r: 1 + Math.random() * 1.8,
      }))
    }

    const onPointer = (e: PointerEvent) => {
      const rect = canvas.getBoundingClientRect()
      pointer.x = e.clientX - rect.left
      pointer.y = e.clientY - rect.top
    }
    const onLeave = () => {
      pointer.x = -9999
      pointer.y = -9999
    }

    const tick = () => {
      const { w, h, dpr } = dotsSize.current
      if (!w || !h) {
        raf = requestAnimationFrame(tick)
        return
      }
      if (particles.length === 0) seed()

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, w, h)

      for (const p of particles) {
        // gentle cursor attraction
        const dx = pointer.x - p.x
        const dy = pointer.y - p.y
        const d2 = dx * dx + dy * dy
        if (d2 < CURSOR_RADIUS * CURSOR_RADIUS && d2 > 1) {
          const d = Math.sqrt(d2)
          const pull = (1 - d / CURSOR_RADIUS) * 0.045
          p.vx += (dx / d) * pull
          p.vy += (dy / d) * pull
        }

        p.vx *= 0.992
        p.vy *= 0.992
        p.x += p.vx
        p.y += p.vy

        if (p.x < -20) p.x = w + 20
        if (p.x > w + 20) p.x = -20
        if (p.y < -20) p.y = h + 20
        if (p.y > h + 20) p.y = -20
      }

      // links
      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const a = particles[i]
          const b = particles[j]
          const dx = a.x - b.x
          const dy = a.y - b.y
          const d2 = dx * dx + dy * dy
          if (d2 > LINK_DISTANCE * LINK_DISTANCE) continue
          const alpha = (1 - Math.sqrt(d2) / LINK_DISTANCE) * 0.34
          ctx.strokeStyle = `rgba(0,51,141,${alpha})`
          ctx.lineWidth = 1
          ctx.beginPath()
          ctx.moveTo(a.x, a.y)
          ctx.lineTo(b.x, b.y)
          ctx.stroke()
        }
      }

      // nodes
      for (const p of particles) {
        ctx.fillStyle = 'rgba(0,145,218,0.42)'
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
        ctx.fill()
      }

      raf = requestAnimationFrame(tick)
    }

    const onResize = () => {
      particles = []
    }

    window.addEventListener('pointermove', onPointer)
    window.addEventListener('pointerleave', onLeave)
    window.addEventListener('resize', onResize)
    raf = requestAnimationFrame(tick)
    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('pointermove', onPointer)
      window.removeEventListener('pointerleave', onLeave)
      window.removeEventListener('resize', onResize)
    }
  }, [dotsSize])

  return (
    <div className={`pointer-events-none overflow-hidden ${className}`} aria-hidden="true">
      {/* paper base */}
      <div className="absolute inset-0 bg-[#fbfbfe]" />

      {/* drifting colour mesh, softened and faded toward the page bottom */}
      <canvas
        ref={meshRef}
        className="absolute inset-0 h-full w-full opacity-[0.55] blur-[60px] [mask-image:linear-gradient(to_bottom,black_0%,black_55%,transparent_100%)]"
      />

      {/* fine engineering grid */}
      <div className="bg-grid absolute inset-0 [mask-image:radial-gradient(ellipse_70%_60%_at_50%_0%,black,transparent)]" />

      {/* interactive particle graph */}
      <canvas
        ref={dotsRef}
        className="absolute inset-0 h-full w-full opacity-70 [mask-image:radial-gradient(ellipse_80%_70%_at_50%_10%,black,transparent)]"
      />

      {/* subtle noise to kill gradient banding */}
      <div className="noise-overlay absolute inset-0" />
    </div>
  )
}

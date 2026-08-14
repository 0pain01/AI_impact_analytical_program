// Shared GSAP setup + small animation hooks used by the marketing landing page.
//
// Everything here degrades gracefully: if the user has `prefers-reduced-motion:
// reduce` set, elements are made visible immediately with no tweening, so the
// page is fully readable without motion.

import { useEffect, useRef } from 'react'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import Lenis from 'lenis'

gsap.registerPlugin(ScrollTrigger)

export const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

export const EASE = 'power3.out'

/**
 * Lenis inertia scrolling, driven by GSAP's ticker so ScrollTrigger and the
 * smooth-scroll position stay on the same frame (otherwise triggers fire one
 * frame late and reveals look jittery).
 */
export function useSmoothScroll() {
  useEffect(() => {
    if (prefersReducedMotion()) return

    const lenis = new Lenis({
      duration: 1.05,
      easing: (t: number) => 1 - Math.pow(1 - t, 3),
      smoothWheel: true,
    })

    const raf = (time: number) => lenis.raf(time * 1000)
    lenis.on('scroll', ScrollTrigger.update)
    gsap.ticker.add(raf)
    gsap.ticker.lagSmoothing(0)

    // Lenis owns the scroll position, so native anchor jumps would fight it —
    // route in-page `#hash` links through Lenis instead.
    const onAnchorClick = (e: MouseEvent) => {
      const anchor = (e.target as HTMLElement | null)?.closest?.('a[href^="#"]') as HTMLAnchorElement | null
      const hash = anchor?.getAttribute('href')
      if (!hash || hash === '#') return
      const target = document.querySelector(hash)
      if (!target) return
      e.preventDefault()
      lenis.scrollTo(target as HTMLElement, { offset: -80 })
    }

    document.addEventListener('click', onAnchorClick)

    return () => {
      document.removeEventListener('click', onAnchorClick)
      gsap.ticker.remove(raf)
      lenis.destroy()
    }
  }, [])
}

/**
 * Scroll-triggered staggered reveal. Every descendant carrying `data-reveal`
 * fades and rises into place as the container enters the viewport.
 */
export function useRevealOnScroll<T extends HTMLElement>(options?: { stagger?: number; y?: number }) {
  const ref = useRef<T>(null)
  const { stagger = 0.08, y = 28 } = options ?? {}

  useEffect(() => {
    const root = ref.current
    if (!root) return
    const targets = root.querySelectorAll<HTMLElement>('[data-reveal]')
    if (targets.length === 0) return

    if (prefersReducedMotion()) {
      gsap.set(targets, { opacity: 1, y: 0 })
      return
    }

    const ctx = gsap.context(() => {
      gsap.fromTo(
        targets,
        { opacity: 0, y, filter: 'blur(6px)' },
        {
          opacity: 1,
          y: 0,
          filter: 'blur(0px)',
          duration: 0.9,
          ease: EASE,
          stagger,
          scrollTrigger: { trigger: root, start: 'top 82%', once: true },
        },
      )
    }, root)

    return () => ctx.revert()
  }, [stagger, y])

  return ref
}

/** Counts a number up when it scrolls into view. Returns the ref to attach. */
export function useCountUp<T extends HTMLElement>(
  to: number,
  { decimals = 0, prefix = '', suffix = '', duration = 1.6 } = {},
) {
  const ref = useRef<T>(null)

  useEffect(() => {
    const el = ref.current
    if (!el) return

    const format = (n: number) => `${prefix}${n.toFixed(decimals)}${suffix}`

    if (prefersReducedMotion()) {
      el.textContent = format(to)
      return
    }

    const counter = { value: 0 }
    const ctx = gsap.context(() => {
      gsap.to(counter, {
        value: to,
        duration,
        ease: 'power2.out',
        onUpdate: () => {
          el.textContent = format(counter.value)
        },
        scrollTrigger: { trigger: el, start: 'top 88%', once: true },
      })
    }, el)

    return () => ctx.revert()
  }, [to, decimals, prefix, suffix, duration])

  return ref
}

/**
 * Subtle parallax: translates the element as it moves through the viewport.
 * `strength` is in pixels of total travel across the full scroll range.
 */
export function useParallax<T extends HTMLElement>(strength = 60) {
  const ref = useRef<T>(null)

  useEffect(() => {
    const el = ref.current
    if (!el || prefersReducedMotion()) return

    const ctx = gsap.context(() => {
      gsap.fromTo(
        el,
        { y: strength / 2 },
        {
          y: -strength / 2,
          ease: 'none',
          scrollTrigger: { trigger: el, start: 'top bottom', end: 'bottom top', scrub: true },
        },
      )
    }, el)

    return () => ctx.revert()
  }, [strength])

  return ref
}

export { gsap, ScrollTrigger }

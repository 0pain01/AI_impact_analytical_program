import { useEffect, useRef, useState } from 'react'
import {
  Activity,
  ArrowRight,
  BarChart3,
  BrainCircuit,
  Check,
  ChevronRight,
  DollarSign,
  GaugeCircle,
  GitPullRequest,
  LayoutGrid,
  Link2,
  PieChart,
  Plug,
  ShieldCheck,
  Sparkles,
  Timer,
  TrendingUp,
  Workflow,
  Zap,
  type LucideIcon,
} from 'lucide-react'
import { Area, AreaChart, Bar, BarChart, ResponsiveContainer, Tooltip, XAxis } from 'recharts'
import aiImpactEvaluationLogo from '../assets/ai-impact-evaluation-logo.png'
import GradientMeshBackground from '../components/GradientMeshBackground'
import { gsap, prefersReducedMotion, useCountUp, useParallax, useRevealOnScroll, useSmoothScroll } from '../lib/motion'

type Icon = LucideIcon

const FEATURES: { title: string; body: string; icon: Icon; accent: string }[] = [
  {
    title: 'DORA & delivery metrics',
    body: 'Deployment frequency, lead time, change failure rate and MTTR — computed automatically from your existing tools, with zero manual tagging.',
    icon: Zap,
    accent: 'from-kpmg-500 to-cobalt-600',
  },
  {
    title: 'Investment Profile',
    body: 'See planned vs. unplanned engineering time by team, before it quietly derails a milestone.',
    icon: PieChart,
    accent: 'from-cobalt-500 to-cobalt-700',
  },
  {
    title: 'Code Review Analytics',
    body: 'Spot aging PRs and unbalanced review load before they block a release.',
    icon: GitPullRequest,
    accent: 'from-sky-500 to-kpmg-600',
  },
  {
    title: 'AI ROI',
    body: 'Quantify AI coding-assistant adoption and its dollar return, in the language your board speaks.',
    icon: Sparkles,
    accent: 'from-amber-500 to-orange-600',
  },
  {
    title: 'Role-based dashboards',
    body: 'Executive Cockpit, Manager team views and opt-in IC growth views — never surveillance.',
    icon: LayoutGrid,
    accent: 'from-teal-500 to-emerald-600',
  },
  {
    title: 'Governed & auditable',
    body: 'Least-privilege connectors, RBAC and a 12-month audit trail out of the box.',
    icon: ShieldCheck,
    accent: 'from-rose-500 to-pink-600',
  },
]

const INTEGRATIONS = [
  'GitHub',
  'GitLab',
  'Jira',
  'GitHub Actions',
  'Jenkins',
  'SonarQube',
  'PagerDuty',
  'GitHub Copilot',
  'Cursor',
  'Claude Code',
]

const METRICS: { value: number; decimals?: number; prefix?: string; suffix?: string; label: string; icon: Icon }[] = [
  { value: 30, suffix: ' min', label: 'to your first dashboard', icon: Timer },
  { value: 4, label: 'DORA metrics, automated', icon: GaugeCircle },
  { value: 0, label: 'manual status updates required', icon: Check },
  { value: 12, suffix: ' mo', label: 'audit trail retention', icon: ShieldCheck },
]

const STEPS: { title: string; body: string; icon: Icon }[] = [
  {
    title: 'Connect your stack',
    body: 'OAuth into GitHub, Jira, CI, SonarQube and PagerDuty with least-privilege scopes. Nothing is written back.',
    icon: Plug,
  },
  {
    title: 'We normalise the mess',
    body: 'Contributor identities are resolved across tools and team structure is inferred — no spreadsheets to maintain.',
    icon: Workflow,
  },
  {
    title: 'Decide with evidence',
    body: 'Executive, manager and IC dashboards populate automatically, every metric traceable to its raw source.',
    icon: BrainCircuit,
  },
]

const AI_ROI_CARDS: { title: string; body: string; icon: Icon }[] = [
  {
    title: 'Show AI ROI',
    body: 'Know whether AI is speeding up delivery or quietly creating more review work and rework.',
    icon: TrendingUp,
  },
  {
    title: 'Quantify adoption',
    body: 'See which teams and repos actually use AI assistants, and what that saves in engineering hours.',
    icon: BarChart3,
  },
]

const AI_SPEND_BY_TEAM = [
  { team: 'Platform', spend: 22 },
  { team: 'Payments', spend: 41 },
  { team: 'Growth', spend: 31 },
]

const DELIVERY_TREND = [
  { d: 'W1', v: 18 },
  { d: 'W2', v: 24 },
  { d: 'W3', v: 21 },
  { d: 'W4', v: 33 },
  { d: 'W5', v: 39 },
  { d: 'W6', v: 47 },
  { d: 'W7', v: 58 },
]

const TRUST_POINTS = ['No keystroke or idle tracking', 'No manual tagging', 'Read-only connectors']

/* ------------------------------------------------------------------ */
/* Hero                                                                */
/* ------------------------------------------------------------------ */

function Hero({ onLogin }: { onLogin: () => void }) {
  const rootRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const root = rootRef.current
    if (!root) return

    const targets = root.querySelectorAll<HTMLElement>('[data-hero]')
    if (prefersReducedMotion()) {
      gsap.set(targets, { opacity: 1, y: 0, filter: 'blur(0px)' })
      return
    }

    const ctx = gsap.context(() => {
      gsap
        .timeline({ defaults: { ease: 'power3.out' } })
        .fromTo(
          targets,
          { opacity: 0, y: 34, filter: 'blur(10px)' },
          { opacity: 1, y: 0, filter: 'blur(0px)', duration: 1, stagger: 0.09 },
        )
        .fromTo(
          panelRef.current,
          { opacity: 0, y: 60, rotateX: 12, scale: 0.96 },
          { opacity: 1, y: 0, rotateX: 0, scale: 1, duration: 1.2 },
          '-=0.7',
        )
    }, root)

    return () => ctx.revert()
  }, [])

  // Pointer-tracked tilt on the dashboard preview.
  useEffect(() => {
    const panel = panelRef.current
    if (!panel || prefersReducedMotion()) return

    const setX = gsap.quickTo(panel, 'rotateY', { duration: 0.6, ease: 'power3.out' })
    const setY = gsap.quickTo(panel, 'rotateX', { duration: 0.6, ease: 'power3.out' })

    const onMove = (e: PointerEvent) => {
      const r = panel.getBoundingClientRect()
      setX(((e.clientX - (r.left + r.width / 2)) / r.width) * 10)
      setY((-(e.clientY - (r.top + r.height / 2)) / r.height) * 8)
    }
    const onLeave = () => {
      setX(0)
      setY(0)
    }

    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerleave', onLeave)
    return () => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerleave', onLeave)
    }
  }, [])

  return (
    <section ref={rootRef} className="relative isolate">
      <div className="mx-auto max-w-6xl px-6 pb-16 pt-20 text-center sm:pt-28">
        <span
          data-hero
          className="inline-flex items-center gap-2 rounded-full border border-kpmg-200/70 bg-white/70 px-3.5 py-1.5 text-xs font-medium text-kpmg-700 shadow-sm shadow-kpmg-100/60 backdrop-blur"
        >
          <span className="relative flex h-1.5 w-1.5">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-kpmg-400 opacity-75" />
            <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-kpmg-500" />
          </span>
          AI-native Software Engineering Intelligence
        </span>

        <h1
          data-hero
          className="mx-auto mt-7 max-w-5xl text-[2.6rem] font-bold leading-[1.08] tracking-tight text-slate-900 sm:text-6xl lg:text-[4.25rem]"
        >
         AI Impact 

          <span className="text-gradient animate-shimmer bg-clip-text"> Evaluation</span>  <br />Intelligence & Analytics Platform
        </h1>

        <p data-hero className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-slate-600">
          Bring GitHub, Jira, CI/CD and AI assistants like Copilot, Cursor and Claude Code into one
          intelligent platform — DORA metrics, investment profile and AI ROI, computed automatically
          from the tools you already run.
        </p>

        <div data-hero className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <button
            onClick={onLogin}
            className="group relative inline-flex w-full items-center justify-center gap-2 overflow-hidden rounded-xl bg-[#00338D] px-7 py-3.5 text-sm font-semibold text-white shadow-lg shadow-kpmg-900/20 transition-all duration-300 hover:-translate-y-0.5 hover:bg-kpmg-700 hover:shadow-xl active:translate-y-0 sm:w-auto"
          >
            <span className="absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/25 to-transparent transition-transform duration-700 group-hover:translate-x-full" />
            <span className="relative">Log in to Cockpit</span>
            <ArrowRight className="relative h-4 w-4 transition-transform duration-300 group-hover:translate-x-1" />
          </button>
          <a
            href="#features"
            className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white/70 px-7 py-3.5 text-sm font-semibold text-slate-700 backdrop-blur transition-all duration-300 hover:-translate-y-0.5 hover:border-kpmg-200 hover:text-kpmg-700 hover:shadow-md hover:shadow-kpmg-100 sm:w-auto"
          >
            See what it tracks
            <ChevronRight className="h-4 w-4" />
          </a>
        </div>

        <ul data-hero className="mt-7 flex flex-wrap items-center justify-center gap-x-6 gap-y-2">
          {TRUST_POINTS.map((t) => (
            <li key={t} className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-500">
              <Check className="h-3.5 w-3.5 text-emerald-500" strokeWidth={3} />
              {t}
            </li>
          ))}
        </ul>

        {/* dashboard preview */}
        <div className="mt-16 [perspective:1600px]">
          <div ref={panelRef} className="relative mx-auto max-w-5xl [transform-style:preserve-3d]">
            <div className="pointer-events-none absolute -inset-6 -z-10 rounded-[2rem] bg-gradient-to-tr from-kpmg-400/25 via-cobalt-400/20 to-sky-400/25 blur-3xl" />
            <DashboardPreview />
          </div>
        </div>
      </div>
    </section>
  )
}

function DashboardPreview() {
  const KPIS = [
    { label: 'Deploy frequency', value: '4.8/day', delta: '+18%', up: true, icon: Zap },
    { label: 'Lead time', value: '19 h', delta: '−31%', up: true, icon: Timer },
    { label: 'Change failure', value: '3.1%', delta: '−0.8pt', up: true, icon: Activity },
    { label: 'AI ROI', value: '$54.5K', delta: '+12%', up: true, icon: Sparkles },
  ]

  return (
    <div className="overflow-hidden rounded-2xl border border-white/60 bg-white/80 shadow-2xl shadow-kpmg-900/10 ring-1 ring-slate-900/5 backdrop-blur-xl">
      {/* chrome */}
      <div className="flex items-center gap-2 border-b border-slate-100 bg-slate-50/70 px-4 py-3">
        <span className="h-2.5 w-2.5 rounded-full bg-rose-300" />
        <span className="h-2.5 w-2.5 rounded-full bg-amber-300" />
        <span className="h-2.5 w-2.5 rounded-full bg-emerald-300" />
        <span className="ml-3 rounded-md bg-white px-2.5 py-1 text-[11px] font-medium text-slate-400 ring-1 ring-slate-200">
          Executive Cockpit
        </span>
      </div>

      <div className="grid gap-4 p-5 text-left sm:grid-cols-4">
        {KPIS.map((k) => (
          <div key={k.label} className="rounded-xl border border-slate-100 bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <k.icon className="h-4 w-4 text-kpmg-500" />
              <span
                className={`rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${
                  k.up ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'
                }`}
              >
                {k.delta}
              </span>
            </div>
            <p className="mt-3 text-xl font-bold tracking-tight text-slate-900">{k.value}</p>
            <p className="mt-0.5 text-[11px] text-slate-500">{k.label}</p>
          </div>
        ))}
      </div>

      <div className="px-5 pb-5">
        <div className="rounded-xl border border-slate-100 bg-white p-4">
          <div className="flex items-center justify-between">
            <p className="text-xs font-semibold text-slate-700">Delivery throughput</p>
            <p className="text-[11px] text-slate-400">last 7 weeks</p>
          </div>
          <div className="mt-3 h-32">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={DELIVERY_TREND} margin={{ left: 0, right: 0, top: 4, bottom: 0 }}>
                <defs>
                  <linearGradient id="heroArea" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#00338D" stopOpacity={0.45} />
                    <stop offset="100%" stopColor="#00338D" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="d" tick={{ fontSize: 10, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
                <Area
                  type="monotone"
                  dataKey="v"
                  stroke="#00338D"
                  strokeWidth={2.5}
                  fill="url(#heroArea)"
                  animationDuration={1400}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Sections                                                            */
/* ------------------------------------------------------------------ */

function MetricStat({ metric }: { metric: (typeof METRICS)[number] }) {
  const ref = useCountUp<HTMLParagraphElement>(metric.value, {
    decimals: metric.decimals ?? 0,
    prefix: metric.prefix ?? '',
    suffix: metric.suffix ?? '',
  })

  return (
    <div data-reveal className="group text-center">
      <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-white/80 shadow-sm ring-1 ring-slate-200/70 transition duration-300 group-hover:-translate-y-1 group-hover:ring-kpmg-200">
        <metric.icon className="h-5 w-5 text-kpmg-600" />
      </div>
      <p ref={ref} className="mt-4 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
        0
      </p>
      <p className="mt-1 text-sm text-slate-500">{metric.label}</p>
    </div>
  )
}

function MetricsBand() {
  const ref = useRevealOnScroll<HTMLDivElement>({ stagger: 0.1 })
  return (
    <section
      id="metrics"
      className="relative border-y border-slate-200/60 bg-white/50 backdrop-blur-sm"
    >
      <div ref={ref} className="mx-auto grid max-w-5xl grid-cols-2 gap-10 px-6 py-14 sm:grid-cols-4">
        {METRICS.map((m) => (
          <MetricStat key={m.label} metric={m} />
        ))}
      </div>
    </section>
  )
}

function Features() {
  const ref = useRevealOnScroll<HTMLDivElement>({ stagger: 0.07 })

  return (
    <section id="features" ref={ref} className="mx-auto max-w-6xl px-6 py-24 sm:py-28">
      <div className="mx-auto max-w-2xl text-center">
        <span
          data-reveal
          className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-[0.18em] text-kpmg-600"
        >
          <Sparkles className="h-3.5 w-3.5" />
          Platform capabilities
        </span>
        <h2 data-reveal className="mt-4 text-3xl font-bold tracking-tight text-slate-900 sm:text-[2.6rem] sm:leading-tight">
          Everything a leader needs, nothing that feels like surveillance
        </h2>
        <p data-reveal className="mt-4 text-lg text-slate-600">
          Every metric traces back to raw tool data — never to a keystroke, an idle timer, or a
          manually filled-in status field.
        </p>
      </div>

      <div className="mt-14 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {FEATURES.map((f, i) => (
          <article
            key={f.title}
            data-reveal
            className="group relative h-full overflow-hidden rounded-2xl border border-white/70 bg-white/70 p-7 shadow-sm shadow-slate-200/50 ring-1 ring-slate-900/[0.03] backdrop-blur-md transition-all duration-500 ease-out hover:-translate-y-1.5 hover:border-kpmg-200/70 hover:shadow-2xl hover:shadow-kpmg-200/40"
          >
            <span className="pointer-events-none absolute -right-10 -top-10 h-32 w-32 rounded-full bg-gradient-to-br from-kpmg-300/0 to-cobalt-300/0 blur-2xl transition-all duration-700 group-hover:from-kpmg-300/50 group-hover:to-cobalt-300/40" />
            <div className="relative flex items-start justify-between">
              <div
                className={`flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br ${f.accent} shadow-lg shadow-kpmg-500/20 ring-1 ring-white/40 transition-transform duration-500 group-hover:scale-110 group-hover:rotate-3`}
              >
                <f.icon className="h-5 w-5 text-white" strokeWidth={2} />
              </div>
              <span className="font-mono text-[11px] font-semibold text-slate-300 transition-colors duration-300 group-hover:text-kpmg-400">
                {String(i + 1).padStart(2, '0')}
              </span>
            </div>
            <h3 className="relative mt-6 text-base font-semibold tracking-tight text-slate-900">{f.title}</h3>
            <p className="relative mt-2.5 text-sm leading-relaxed text-slate-600">{f.body}</p>
          </article>
        ))}
      </div>
    </section>
  )
}

function HowItWorks() {
  const ref = useRevealOnScroll<HTMLDivElement>({ stagger: 0.12 })

  return (
    <section id="how" className="relative border-y border-slate-200/60 bg-white/50 py-24 backdrop-blur-sm">
      <div ref={ref} className="mx-auto max-w-6xl px-6">
        <div className="mx-auto max-w-2xl text-center">
          <span
            data-reveal
            className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-[0.18em] text-kpmg-600"
          >
            <Link2 className="h-3.5 w-3.5" />
            How it works
          </span>
          <h2 data-reveal className="mt-4 text-3xl font-bold tracking-tight text-slate-900 sm:text-[2.4rem]">
            Live in an afternoon, not a quarter
          </h2>
        </div>

        <div className="relative mt-14 grid grid-cols-1 gap-8 md:grid-cols-3">
          {/* connector line */}
          <div className="pointer-events-none absolute left-0 right-0 top-6 hidden h-px bg-gradient-to-r from-transparent via-kpmg-300/60 to-transparent md:block" />
          {STEPS.map((s, i) => (
            <div key={s.title} data-reveal className="relative text-center md:text-left">
              <div className="relative z-10 mx-auto flex h-12 w-12 items-center justify-center rounded-full border border-kpmg-200 bg-white shadow-md shadow-kpmg-100 md:mx-0">
                <s.icon className="h-5 w-5 text-kpmg-600" />
              </div>
              <p className="mt-5 text-[11px] font-semibold uppercase tracking-widest text-kpmg-500">
                Step {i + 1}
              </p>
              <h3 className="mt-1.5 text-lg font-semibold tracking-tight text-slate-900">{s.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-600">{s.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

function AiRoi({ onLogin }: { onLogin: () => void }) {
  const ref = useRevealOnScroll<HTMLDivElement>({ stagger: 0.1 })
  const floatRef = useParallax<HTMLDivElement>(50)

  return (
    <section id="ai-roi" className="relative overflow-hidden py-24 sm:py-28">
      <div ref={ref} className="relative mx-auto grid max-w-6xl grid-cols-1 items-center gap-16 px-6 lg:grid-cols-2">
        <div>
          <span
            data-reveal
            className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-[0.18em] text-kpmg-600"
          >
            <DollarSign className="h-3.5 w-3.5" />
            AI ROI
          </span>
          <h2 data-reveal className="mt-4 text-3xl font-bold leading-tight tracking-tight text-slate-900 sm:text-[2.6rem]">
            Prove what your engineering investment actually delivers
          </h2>
          <p data-reveal className="mt-4 text-lg text-slate-600">
            Know where engineering capacity is going, and show your board AI's real impact — in
            dollars, not vibes.
          </p>

          <div className="mt-9 grid grid-cols-1 gap-4 sm:grid-cols-2">
            {AI_ROI_CARDS.map((c) => (
              <div
                key={c.title}
                data-reveal
                className="rounded-2xl border border-white/70 bg-white/70 p-6 shadow-sm ring-1 ring-slate-900/[0.03] backdrop-blur-md transition duration-300 hover:-translate-y-1 hover:shadow-lg hover:shadow-kpmg-200/40"
              >
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-kpmg-500 to-cobalt-600 shadow-md shadow-kpmg-500/20">
                  <c.icon className="h-5 w-5 text-white" />
                </div>
                <p className="mt-4 font-semibold text-slate-900">{c.title}</p>
                <p className="mt-2 text-sm leading-relaxed text-slate-600">{c.body}</p>
              </div>
            ))}
          </div>

          <button
            data-reveal
            onClick={onLogin}
            className="group mt-9 inline-flex items-center gap-2 rounded-xl bg-[#00338D] px-7 py-3.5 text-sm font-semibold text-white shadow-lg shadow-kpmg-900/20 transition-all duration-300 hover:-translate-y-0.5 hover:bg-kpmg-700 hover:shadow-xl"
          >
            Log in to Cockpit
            <ArrowRight className="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1" />
          </button>
        </div>

        <div data-reveal className="relative">
          <div className="rounded-2xl border border-white/70 bg-white/85 p-6 shadow-xl shadow-slate-300/40 ring-1 ring-slate-900/[0.04] backdrop-blur-xl">
            <p className="text-center text-base font-bold text-slate-900">AI-assisted spend by team</p>
            <div className="mt-6 h-60">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={AI_SPEND_BY_TEAM} margin={{ top: 24 }}>
                  <XAxis dataKey="team" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                  <Tooltip formatter={(v: number) => [`$${v}K`, 'AI-assisted spend']} cursor={{ fill: '#eef2ff' }} />
                  <Bar dataKey="spend" radius={[10, 10, 0, 0]} fill="url(#roiBarGradient)" animationDuration={1100} />
                  <defs>
                    <linearGradient id="roiBarGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#478aff" />
                      <stop offset="100%" stopColor="#00338D" />
                    </linearGradient>
                  </defs>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div
            ref={floatRef}
            className="absolute -right-4 -top-10 w-56 rounded-2xl bg-gradient-to-br from-kpmg-600 to-cobalt-600 p-5 text-white shadow-2xl shadow-kpmg-500/40 sm:-right-10"
          >
            <div className="flex items-center gap-2">
              <TrendingUp className="h-4 w-4 text-kpmg-200" />
              <p className="text-xs font-medium text-kpmg-100">Total ROI on AI investments</p>
            </div>
            <div className="my-3 h-px bg-white/20" />
            <p className="text-3xl font-bold">$54.5K</p>
            <p className="mt-1 text-xs text-kpmg-100">For 500 developers</p>
          </div>
        </div>
      </div>
    </section>
  )
}

function FinalCta({ onLogin }: { onLogin: () => void }) {
  const ref = useRevealOnScroll<HTMLDivElement>({ stagger: 0.09 })

  return (
    <section className="relative overflow-hidden px-6 pb-24 pt-8">
      <div
        ref={ref}
        className="relative mx-auto max-w-5xl overflow-hidden rounded-3xl border border-kpmg-400/20 bg-gradient-to-br from-kpmg-600 via-cobalt-600 to-kpmg-700 px-6 py-16 text-center shadow-2xl shadow-kpmg-500/30"
      >
        <div className="pointer-events-none absolute -left-16 -top-16 h-64 w-64 animate-blob rounded-full bg-white/15 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-20 -right-10 h-72 w-72 animate-blob rounded-full bg-sky-300/20 blur-3xl [animation-delay:6s]" />
        <div className="bg-grid pointer-events-none absolute inset-0 opacity-20" />

        <h2 data-reveal className="relative text-3xl font-bold tracking-tight text-white sm:text-4xl">
          See your team's delivery health in the next 5 minutes
        </h2>
        <p data-reveal className="relative mx-auto mt-4 max-w-xl text-kpmg-100">
          Connect your tools, keep your workflow, and get an executive-ready view of delivery and AI
          ROI before your next standup.
        </p>
        <button
          data-reveal
          onClick={onLogin}
          className="group relative mt-8 inline-flex items-center gap-2 rounded-xl bg-white px-8 py-3.5 text-sm font-semibold text-kpmg-700 shadow-lg shadow-kpmg-900/20 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-xl"
        >
          Log in
          <ArrowRight className="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1" />
        </button>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------ */
/* Page                                                                */
/* ------------------------------------------------------------------ */

export default function Landing({ onLogin }: { onLogin: () => void }) {
  useSmoothScroll()
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <div className="relative min-h-screen text-slate-900">
      {/* Rendered outside the overflow-hidden wrapper below — `overflow` on an ancestor
          clips `position: fixed` descendants in most browsers, which would hide this
          layer entirely. */}
      <GradientMeshBackground className="fixed inset-0 -z-10" />

      <div className="relative overflow-x-clip">
        <header
          className={`sticky top-0 z-30 bg-[#00338D] transition-shadow duration-300 ${
            scrolled ? 'shadow-md shadow-kpmg-900/20' : ''
          }`}
        >
          <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-6">
            <a href="#" className="flex items-center gap-3">
              <span className="flex h-8 shrink-0 items-center justify-center rounded bg-white px-2.5 text-xs font-extrabold tracking-tight text-[#00338D]">
                KPMG
              </span>
              <div className="hidden h-6 w-px bg-white/25 sm:block" />
              <div className="hidden sm:block">
                <div className="text-sm font-semibold leading-tight text-white">AI Impact Evaluation</div>
                <div className="text-[10px] text-blue-100">Engineering Intelligence &amp; Analytics Platform</div>
              </div>
              <span className="text-[15px] font-semibold tracking-tight text-white sm:hidden">
                AI Impact Evaluation
              </span>
            </a>
            <nav className="flex items-center gap-1 sm:gap-2">
              {[
                { href: '#features', label: 'Features' },
                { href: '#how', label: 'How it works' },
                { href: '#ai-roi', label: 'AI ROI' },
              ].map((l) => (
                <a
                  key={l.href}
                  href={l.href}
                  className="hidden rounded-lg px-3 py-2 text-sm font-medium text-blue-100 transition hover:bg-white/10 hover:text-white sm:block"
                >
                  {l.label}
                </a>
              ))}
              <button
                onClick={onLogin}
                className="ml-2 rounded-lg bg-white px-5 py-2.5 text-sm font-semibold text-kpmg-700 shadow-md shadow-kpmg-900/20 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lg active:translate-y-0"
              >
                Log in
              </button>
            </nav>
          </div>
        </header>

        <Hero onLogin={onLogin} />

        {/* integrations marquee */}
        <section className="pb-16">
          <p className="text-center text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-400">
            Ingests data from the tools you already run
          </p>
          <div className="relative mt-6 overflow-hidden [mask-image:linear-gradient(to_right,transparent,black_12%,black_88%,transparent)]">
            <div className="flex w-max animate-marquee gap-12 whitespace-nowrap py-2">
              {[...INTEGRATIONS, ...INTEGRATIONS].map((name, i) => (
                <span
                  key={`${name}-${i}`}
                  className="text-sm font-semibold text-slate-400 transition-colors hover:text-kpmg-500"
                >
                  {name}
                </span>
              ))}
            </div>
          </div>
        </section>

        <MetricsBand />
        <Features />
        <HowItWorks />
        <AiRoi onLogin={onLogin} />
        <FinalCta onLogin={onLogin} />

        <footer className="border-t border-slate-200/60 px-6 py-10">
          <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 sm:flex-row">
            <div className="flex items-center gap-2.5">
              <span className="flex h-6 shrink-0 items-center justify-center rounded bg-[#00338D] px-2 text-[10px] font-extrabold tracking-tight text-white">
                KPMG
              </span>
              <img src={aiImpactEvaluationLogo} alt="" className="h-7 w-7 rounded-lg object-cover ring-1 ring-slate-900/5" />
              <span className="text-sm font-semibold tracking-tight text-slate-700">AI Impact Evaluation</span>
            </div>
            <p className="text-xs text-slate-400">Engineering Intelligence &amp; Analytics Platform</p>
          </div>
        </footer>
      </div>
    </div>
  )
}

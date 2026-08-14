// Subtle, sitewide ambient background for the marketing landing page: a faint
// "neural network" of pulsing nodes and flowing connections (evokes the AI/ML
// theme) layered over soft, slow-drifting color blobs. Fixed + pointer-events-none
// so it never interferes with content or interaction; kept at low opacity throughout
// so it reads as texture, not decoration competing with the copy.

const NODES: [number, number][] = [
  [120, 140], [320, 80], [560, 180], [780, 110], [980, 200], [1200, 140], [1340, 260],
  [80, 380], [300, 420], [520, 360], [760, 440], [960, 380], [1180, 420], [1360, 500],
  [160, 620], [420, 680], [660, 600], [900, 660], [1140, 620], [1300, 720],
]

const EDGES: [number, number][] = [
  [0, 1], [1, 2], [2, 3], [3, 4], [4, 5], [5, 6],
  [7, 8], [8, 9], [9, 10], [10, 11], [11, 12], [12, 13],
  [14, 15], [15, 16], [16, 17], [17, 18], [18, 19],
  [0, 7], [2, 9], [4, 11], [6, 13], [8, 15], [10, 17], [12, 19],
]

export default function AiNetworkBackground({ className = '' }: { className?: string }) {
  return (
    <div className={`pointer-events-none overflow-hidden ${className}`} aria-hidden="true">
      {/* ambient color wash */}
      <div className="absolute -left-32 top-0 h-[32rem] w-[32rem] animate-blob rounded-full bg-kpmg-200/25 blur-3xl" />
      <div className="absolute right-[-10rem] top-1/4 h-[36rem] w-[36rem] animate-blob rounded-full bg-cobalt-200/25 blur-3xl [animation-delay:5s]" />
      <div className="absolute left-1/4 bottom-0 h-[30rem] w-[30rem] animate-blob rounded-full bg-sky-200/20 blur-3xl [animation-delay:10s]" />

      {/* neural-network motif */}
      <svg
        className="absolute inset-0 h-full w-full opacity-[0.55] [mask-image:radial-gradient(ellipse_75%_75%_at_50%_20%,black,transparent)]"
        viewBox="0 0 1440 900"
        preserveAspectRatio="xMidYMid slice"
        fill="none"
      >
        <defs>
          <linearGradient id="ai-edge" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#478aff" />
            <stop offset="50%" stopColor="#00338D" />
            <stop offset="100%" stopColor="#0091DA" />
          </linearGradient>
        </defs>

        {EDGES.map(([a, b], i) => {
          const [x1, y1] = NODES[a]
          const [x2, y2] = NODES[b]
          return (
            <line
              key={`edge-${a}-${b}`}
              x1={x1}
              y1={y1}
              x2={x2}
              y2={y2}
              stroke="url(#ai-edge)"
              strokeWidth="1"
              strokeOpacity="0.35"
              strokeDasharray="6 10"
              className="animate-signal-flow"
              style={{ animationDelay: `${(i % 8) * 0.25}s` }}
            />
          )
        })}

        {NODES.map(([cx, cy], i) => (
          <circle
            key={`node-${i}`}
            cx={cx}
            cy={cy}
            r="3.5"
            fill={i % 3 === 0 ? '#478aff' : i % 3 === 1 ? '#00338D' : '#0091DA'}
            className="animate-node-pulse"
            style={{ animationDelay: `${(i % 10) * 0.35}s`, transformOrigin: `${cx}px ${cy}px` }}
          />
        ))}
      </svg>
    </div>
  )
}

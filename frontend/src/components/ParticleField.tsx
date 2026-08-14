import { useEffect, useRef } from 'react'
import * as THREE from 'three'

function makeDotTexture() {
  const size = 64
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!
  const gradient = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
  gradient.addColorStop(0, 'rgba(255,255,255,1)')
  gradient.addColorStop(0.4, 'rgba(255,255,255,0.6)')
  gradient.addColorStop(1, 'rgba(255,255,255,0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, size, size)
  const texture = new THREE.CanvasTexture(canvas)
  texture.needsUpdate = true
  return texture
}

export default function ParticleField({ className = '' }: { className?: string }) {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    let disposed = false
    let frameId = 0
    let cleanup = () => {}

    try {
      const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

      const scene = new THREE.Scene()
      scene.fog = new THREE.FogExp2(0xf8fafc, 0.05)

      const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 100)
      camera.position.set(0, 0, 14)

      const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' })
      if (!renderer.getContext()) {
        renderer.dispose()
        return
      }
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
      renderer.setClearColor(0x000000, 0)
      container.appendChild(renderer.domElement)

      const PARTICLE_COUNT = 700
      const positions = new Float32Array(PARTICLE_COUNT * 3)
      const colorPalette = [new THREE.Color('#4f46e5'), new THREE.Color('#7c3aed'), new THREE.Color('#0284c7')]
      const colors = new Float32Array(PARTICLE_COUNT * 3)

      for (let i = 0; i < PARTICLE_COUNT; i++) {
        positions[i * 3] = (Math.random() - 0.5) * 34
        positions[i * 3 + 1] = (Math.random() - 0.5) * 20
        positions[i * 3 + 2] = (Math.random() - 0.5) * 24

        const c = colorPalette[i % colorPalette.length]
        colors[i * 3] = c.r
        colors[i * 3 + 1] = c.g
        colors[i * 3 + 2] = c.b
      }

      const particleGeometry = new THREE.BufferGeometry()
      particleGeometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
      particleGeometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))

      const dotTexture = makeDotTexture()
      const particleMaterial = new THREE.PointsMaterial({
        size: 0.16,
        map: dotTexture,
        transparent: true,
        opacity: 0.55,
        vertexColors: true,
        depthWrite: false,
        blending: THREE.NormalBlending,
      })

      const particles = new THREE.Points(particleGeometry, particleMaterial)
      scene.add(particles)

      const shapesGroup = new THREE.Group()
      const shapeGeometries = [
        new THREE.IcosahedronGeometry(1.6, 0),
        new THREE.OctahedronGeometry(1.3, 0),
        new THREE.TorusGeometry(1.1, 0.32, 8, 24),
        new THREE.IcosahedronGeometry(1, 0),
      ]
      const shapeColors = ['#4f46e5', '#7c3aed', '#0284c7', '#8b5cf6']
      const shapes: THREE.Mesh[] = []

      shapeGeometries.forEach((geometry, i) => {
        const material = new THREE.MeshBasicMaterial({
          color: shapeColors[i % shapeColors.length],
          wireframe: true,
          transparent: true,
          opacity: 0.4,
        })
        const mesh = new THREE.Mesh(geometry, material)
        const angle = (i / shapeGeometries.length) * Math.PI * 2
        const radius = 6.5
        mesh.position.set(Math.cos(angle) * radius, Math.sin(angle * 1.3) * 3.5, Math.sin(angle) * radius - 4)
        shapesGroup.add(mesh)
        shapes.push(mesh)
      })
      scene.add(shapesGroup)

      const mouse = { x: 0, y: 0 }
      const targetRotation = { x: 0, y: 0 }

      function handlePointerMove(e: PointerEvent) {
        const rect = container!.getBoundingClientRect()
        mouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
        mouse.y = ((e.clientY - rect.top) / rect.height) * 2 - 1
      }
      window.addEventListener('pointermove', handlePointerMove)

      function resize() {
        const { clientWidth, clientHeight } = container!
        camera.aspect = clientWidth / Math.max(clientHeight, 1)
        camera.updateProjectionMatrix()
        renderer.setSize(clientWidth, clientHeight)
      }
      resize()
      const resizeObserver = new ResizeObserver(resize)
      resizeObserver.observe(container)

      const clock = new THREE.Clock()

      function renderFrame() {
        const elapsed = clock.getElapsedTime()

        particles.rotation.y = elapsed * 0.02
        particles.rotation.x = elapsed * 0.008

        shapes.forEach((mesh, i) => {
          mesh.rotation.x = elapsed * 0.12 + i
          mesh.rotation.y = elapsed * 0.16 + i
          mesh.position.y += Math.sin(elapsed * 0.5 + i * 2) * 0.0025
        })

        targetRotation.x += (mouse.y * 0.15 - targetRotation.x) * 0.03
        targetRotation.y += (mouse.x * 0.2 - targetRotation.y) * 0.03
        camera.position.x = targetRotation.y * 2
        camera.position.y = -targetRotation.x * 2
        camera.lookAt(0, 0, 0)

        renderer.render(scene, camera)
      }

      function animate() {
        renderFrame()
        frameId = requestAnimationFrame(animate)
      }

      if (prefersReducedMotion) {
        renderFrame()
      } else {
        animate()
      }

      cleanup = () => {
        if (frameId) cancelAnimationFrame(frameId)
        window.removeEventListener('pointermove', handlePointerMove)
        resizeObserver.disconnect()
        particleGeometry.dispose()
        particleMaterial.dispose()
        dotTexture.dispose()
        shapeGeometries.forEach((g) => g.dispose())
        shapes.forEach((m) => (m.material as THREE.Material).dispose())
        if (container.contains(renderer.domElement)) {
          container.removeChild(renderer.domElement)
        }
        renderer.dispose()
        renderer.forceContextLoss()
      }
    } catch {
      // WebGL unavailable or exhausted in this environment — render nothing, page still works.
    }

    return () => {
      if (disposed) return
      disposed = true
      cleanup()
    }
  }, [])

  return <div ref={containerRef} className={className} />
}

/**
 * Layer 1 — Algorithm exploration animation
 *
 * Drives a single requestAnimationFrame loop that reveals `visitedOrder` nodes
 * for all selected algorithms in parallel. Each algorithm uses a distinct color
 * with a 2-pixel offset to prevent perfect overlap.
 *
 * Bi-BFS uses two shades of green to visualise the two search frontiers.
 */
import { useEffect, useRef, useImperativeHandle, forwardRef } from 'react'
import type { AlgorithmResult } from '../../types'
import type { CoordinateTransformer } from '../../utils/CoordinateTransformer'
import {
  ALGORITHM_STYLES,
  VISITED_DOT_RADIUS,
  ANIMATION_SPEED,
} from '../../utils/CoordinateTransformer'

export interface AnimationLayerHandle {
  /** Reset animation to frame 0 and clear canvas */
  reset: () => void
}

interface Props {
  width: number
  height: number
  transformer: CoordinateTransformer
  results: AlgorithmResult[]
  /** Called with the current frame so ComparePanel can show live counts */
  onFrameUpdate?: (frame: number) => void
  /** Called when all algorithms have been fully drawn */
  onAnimationEnd?: () => void
}

export const AnimationLayer = forwardRef<AnimationLayerHandle, Props>(
  function AnimationLayer(
    { width, height, transformer, results, onFrameUpdate, onAnimationEnd },
    ref,
  ) {
    const canvasRef = useRef<HTMLCanvasElement>(null)
    const rafRef = useRef<number>(0)
    const frameRef = useRef(0)

    // Expose reset() to parent
    useImperativeHandle(ref, () => ({
      reset() {
        cancelAnimationFrame(rafRef.current)
        frameRef.current = 0
        const ctx = canvasRef.current?.getContext('2d')
        if (ctx) ctx.clearRect(0, 0, width, height)
      },
    }))

    useEffect(() => {
      cancelAnimationFrame(rafRef.current)
      frameRef.current = 0

      const canvas = canvasRef.current
      if (!canvas || !results.length) return
      const ctx = canvas.getContext('2d')!
      ctx.clearRect(0, 0, width, height)

      const maxFrames = Math.max(
        ...results.map(r => Math.ceil(r.visitedOrder.length / ANIMATION_SPEED)),
      )

      function tick() {
        const frame = frameRef.current
        if (frame > maxFrames) {
          onAnimationEnd?.()
          return
        }

        for (const result of results) {
          const style = ALGORITHM_STYLES[result.algorithm] ?? ALGORITHM_STYLES['dijkstra']
          const start = frame * ANIMATION_SPEED
          const end = Math.min(start + ANIMATION_SPEED, result.visitedOrder.length)

          // For Bi-BFS, colour the second half of visitedOrder with a darker shade
          for (let i = start; i < end; i++) {
            const node = result.visitedOrder[i]
            const { x, y } = transformer.latLonToXY(node.lat, node.lon)

            let color = style.visitedColor
            if (result.algorithm === 'bibfs') {
              // First half = forward (light green), second half = backward (dark green)
              const midpoint = result.visitedOrder.length / 2
              color = i < midpoint
                ? 'rgba(134,239,172,0.60)'   // light green — forward frontier
                : 'rgba(21,128,61,0.70)'     // dark green — backward frontier
            }

            ctx.beginPath()
            ctx.arc(
              x + style.offsetX,
              y + style.offsetY,
              VISITED_DOT_RADIUS,
              0,
              Math.PI * 2,
            )
            ctx.fillStyle = color
            ctx.fill()
          }
        }

        onFrameUpdate?.(frame)
        frameRef.current += 1
        rafRef.current = requestAnimationFrame(tick)
      }

      rafRef.current = requestAnimationFrame(tick)
      return () => cancelAnimationFrame(rafRef.current)
    }, [results, width, height, transformer, onFrameUpdate, onAnimationEnd])

    return (
      <canvas
        ref={canvasRef}
        width={width}
        height={height}
        style={{ position: 'absolute', top: 0, left: 0 }}
      />
    )
  },
)

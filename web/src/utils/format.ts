/**
 * EN: Formats a duration in seconds into a compact, human-friendly label for route summaries.
 * It intentionally stays simple for UI display and does not handle localization yet.
 * 中文：将秒数格式化为适合路线摘要显示的简洁可读时间文本。
 * 这里有意保持简单，暂不处理本地化问题。
 */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(seconds)) {
    return 'Unavailable'
  }

  const totalSeconds = Math.max(0, Math.round(seconds))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const remainingSeconds = totalSeconds % 60

  if (totalSeconds < 60) {
    return `${totalSeconds} sec`
  }

  if (totalSeconds < 3600) {
    if (remainingSeconds === 0) {
      return `${minutes} min`
    }
    return `${minutes} min ${remainingSeconds} sec`
  }

  if (minutes === 0) {
    return `${hours} h`
  }

  return `${hours} h ${minutes} min`
}

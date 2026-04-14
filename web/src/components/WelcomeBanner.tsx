import { useState } from 'react'

export function WelcomeBanner() {
  const [dismissed, setDismissed] = useState(false)

  if (dismissed) return null

  return (
    <div className="welcome-banner">
      <div className="welcome-banner__content">
        <div className="welcome-banner__title">Welcome to NEU Pathfinder</div>
        <div className="welcome-banner__text">
          A campus companion for Northeastern Arlington students. Find nearby food, plan safe walking routes,
          discover essential services, and share tips with fellow students.
        </div>
      </div>
      <button type="button" className="welcome-banner__close" onClick={() => setDismissed(true)}>
        &times;
      </button>
    </div>
  )
}

import type { PropsWithChildren } from 'react'

export function Sidebar({ children }: PropsWithChildren) {
  return <aside className="sidebar">{children}</aside>
}

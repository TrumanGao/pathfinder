import type { ReactNode } from 'react'

export type SidebarTab = 'explore' | 'search' | 'route' | 'community'

interface TabDef {
  id: SidebarTab
  label: string
  icon: string
}

const TABS: TabDef[] = [
  { id: 'explore', label: 'Explore', icon: '\uD83E\uDDED' },
  { id: 'search', label: 'Search', icon: '\uD83D\uDD0D' },
  { id: 'route', label: 'Route', icon: '\uD83D\uDDFA\uFE0F' },
  { id: 'community', label: 'Community', icon: '\uD83D\uDCAC' },
]

interface SidebarProps {
  activeTab: SidebarTab
  onTabChange: (tab: SidebarTab) => void
  panels: Record<SidebarTab, ReactNode>
  darkMode: boolean
  onToggleDarkMode: () => void
  footer?: ReactNode
}

export function Sidebar({ activeTab, onTabChange, panels, darkMode, onToggleDarkMode, footer }: SidebarProps) {
  return (
    <aside className="sidebar">
      <nav className="sidebar-tabs">
        {TABS.map(tab => (
          <button
            key={tab.id}
            type="button"
            className={`sidebar-tab ${activeTab === tab.id ? 'sidebar-tab--active' : ''}`}
            onClick={() => onTabChange(tab.id)}
          >
            <span className="sidebar-tab__icon">{tab.icon}</span>
            <span className="sidebar-tab__label">{tab.label}</span>
          </button>
        ))}
        <button type="button" className="sidebar-tab sidebar-theme-toggle" onClick={onToggleDarkMode}>
          <span className="sidebar-tab__icon">{darkMode ? '\u2600\uFE0F' : '\uD83C\uDF19'}</span>
          <span className="sidebar-tab__label">{darkMode ? 'Light' : 'Dark'}</span>
        </button>
      </nav>
      <div className="sidebar-content">
        {panels[activeTab]}
      </div>
      {footer}
    </aside>
  )
}

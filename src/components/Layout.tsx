import React from 'react';
import {
  LayoutDashboard, 
  FileUp, 
  Table, 
  BarChart3, 
  Settings, 
  LogOut,
  Bell,
  HelpCircle,
  Search,
  Sparkles
} from 'lucide-react';
import { AuthUser } from '../types';

interface LayoutProps {
  children: React.ReactNode;
  activeTab: string;
  onTabChange: (tab: string) => void;
  currentUser: AuthUser;
  onLogout: () => void;
}

export default function Layout({ children, activeTab, onTabChange, currentUser, onLogout }: LayoutProps) {
  const initials = currentUser.displayName.slice(0, 2).toUpperCase();

  return (
    <div className="flex flex-col min-h-screen bg-background text-on-background font-sans">
      {/* Top Navigation Bar */}
      <nav className="sticky top-0 z-50 flex items-center justify-between w-full px-6 h-16 bg-surface-container-lowest border-b border-outline-variant/15 shadow-none">
        <div className="flex items-center gap-8">
          <div className="flex items-center gap-2">
            <Table className="text-primary w-6 h-6" />
            <span className="text-xl font-bold tracking-tight text-on-surface">SmartDoc AI</span>
          </div>
          <div className="hidden md:flex items-center gap-6">
            <button 
              onClick={() => onTabChange('dashboard')}
              className={`px-3 py-2 rounded-lg transition-colors ${activeTab === 'dashboard' ? 'text-primary font-bold border-b-2 border-primary' : 'text-on-surface-variant hover:bg-surface-container-low'}`}
            >
              대시보드
            </button>
            <button 
              onClick={() => onTabChange('list')}
              className={`px-3 py-2 rounded-lg transition-colors ${activeTab === 'list' ? 'text-primary font-bold border-b-2 border-primary' : 'text-on-surface-variant hover:bg-surface-container-low'}`}
            >
              분석 목록
            </button>
            <button 
              onClick={() => onTabChange('rules')}
              className={`px-3 py-2 rounded-lg transition-colors ${activeTab === 'rules' ? 'text-primary font-bold border-b-2 border-primary' : 'text-on-surface-variant hover:bg-surface-container-low'}`}
            >
              알림 규칙
            </button>
          </div>
        </div>
        <div className="flex items-center gap-4">
          <div className="relative group">
            <Bell className="text-on-surface-variant p-1.5 cursor-pointer hover:bg-surface-container-low rounded-full transition-colors w-9 h-9" />
            <span className="absolute top-2 right-2 w-2 h-2 bg-error rounded-full border-2 border-surface-container-lowest"></span>
          </div>
          <HelpCircle className="text-on-surface-variant p-1.5 cursor-pointer hover:bg-surface-container-low rounded-full transition-colors w-9 h-9" />
          <Settings className="text-on-surface-variant p-1.5 cursor-pointer hover:bg-surface-container-low rounded-full transition-colors w-9 h-9" />
          <div className="hidden sm:block text-right">
            <p className="text-xs font-bold text-on-surface">{currentUser.displayName}</p>
            <p className="text-[11px] text-on-surface-variant">{currentUser.email}</p>
          </div>
          <div className="w-9 h-9 rounded-full border border-outline-variant/30 bg-primary/10 text-primary flex items-center justify-center text-xs font-black">
            {initials}
          </div>
        </div>
      </nav>

      <div className="flex flex-1">
        {/* Side Navigation Bar */}
        <aside className="fixed left-0 top-16 h-[calc(100vh-64px)] w-64 bg-surface-container flex flex-col p-4 z-40">
          <div className="flex flex-col gap-2 flex-grow">
            <SidebarItem 
              icon={<LayoutDashboard className="w-5 h-5" />} 
              label="대시보드" 
              active={activeTab === 'dashboard'} 
              onClick={() => onTabChange('dashboard')}
            />
            <SidebarItem 
              icon={<FileUp className="w-5 h-5" />} 
              label="문서 업로드" 
              onClick={() => onTabChange('list')}
            />
            <SidebarItem 
              icon={<Table className="w-5 h-5" />} 
              label="분석 목록" 
              active={activeTab === 'list' || activeTab === 'detail'} 
              onClick={() => onTabChange('list')}
            />
            <SidebarItem 
              icon={<BarChart3 className="w-5 h-5" />} 
              label="상세 인사이트" 
            />
          </div>
          <div className="mt-auto flex flex-col gap-2 pt-4 border-t border-outline-variant/15">
            <SidebarItem 
              icon={<Settings className="w-5 h-5" />} 
              label="알림 규칙"
              active={activeTab === 'rules'}
              onClick={() => onTabChange('rules')}
            />
            <SidebarItem 
              icon={<LogOut className="w-5 h-5 text-error" />} 
              label="로그아웃" 
              className="text-error/80 hover:bg-error-container/20"
              onClick={onLogout}
            />
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="ml-64 w-full min-h-[calc(100vh-64px)] p-8">
          {children}
        </main>
      </div>

      {/* Floating AI Chat Support */}
      <div className="fixed bottom-8 right-8 z-50">
        <button className="w-14 h-14 rounded-full bg-gradient-to-tr from-primary to-primary-container text-white shadow-xl shadow-primary/30 flex items-center justify-center hover:scale-110 active:scale-95 transition-all">
          <Sparkles className="w-8 h-8" />
        </button>
      </div>
    </div>
  );
}

interface SidebarItemProps {
  icon: React.ReactNode;
  label: string;
  active?: boolean;
  onClick?: () => void;
  className?: string;
}

function SidebarItem({ icon, label, active, onClick, className }: SidebarItemProps) {
  return (
    <button 
      onClick={onClick}
      className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 w-full text-left ${
        active 
          ? 'bg-surface-container-lowest text-primary font-semibold shadow-sm' 
          : 'text-on-surface-variant hover:bg-surface-container-highest hover:translate-x-1'
      } ${className || ''}`}
    >
      {icon}
      <span>{label}</span>
    </button>
  );
}

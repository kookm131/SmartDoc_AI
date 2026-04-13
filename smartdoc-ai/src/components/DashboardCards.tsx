import React from 'react';
import { FileText, BarChart3, AlertCircle } from 'lucide-react';
import { DashboardStats } from '../types';

interface DashboardCardsProps {
  stats: DashboardStats;
}

export default function DashboardCards({ stats }: DashboardCardsProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-2">
      <StatCard 
        icon={<FileText className="w-7 h-7" />}
        label="주간 분석량"
        value={`${stats.weeklyCount} 건`}
        change={`↑ ${stats.weeklyChange}% 지난주 대비`}
        type="primary"
      />
      <StatCard 
        icon={<BarChart3 className="w-7 h-7" />}
        label="평균 분석 신뢰도"
        value={`${stats.avgConfidence}%`}
        change="최적 상태"
        type="tertiary"
      />
      <StatCard 
        icon={<AlertCircle className="w-7 h-7" />}
        label="검토 요망 문서"
        value={`${stats.reviewNeeded} 건`}
        change="즉시 확인 필요"
        type="error"
      />
    </div>
  );
}

interface StatCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  change: string;
  type: 'primary' | 'tertiary' | 'error';
}

function StatCard({ icon, label, value, change, type }: StatCardProps) {
  const colors = {
    primary: 'bg-primary/10 text-primary',
    tertiary: 'bg-tertiary/10 text-tertiary',
    error: 'bg-error/10 text-error'
  };

  const textColors = {
    primary: 'text-primary',
    tertiary: 'text-tertiary',
    error: 'text-error'
  };

  return (
    <div className="bg-surface-container-lowest p-5 rounded-xl border border-outline-variant/10 shadow-sm flex items-center gap-4 hover:shadow-md transition-shadow">
      <div className={`w-12 h-12 rounded-full flex items-center justify-center ${colors[type]}`}>
        {icon}
      </div>
      <div>
        <p className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">{label}</p>
        <h3 className="text-2xl font-bold">{value}</h3>
        <p className={`text-[11px] font-bold mt-1 ${textColors[type]}`}>{change}</p>
      </div>
    </div>
  );
}

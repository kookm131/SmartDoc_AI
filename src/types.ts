export type DocumentStatus = '완료' | '검토 중' | '리스크' | '분석 중';

export interface Document {
  id: string;
  type: string;
  company: string;
  amount: string;
  keywords: string[];
  sentiment: {
    label: string;
    score: number;
    type: 'positive' | 'neutral' | 'negative';
  };
  status: DocumentStatus;
  date: string;
}

export interface DashboardStats {
  weeklyCount: number;
  weeklyChange: number;
  avgConfidence: number;
  reviewNeeded: number;
}

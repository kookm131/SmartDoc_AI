import React, { useState } from 'react';
import Layout from './components/Layout';
import DocumentList from './components/DocumentList';
import DocumentDetail from './components/DocumentDetail';
import DashboardCards from './components/DashboardCards';
import { Document, DashboardStats } from './types';
import { FileText } from 'lucide-react';

const MOCK_DOCUMENTS: Document[] = [
  {
    id: '1',
    type: '공급 계약서',
    company: '나래 테크놀로지',
    amount: '₩ 124,500,000',
    keywords: ['정기 유지보수', 'SaaS'],
    sentiment: { label: '긍정', score: 85, type: 'positive' },
    status: '완료',
    date: '2024_03_12'
  },
  {
    id: '2',
    type: '영업 비밀 유지',
    company: '글로벌 파트너스',
    amount: '-',
    keywords: ['NDA', '연구 개발'],
    sentiment: { label: '중립', score: 60, type: 'neutral' },
    status: '검토 중',
    date: '2024_03_11'
  },
  {
    id: '3',
    type: '매입 송장',
    company: '미래 물류 주식회사',
    amount: '₩ 8,920,000',
    keywords: ['운송비', '긴급'],
    sentiment: { label: '주의', score: 42, type: 'negative' },
    status: '리스크',
    date: '2024_03_12'
  },
  {
    id: '4',
    type: '공급 계약서',
    company: '서울 소프트웨어',
    amount: '₩ 45,000,000',
    keywords: ['AI 모델링', '아웃소싱'],
    sentiment: { label: '신뢰', score: 92, type: 'positive' },
    status: '완료',
    date: '2024_03_10'
  },
  {
    id: '5',
    type: '기타 증빙',
    company: '하나 데이터 센터',
    amount: '₩ 1,200,000',
    keywords: ['클라우드'],
    sentiment: { label: '긍정', score: 72, type: 'positive' },
    status: '완료',
    date: '2024_03_09'
  }
];

const MOCK_STATS: DashboardStats = {
  weeklyCount: 428,
  weeklyChange: 12,
  avgConfidence: 94.2,
  reviewNeeded: 7
};

export default function App() {
  const [activeTab, setActiveTab] = useState('list');
  const [selectedDoc, setSelectedDoc] = useState<Document | null>(null);

  const handleDocumentClick = (doc: Document) => {
    setSelectedDoc(doc);
    setActiveTab('detail');
  };

  const handleBack = () => {
    setSelectedDoc(null);
    setActiveTab('list');
  };

  return (
    <Layout activeTab={activeTab} onTabChange={setActiveTab}>
      {activeTab === 'dashboard' && (
        <div className="flex flex-col gap-8">
          <header>
            <h1 className="text-3xl font-bold tracking-tight text-on-surface mb-1">대시보드</h1>
            <p className="text-on-surface-variant text-sm">전체 분석 현황을 한눈에 확인하세요.</p>
          </header>
          <DashboardCards stats={MOCK_STATS} />
          
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-surface-container-lowest p-6 rounded-xl border border-outline-variant/10 shadow-sm">
              <h3 className="text-lg font-bold mb-4">최근 분석 문서</h3>
              <div className="flex flex-col gap-3">
                {MOCK_DOCUMENTS.slice(0, 3).map(doc => (
                  <div key={doc.id} className="flex items-center justify-between p-3 hover:bg-surface-container-low rounded-lg transition-colors cursor-pointer" onClick={() => handleDocumentClick(doc)}>
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-lg bg-primary/5 flex items-center justify-center text-primary">
                        <FileText className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-sm font-bold">{doc.company}</p>
                        <p className="text-xs text-on-surface-variant">{doc.type} • {doc.date}</p>
                      </div>
                    </div>
                    <span className={`px-2 py-0.5 text-[10px] font-bold rounded-full ${
                      doc.status === '완료' ? 'bg-primary/10 text-primary' : 'bg-tertiary/10 text-tertiary'
                    }`}>
                      {doc.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>
            
            <div className="bg-surface-container-lowest p-6 rounded-xl border border-outline-variant/10 shadow-sm">
              <h3 className="text-lg font-bold mb-4">AI 인사이트 요약</h3>
              <div className="space-y-4">
                <div className="p-4 bg-tertiary/5 rounded-lg border-l-4 border-tertiary">
                  <p className="text-sm font-semibold text-tertiary mb-1">분석 효율성 향상</p>
                  <p className="text-xs text-on-surface-variant">지난주 대비 문서 처리 속도가 15% 향상되었습니다. 현재 AI 모델의 신뢰도는 매우 안정적입니다.</p>
                </div>
                <div className="p-4 bg-error/5 rounded-lg border-l-4 border-error">
                  <p className="text-sm font-semibold text-error mb-1">주의 필요 조항 발견</p>
                  <p className="text-xs text-on-surface-variant">최근 3건의 계약서에서 표준 범위를 벗어나는 위약금 조항이 발견되었습니다. 상세 인사이트를 확인하세요.</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'list' && (
        <div className="flex flex-col gap-8">
          <DocumentList 
            documents={MOCK_DOCUMENTS} 
            onDocumentClick={handleDocumentClick} 
          />
          <DashboardCards stats={MOCK_STATS} />
        </div>
      )}

      {activeTab === 'detail' && selectedDoc && (
        <DocumentDetail 
          document={selectedDoc} 
          onBack={handleBack} 
        />
      )}
    </Layout>
  );
}

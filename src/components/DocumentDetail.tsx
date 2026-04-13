import React from 'react';
import { 
  ArrowLeft, 
  ZoomIn, 
  ZoomOut, 
  Download, 
  Bolt, 
  Share2, 
  FileText, 
  LayoutGrid, 
  BarChart3, 
  Lightbulb,
  MessageSquare
} from 'lucide-react';
import { Document } from '../types';
import { motion } from 'motion/react';

interface DocumentDetailProps {
  document: Document;
  onBack: () => void;
}

export default function DocumentDetail({ document, onBack }: DocumentDetailProps) {
  return (
    <motion.div 
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="fixed inset-0 z-[60] bg-background flex flex-col h-screen overflow-hidden"
    >
      {/* Top Header */}
      <header className="h-16 px-6 flex items-center justify-between bg-surface-container-lowest border-b border-outline-variant/15">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2">
            <FileText className="text-primary w-6 h-6" />
            <span className="text-xl font-bold tracking-tight text-on-surface">SmartDoc AI</span>
          </div>
          <nav className="hidden md:flex items-center space-x-1">
            <button className="px-3 py-2 text-on-surface-variant hover:bg-surface-container-low transition-colors rounded-lg">대시보드</button>
            <button className="px-3 py-2 text-primary font-bold border-b-2 border-primary">상세 인사이트</button>
            <button className="px-3 py-2 text-on-surface-variant hover:bg-surface-container-low transition-colors rounded-lg">문서 보관함</button>
          </nav>
        </div>
        <div className="flex items-center gap-4">
          <div className="w-8 h-8 rounded-full overflow-hidden border border-outline-variant/30">
            <img 
              alt="User" 
              src="https://picsum.photos/seed/user/100/100" 
              referrerPolicy="no-referrer"
              className="w-full h-full object-cover"
            />
          </div>
        </div>
      </header>

      <main className="flex-1 flex overflow-hidden">
        {/* Left Side: Document Viewer */}
        <section className="flex-1 bg-surface-container-low flex flex-col min-w-0">
          <div className="h-12 px-6 flex items-center justify-between bg-white/50 backdrop-blur-sm border-b border-outline-variant/10">
            <div className="flex items-center gap-3">
              <button 
                onClick={onBack}
                className="p-1.5 hover:bg-surface-container-high rounded-md transition-colors"
              >
                <ArrowLeft className="w-4 h-4" />
              </button>
              <span className="font-semibold text-on-surface">{document.company}_계약서_{document.date}.pdf</span>
              {document.status === '리스크' && (
                <span className="bg-error-container text-error px-2 py-0.5 rounded text-[11px] font-bold">긴급</span>
              )}
            </div>
            <div className="flex items-center gap-2">
              <button className="p-1.5 hover:bg-surface-container-high rounded-md transition-colors"><ZoomIn className="w-4 h-4" /></button>
              <button className="p-1.5 hover:bg-surface-container-high rounded-md transition-colors"><ZoomOut className="w-4 h-4" /></button>
              <div className="h-4 w-[1px] bg-outline-variant/30 mx-1"></div>
              <button className="p-1.5 hover:bg-surface-container-high rounded-md transition-colors"><Download className="w-4 h-4" /></button>
            </div>
          </div>
          <div className="flex-1 overflow-y-auto p-8 flex justify-center items-start custom-scrollbar">
            <div className="w-full max-w-3xl bg-white shadow-sm rounded-lg overflow-hidden relative">
              <img 
                alt="Document Preview" 
                className="w-full h-auto" 
                src="https://picsum.photos/seed/document/800/1200" 
                referrerPolicy="no-referrer"
              />
              {/* AI Highlight Overlays */}
              <div className="absolute top-[15%] left-[20%] w-[40%] h-[5%] bg-primary/10 border-l-2 border-primary"></div>
              <div className="absolute top-[45%] left-[15%] w-[30%] h-[3%] bg-tertiary/10 border-l-2 border-tertiary"></div>
            </div>
          </div>
        </section>

        {/* Right Side: AI Analysis Panel */}
        <aside className="w-[480px] bg-surface-container-lowest flex flex-col border-l border-outline-variant/10">
          <div className="p-6 pb-2">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold text-on-surface">AI 분석 결과</h2>
              <div className="flex items-center gap-1.5 text-xs text-on-tertiary-fixed-variant bg-tertiary-fixed/30 px-2 py-1 rounded-full">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-tertiary-fixed opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-tertiary-fixed"></span>
                </span>
                AI 신뢰도: 98.4%
              </div>
            </div>
            <div className="flex gap-2 mb-6">
              <button className="flex-1 flex items-center justify-center gap-2 bg-primary text-white py-2.5 rounded-xl text-sm font-medium hover:opacity-90 transition-all">
                <Bolt className="w-4 h-4" />
                AI 요약 생성
              </button>
              <button className="flex items-center justify-center gap-2 border border-outline-variant/30 text-on-surface-variant px-4 py-2.5 rounded-xl text-sm font-medium hover:bg-surface-container-low transition-all">
                <Share2 className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-6 pb-8 space-y-6 custom-scrollbar">
            {/* Section: Extracted Text */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <FileText className="text-primary w-5 h-5" />
                <h3 className="font-bold text-sm text-on-surface">추출된 텍스트</h3>
              </div>
              <div className="bg-surface-container-low p-4 rounded-xl text-sm leading-relaxed text-on-surface-variant border border-outline-variant/5">
                <p className="mb-2">제 12조 (비밀유지 의무): 을은 본 계약의 수행 과정에서 알게 된 갑의 영업 비밀 및 정보를 제 3자에게 누설하여서는 아니 된다.</p>
                <p>위반 시 을은 갑에게 <span className="bg-error-container/50 text-error font-semibold px-1">금 일억 원(₩100,000,000)</span>의 위약금을 지급하여야 하며...</p>
              </div>
            </div>

            {/* Section: Key Entities */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <LayoutGrid className="text-primary w-5 h-5" />
                <h3 className="font-bold text-sm text-on-surface">핵심 개체</h3>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <EntityCard label="계약 주체" value={document.company} />
                <EntityCard label="계약 금액" value={document.amount} />
                <EntityCard label="만료 일자" value="2025-03-12" />
                <EntityCard label="담당 부서" value="전략기획실" />
              </div>
            </div>

            {/* Section: Sentiment Analysis */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <BarChart3 className="text-primary w-5 h-5" />
                <h3 className="font-bold text-sm text-on-surface">리스크 및 감성 분석</h3>
              </div>
              <div className="bg-surface-container-low p-4 rounded-xl border border-outline-variant/5">
                <div className="flex items-center justify-between mb-4">
                  <span className="text-xs text-on-surface-variant">종합 리스크 스코어</span>
                  <span className="text-sm font-black text-error">78/100 (높음)</span>
                </div>
                <div className="flex items-end gap-2 h-24 mb-2 px-2">
                  <div className="flex-1 bg-primary/20 rounded-t-sm h-[40%] relative group"></div>
                  <div className="flex-1 bg-outline-variant/30 rounded-t-sm h-[60%] relative group"></div>
                  <div className="flex-1 bg-error rounded-t-sm h-[78%] relative group"></div>
                </div>
                <div className="flex justify-between text-[10px] text-on-surface-variant">
                  <span>긍정</span>
                  <span>중립</span>
                  <span>리스크</span>
                </div>
              </div>
            </div>

            {/* Section: Insight Cards */}
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Lightbulb className="text-primary w-5 h-5" />
                <h3 className="font-bold text-sm text-on-surface">AI 추천 인사이트</h3>
              </div>
              <div className="space-y-2">
                <InsightCard 
                  title="조항 수정 권고" 
                  content="위약금 산정 기준이 시장 평균 대비 15% 높게 설정되어 있습니다. 법률 검토가 필요합니다."
                  type="primary"
                />
                <InsightCard 
                  title="유관 문서 연계" 
                  content="2023년 체결된 '업무 협약서'의 내용과 충돌하는 지점이 2곳 발견되었습니다."
                  type="tertiary"
                />
              </div>
            </div>
          </div>

          {/* Sticky Bottom Action */}
          <div className="p-6 border-t border-outline-variant/10 bg-white/80 backdrop-blur-md">
            <button className="w-full flex items-center justify-center gap-2 bg-[#4A154B] text-white py-3 rounded-xl text-sm font-bold shadow-lg hover:shadow-xl active:scale-[0.98] transition-all">
              <MessageSquare className="w-5 h-5" />
              Slack으로 알림 보내기
            </button>
            <p className="mt-2 text-[10px] text-center text-on-surface-variant">긴급 채널로 분석 리포트가 즉시 공유됩니다.</p>
          </div>
        </aside>
      </main>
    </motion.div>
  );
}

function EntityCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-surface-container p-3 rounded-lg flex flex-col">
      <span className="text-[11px] text-on-surface-variant mb-1">{label}</span>
      <span className="font-semibold text-sm">{value}</span>
    </div>
  );
}

function InsightCard({ title, content, type }: { title: string; content: string; type: 'primary' | 'tertiary' }) {
  return (
    <div className={`group p-3 border-l-4 rounded-r-lg hover:translate-x-1 transition-transform ${
      type === 'primary' ? 'bg-secondary-container/30 border-primary' : 'bg-tertiary/5 border-tertiary'
    }`}>
      <h4 className={`text-xs font-bold mb-1 ${type === 'primary' ? 'text-primary' : 'text-tertiary'}`}>{title}</h4>
      <p className="text-xs text-on-surface-variant">{content}</p>
    </div>
  );
}

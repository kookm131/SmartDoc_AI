import React from 'react';
import { Search, Filter, Download, Plus, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import { Document } from '../types';

interface DocumentListProps {
  documents: Document[];
  onDocumentClick: (doc: Document) => void;
}

export default function DocumentList({ documents, onDocumentClick }: DocumentListProps) {
  return (
    <div className="max-w-[1600px] mx-auto flex flex-col gap-6">
      {/* Header Section */}
      <header className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-on-surface mb-1">문서 분석 목록</h1>
          <p className="text-on-surface-variant text-sm">전체 엔터프라이즈 문서의 AI 분석 결과 및 데이터 현황을 확인하세요.</p>
        </div>
        <div className="flex gap-3">
          <button className="flex items-center gap-2 px-4 py-2 bg-secondary-container text-on-secondary-container rounded-xl font-semibold hover:brightness-95 transition-all">
            <Download className="w-5 h-5" />
            <span>CSV 내보내기</span>
          </button>
          <button className="flex items-center gap-2 px-6 py-2 bg-gradient-to-r from-primary to-primary-container text-white rounded-xl font-semibold shadow-lg shadow-primary/10 hover:brightness-110 active:scale-95 transition-all">
            <Plus className="w-5 h-5" />
            <span>새 문서 분석</span>
          </button>
        </div>
      </header>

      {/* Filter Bar */}
      <section className="bg-surface-container-lowest rounded-xl p-5 shadow-sm border border-outline-variant/10">
        <div className="grid grid-cols-1 md:grid-cols-4 lg:grid-cols-5 gap-6">
          <div className="md:col-span-2 relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant/60 w-5 h-5" />
            <input 
              type="text" 
              placeholder="업체명, 키워드 또는 문서 검색..."
              className="w-full pl-10 pr-4 py-2.5 bg-surface-container-highest/50 rounded-xl border-none focus:ring-2 focus:ring-primary/40 text-sm transition-all outline-none"
            />
          </div>
          <div className="relative group">
            <label className="absolute -top-2 left-3 px-1 bg-surface-container-lowest text-[10px] font-bold text-on-surface-variant uppercase tracking-wider">날짜 범위</label>
            <div className="flex items-center justify-between w-full px-3 py-2.5 bg-surface-container-highest/50 rounded-xl cursor-pointer hover:bg-surface-container-highest transition-colors">
              <span className="text-sm">최근 30일</span>
              <ChevronRight className="w-4 h-4 text-on-surface-variant/60 rotate-90" />
            </div>
          </div>
          <div className="relative group">
            <label className="absolute -top-2 left-3 px-1 bg-surface-container-lowest text-[10px] font-bold text-on-surface-variant uppercase tracking-wider">문서 유형</label>
            <div className="flex items-center justify-between w-full px-3 py-2.5 bg-surface-container-highest/50 rounded-xl cursor-pointer hover:bg-surface-container-highest transition-colors">
              <span className="text-sm">전체 유형</span>
              <Filter className="w-4 h-4 text-on-surface-variant/60" />
            </div>
          </div>
          <button className="flex items-center justify-center gap-2 px-4 py-2 bg-surface-container-low text-on-surface-variant rounded-xl border border-outline-variant/20 hover:bg-surface-container-high transition-colors">
            <Filter className="w-5 h-5" />
            <span className="font-medium text-sm">상세 필터</span>
          </button>
        </div>
      </section>

      {/* Data Grid */}
      <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/10 overflow-hidden flex flex-col">
        {/* Grid Header */}
        <div className="grid grid-cols-[60px_1.2fr_1.5fr_1.2fr_2fr_1.2fr_1fr] bg-surface-container-low/80 border-b border-outline-variant/20 px-4 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-widest">
          <div className="flex justify-center items-center">
            <input type="checkbox" className="w-4 h-4 rounded border-outline-variant text-primary focus:ring-primary/20" />
          </div>
          <div className="px-4">문서 유형</div>
          <div className="px-4 border-l border-outline-variant/10">업체명</div>
          <div className="px-4 border-l border-outline-variant/10">금액</div>
          <div className="px-4 border-l border-outline-variant/10">키워드</div>
          <div className="px-4 border-l border-outline-variant/10">감성 분석</div>
          <div className="px-4 border-l border-outline-variant/10 text-center">최종 확인</div>
        </div>

        {/* Grid Rows */}
        <div className="flex flex-col custom-scrollbar overflow-y-auto max-h-[600px]">
          {documents.map((doc) => (
            <div 
              key={doc.id}
              onClick={() => onDocumentClick(doc)}
              className="grid grid-cols-[60px_1.2fr_1.5fr_1.2fr_2fr_1.2fr_1fr] items-center px-4 py-4 hover:bg-surface-container-high/40 transition-colors group cursor-pointer"
            >
              <div className="flex justify-center items-center" onClick={(e) => e.stopPropagation()}>
                <input type="checkbox" className="w-4 h-4 rounded border-outline-variant text-primary focus:ring-primary/20" />
              </div>
              <div className="px-4">
                <span className={`px-3 py-1 text-[11px] font-bold rounded-full ${
                  doc.type === '공급 계약서' ? 'bg-secondary-container text-on-secondary-container' :
                  doc.type === '영업 비밀 유지' ? 'bg-surface-variant text-on-surface-variant' :
                  doc.type === '매입 송장' ? 'bg-error-container text-on-error-container' :
                  'bg-secondary-container text-on-secondary-container'
                }`}>
                  {doc.type}
                </span>
              </div>
              <div className="px-4 font-bold text-on-surface">{doc.company}</div>
              <div className="px-4 font-mono text-sm">{doc.amount}</div>
              <div className="px-4 flex flex-wrap gap-1">
                {doc.keywords.map((kw, i) => (
                  <span key={i} className="bg-surface-container-highest px-2 py-0.5 rounded text-[11px] text-on-surface-variant">
                    {kw}
                  </span>
                ))}
              </div>
              <div className="px-4 flex items-center gap-2">
                <div className="flex-1 h-1.5 bg-surface-container rounded-full overflow-hidden">
                  <div 
                    className={`h-full ${
                      doc.sentiment.type === 'positive' ? 'bg-gradient-to-r from-primary to-tertiary-container' :
                      doc.sentiment.type === 'negative' ? 'bg-error' :
                      'bg-on-surface-variant/40'
                    }`}
                    style={{ width: `${doc.sentiment.score}%` }}
                  ></div>
                </div>
                <span className={`text-[11px] font-bold ${
                  doc.sentiment.type === 'positive' ? 'text-primary' :
                  doc.sentiment.type === 'negative' ? 'text-error' :
                  'text-on-surface-variant'
                }`}>
                  {doc.sentiment.label} {doc.sentiment.score}%
                </span>
              </div>
              <div className="px-4 flex justify-center">
                <span className={`px-3 py-1 text-[11px] font-bold rounded-full ${
                  doc.status === '완료' ? 'bg-primary/10 text-primary' :
                  doc.status === '검토 중' ? 'bg-tertiary/10 text-tertiary' :
                  doc.status === '리스크' ? 'bg-error/10 text-error animate-pulse' :
                  'bg-surface-container text-on-surface-variant'
                }`}>
                  {doc.status}
                </span>
              </div>
            </div>
          ))}
        </div>

        {/* Pagination */}
        <div className="flex items-center justify-between px-6 py-3 border-t border-outline-variant/20 bg-surface-container-low/50">
          <div className="text-xs text-on-surface-variant">
            전체 <span className="font-bold">128</span>개 항목 중 <span className="font-bold">1-10</span> 표시
          </div>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-1">
              <button className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant/40"><ChevronsLeft className="w-5 h-5" /></button>
              <button className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant/40"><ChevronLeft className="w-5 h-5" /></button>
              <button className="w-8 h-8 flex items-center justify-center rounded-lg bg-primary text-white text-xs font-bold shadow-sm">1</button>
              <button className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-container-high text-xs font-bold">2</button>
              <button className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-container-high text-xs font-bold">3</button>
              <button className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant"><ChevronRight className="w-5 h-5" /></button>
              <button className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant"><ChevronsRight className="w-5 h-5" /></button>
            </div>
            <div className="flex items-center gap-2 border-l border-outline-variant/20 pl-4">
              <span className="text-xs text-on-surface-variant">페이지 당 줄 수:</span>
              <select className="text-xs bg-transparent border-none focus:ring-0 cursor-pointer font-bold">
                <option>10</option>
                <option>25</option>
                <option>50</option>
              </select>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

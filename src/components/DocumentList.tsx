import React, { useState } from 'react';
import {
  Search,
  Filter,
  Download,
  Plus,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Archive,
} from 'lucide-react';
import { ApiErrorResponse, DocumentCreateInput, DocumentRecord } from '../types';

interface DocumentListProps {
  documents: DocumentRecord[];
  loading: boolean;
  submitting: boolean;
  onDocumentClick: (doc: DocumentRecord) => void;
  onCreateDocument: (input: DocumentCreateInput) => Promise<void>;
  onArchiveDocument: (documentId: string) => Promise<void>;
  latestError: ApiErrorResponse | null;
}

export default function DocumentList({
  documents,
  loading,
  submitting,
  onDocumentClick,
  onCreateDocument,
  onArchiveDocument,
  latestError,
}: DocumentListProps) {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [filename, setFilename] = useState('');
  const [fileKey, setFileKey] = useState('');
  const [contentType, setContentType] = useState('application/pdf');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const trimmedFilename = filename.trim() || selectedFile?.name || '';
    const finalFileKey = fileKey.trim() || `uploads/${trimmedFilename}`;

    await onCreateDocument({
      filename: trimmedFilename,
      fileKey: selectedFile ? fileKey.trim() : finalFileKey,
      contentType: selectedFile ? selectedFile.type || contentType.trim() : contentType.trim(),
      file: selectedFile ?? undefined,
    });

    setFilename('');
    setFileKey('');
    setContentType('application/pdf');
    setSelectedFile(null);
    setShowCreateForm(false);
  };

  return (
    <div className="max-w-[1600px] mx-auto flex flex-col gap-6">
      <header className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-on-surface mb-1">문서 분석 목록</h1>
          <p className="text-on-surface-variant text-sm">
            전체 엔터프라이즈 문서의 AI 분석 결과 및 데이터 현황을 확인하세요.
          </p>
        </div>
        <div className="flex gap-3">
          <button className="flex items-center gap-2 px-4 py-2 bg-secondary-container text-on-secondary-container rounded-xl font-semibold hover:brightness-95 transition-all">
            <Download className="w-5 h-5" />
            <span>CSV 내보내기</span>
          </button>
          <button
            onClick={() => setShowCreateForm((prev) => !prev)}
            className="flex items-center gap-2 px-6 py-2 bg-gradient-to-r from-primary to-primary-container text-white rounded-xl font-semibold shadow-lg shadow-primary/10 hover:brightness-110 active:scale-95 transition-all"
          >
            <Plus className="w-5 h-5" />
            <span>새 문서 분석</span>
          </button>
        </div>
      </header>

      {showCreateForm && (
        <section className="bg-surface-container-lowest rounded-xl p-5 shadow-sm border border-outline-variant/10">
          <form className="grid grid-cols-1 md:grid-cols-5 gap-3" onSubmit={handleSubmit}>
            <input
              type="file"
              accept=".pdf,.txt,.bin,application/pdf,text/plain,application/octet-stream"
              onChange={(event) => {
                const file = event.target.files?.[0] ?? null;
                setSelectedFile(file);
                if (file) {
                  setFilename((prev) => prev || file.name);
                  setContentType(file.type || 'application/octet-stream');
                }
              }}
              className="px-3 py-2.5 bg-surface-container-highest/50 rounded-xl outline-none focus:ring-2 focus:ring-primary/40 text-sm"
            />
            <input
              required={!selectedFile}
              value={filename}
              onChange={(event) => setFilename(event.target.value)}
              placeholder="파일명 (예: invoice-2026-04.pdf)"
              className="px-3 py-2.5 bg-surface-container-highest/50 rounded-xl outline-none focus:ring-2 focus:ring-primary/40"
            />
            <input
              value={fileKey}
              onChange={(event) => setFileKey(event.target.value)}
              placeholder="파일 키 (비우면 uploads/파일명 자동생성)"
              className="px-3 py-2.5 bg-surface-container-highest/50 rounded-xl outline-none focus:ring-2 focus:ring-primary/40"
            />
            <input
              value={contentType}
              onChange={(event) => setContentType(event.target.value)}
              placeholder="contentType"
              className="px-3 py-2.5 bg-surface-container-highest/50 rounded-xl outline-none focus:ring-2 focus:ring-primary/40"
            />
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2.5 rounded-xl bg-primary text-white font-semibold disabled:opacity-50"
            >
              {submitting ? '등록 중...' : '등록 실행'}
            </button>
          </form>
        </section>
      )}

      {latestError && (
        <section className="bg-error/5 border border-error/20 rounded-xl p-4 text-sm">
          <p className="font-bold text-error">{latestError.code}</p>
          <p className="text-on-surface mt-1">{latestError.message}</p>
          <p className="text-on-surface-variant mt-1">traceId: {latestError.traceId}</p>
        </section>
      )}

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
            <label className="absolute -top-2 left-3 px-1 bg-surface-container-lowest text-[10px] font-bold text-on-surface-variant uppercase tracking-wider">
              날짜 범위
            </label>
            <div className="flex items-center justify-between w-full px-3 py-2.5 bg-surface-container-highest/50 rounded-xl cursor-pointer hover:bg-surface-container-highest transition-colors">
              <span className="text-sm">최근 30일</span>
              <ChevronRight className="w-4 h-4 text-on-surface-variant/60 rotate-90" />
            </div>
          </div>
          <div className="relative group">
            <label className="absolute -top-2 left-3 px-1 bg-surface-container-lowest text-[10px] font-bold text-on-surface-variant uppercase tracking-wider">
              문서 유형
            </label>
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

      <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/10 overflow-hidden flex flex-col">
        <div className="grid grid-cols-[60px_1.2fr_1.5fr_1.2fr_2fr_1.2fr_1fr] bg-surface-container-low/80 border-b border-outline-variant/20 px-4 py-4 text-xs font-bold text-on-surface-variant uppercase tracking-widest">
          <div className="flex justify-center items-center">
            <input type="checkbox" className="w-4 h-4 rounded border-outline-variant text-primary focus:ring-primary/20" />
          </div>
          <div className="px-4">문서명</div>
          <div className="px-4 border-l border-outline-variant/10">파일 키</div>
          <div className="px-4 border-l border-outline-variant/10">타입</div>
          <div className="px-4 border-l border-outline-variant/10">상태</div>
          <div className="px-4 border-l border-outline-variant/10">생성일</div>
          <div className="px-4 border-l border-outline-variant/10 text-center">액션</div>
        </div>

        <div className="flex flex-col custom-scrollbar overflow-y-auto max-h-[600px]">
          {loading && <div className="px-6 py-8 text-on-surface-variant">문서 목록을 불러오는 중...</div>}

          {!loading && documents.length === 0 && (
            <div className="px-6 py-8 text-on-surface-variant">등록된 문서가 없습니다. 새 문서를 등록해 주세요.</div>
          )}

          {!loading &&
            documents.map((doc) => (
              <div
                key={doc.documentId}
                onClick={() => onDocumentClick(doc)}
                className="grid grid-cols-[60px_1.2fr_1.5fr_1.2fr_2fr_1.2fr_1fr] items-center px-4 py-4 hover:bg-surface-container-high/40 transition-colors group cursor-pointer"
              >
                <div className="flex justify-center items-center" onClick={(event) => event.stopPropagation()}>
                  <input type="checkbox" className="w-4 h-4 rounded border-outline-variant text-primary focus:ring-primary/20" />
                </div>
                <div className="px-4 font-bold text-on-surface truncate">{doc.filename}</div>
                <div className="px-4 text-sm text-on-surface-variant truncate">{doc.fileKey}</div>
                <div className="px-4 text-sm">{doc.contentType || '-'}</div>
                <div className="px-4">
                  <span className="px-3 py-1 text-[11px] font-bold rounded-full bg-primary/10 text-primary">{doc.status}</span>
                </div>
                <div className="px-4 text-sm text-on-surface-variant">{new Date(doc.createdAt).toLocaleString('ko-KR')}</div>
                <div className="px-4 flex justify-center gap-2" onClick={(event) => event.stopPropagation()}>
                  <button
                    onClick={() => onDocumentClick(doc)}
                    className="px-3 py-1 text-xs rounded-lg border border-outline-variant/20"
                  >
                    상세 보기
                  </button>
                  <button
                    onClick={() => void onArchiveDocument(doc.documentId)}
                    className="inline-flex items-center gap-1 px-3 py-1 text-xs rounded-lg border border-outline-variant/20 text-on-surface-variant hover:bg-error/5 hover:text-error"
                  >
                    <Archive className="h-3.5 w-3.5" />
                    보관
                  </button>
                </div>
              </div>
            ))}
        </div>

        <div className="flex items-center justify-between px-6 py-3 border-t border-outline-variant/20 bg-surface-container-low/50">
          <div className="text-xs text-on-surface-variant">
            전체 <span className="font-bold">{documents.length}</span>개 항목
          </div>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-1">
              <button className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant/40">
                <ChevronsLeft className="w-5 h-5" />
              </button>
              <button className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant/40">
                <ChevronLeft className="w-5 h-5" />
              </button>
              <button className="w-8 h-8 flex items-center justify-center rounded-lg bg-primary text-white text-xs font-bold shadow-sm">1</button>
              <button className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant">
                <ChevronRight className="w-5 h-5" />
              </button>
              <button className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant">
                <ChevronsRight className="w-5 h-5" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

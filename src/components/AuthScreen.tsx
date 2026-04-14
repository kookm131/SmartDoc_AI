import React, { useState } from 'react';
import { FileText, LockKeyhole, Sparkles } from 'lucide-react';
import { ApiErrorResponse, LoginInput, SignupInput } from '../types';

interface AuthScreenProps {
  submitting: boolean;
  latestError: ApiErrorResponse | null;
  onLogin: (input: LoginInput) => Promise<void>;
  onSignup: (input: SignupInput) => Promise<void>;
}

export default function AuthScreen({ submitting, latestError, onLogin, onSignup }: AuthScreenProps) {
  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const [email, setEmail] = useState('test@smartdoc.local');
  const [password, setPassword] = useState('password');
  const [displayName, setDisplayName] = useState('SmartDoc Tester');

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (mode === 'login') {
      await onLogin({ email, password });
      return;
    }
    await onSignup({ email, password, displayName });
  };

  return (
    <main className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(43,95,117,0.22),_transparent_32rem),linear-gradient(135deg,_#f7f1e5_0%,_#edf5ef_48%,_#e8f0f4_100%)] text-on-background flex items-center justify-center px-6 py-10">
      <section className="w-full max-w-5xl grid grid-cols-1 lg:grid-cols-[1.1fr_0.9fr] bg-surface-container-lowest/90 backdrop-blur rounded-[2rem] overflow-hidden shadow-2xl border border-white/70">
        <div className="relative p-10 lg:p-14 bg-gradient-to-br from-[#11343f] via-[#24576a] to-[#7a5a2a] text-white overflow-hidden">
          <div className="absolute -right-20 -top-20 w-64 h-64 rounded-full bg-white/10 blur-2xl" />
          <div className="absolute right-10 bottom-10 w-36 h-36 rounded-full border border-white/20" />
          <div className="relative z-10 flex flex-col min-h-[34rem] justify-between">
            <div>
              <div className="inline-flex items-center gap-2 px-3 py-2 rounded-full bg-white/12 border border-white/15 mb-8">
                <Sparkles className="w-4 h-4" />
                <span className="text-xs font-bold tracking-[0.2em] uppercase">Local Auth v1</span>
              </div>
              <h1 className="text-4xl lg:text-5xl font-black tracking-tight leading-tight mb-5">
                SmartDoc AI 작업실에 들어가기
              </h1>
              <p className="text-white/78 text-base leading-7 max-w-xl">
                지금은 Gateway가 개발용 인증을 담당합니다. H2 in-memory라 재시작하면 사용자 DB는 초기화되지만, 기본 계정은 자동으로 다시 생성됩니다.
              </p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
              <div className="rounded-2xl bg-white/10 p-4 border border-white/10">
                <p className="font-bold mb-1">기본 계정</p>
                <p className="text-white/70 break-all">test@smartdoc.local</p>
              </div>
              <div className="rounded-2xl bg-white/10 p-4 border border-white/10">
                <p className="font-bold mb-1">비밀번호</p>
                <p className="text-white/70">password</p>
              </div>
              <div className="rounded-2xl bg-white/10 p-4 border border-white/10">
                <p className="font-bold mb-1">저장소</p>
                <p className="text-white/70">Gateway H2</p>
              </div>
            </div>
          </div>
        </div>

        <form onSubmit={(event) => void handleSubmit(event)} className="p-8 lg:p-12 flex flex-col justify-center gap-6">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-2xl bg-primary/10 text-primary flex items-center justify-center">
              <FileText className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-2xl font-black text-on-surface">{mode === 'login' ? '로그인' : '회원가입'}</h2>
              <p className="text-sm text-on-surface-variant">문서 분석 플로우를 계속 진행합니다.</p>
            </div>
          </div>

          <div className="flex p-1 rounded-full bg-surface-container-low border border-outline-variant/20">
            <button
              type="button"
              onClick={() => setMode('login')}
              className={`flex-1 rounded-full py-2 text-sm font-bold transition ${mode === 'login' ? 'bg-surface-container-lowest shadow text-primary' : 'text-on-surface-variant'}`}
            >
              로그인
            </button>
            <button
              type="button"
              onClick={() => setMode('signup')}
              className={`flex-1 rounded-full py-2 text-sm font-bold transition ${mode === 'signup' ? 'bg-surface-container-lowest shadow text-primary' : 'text-on-surface-variant'}`}
            >
              회원가입
            </button>
          </div>

          {mode === 'signup' && (
            <label className="flex flex-col gap-2 text-sm font-bold text-on-surface">
              표시 이름
              <input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                className="rounded-xl border border-outline-variant/30 bg-surface-container-lowest px-4 py-3 text-sm font-normal outline-none focus:ring-2 focus:ring-primary/30"
                placeholder="SmartDoc Tester"
              />
            </label>
          )}

          <label className="flex flex-col gap-2 text-sm font-bold text-on-surface">
            이메일
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="rounded-xl border border-outline-variant/30 bg-surface-container-lowest px-4 py-3 text-sm font-normal outline-none focus:ring-2 focus:ring-primary/30"
              placeholder="test@smartdoc.local"
              required
            />
          </label>

          <label className="flex flex-col gap-2 text-sm font-bold text-on-surface">
            비밀번호
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="rounded-xl border border-outline-variant/30 bg-surface-container-lowest px-4 py-3 text-sm font-normal outline-none focus:ring-2 focus:ring-primary/30"
              placeholder="8자 이상"
              required
            />
          </label>

          {latestError && (
            <div className="rounded-2xl border border-error/20 bg-error/5 p-4 text-sm">
              <p className="font-bold text-error">{latestError.code}</p>
              <p className="text-on-surface-variant mt-1">{latestError.message}</p>
              <p className="text-[11px] text-on-surface-variant mt-2">traceId: {latestError.traceId}</p>
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-primary text-white py-3 font-black shadow-lg shadow-primary/20 hover:brightness-105 disabled:opacity-60 disabled:cursor-not-allowed transition"
          >
            <LockKeyhole className="w-4 h-4" />
            {submitting ? '처리 중...' : mode === 'login' ? '로그인하기' : '가입하고 시작하기'}
          </button>
        </form>
      </section>
    </main>
  );
}

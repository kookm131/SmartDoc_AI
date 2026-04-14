import React, { useMemo, useState } from 'react';
import { BellRing, LoaderCircle, Plus, ShieldCheck } from 'lucide-react';
import { ApiErrorResponse, NotificationRuleCreateInput, NotificationRuleRecord } from '../types';

interface NotificationRulesPanelProps {
  rules: NotificationRuleRecord[];
  loading: boolean;
  submitting: boolean;
  latestError: ApiErrorResponse | null;
  onCreateRule: (input: NotificationRuleCreateInput) => Promise<void>;
}

export default function NotificationRulesPanel({
  rules,
  loading,
  submitting,
  latestError,
  onCreateRule,
}: NotificationRulesPanelProps) {
  const [keyword, setKeyword] = useState('');
  const [channel, setChannel] = useState('slack');
  const [enabled, setEnabled] = useState(true);
  const enabledCount = useMemo(() => rules.filter((rule) => rule.enabled).length, [rules]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    await onCreateRule({
      keyword: keyword.trim(),
      channel: channel.trim(),
      enabled,
    });
    setKeyword('');
    setChannel('slack');
    setEnabled(true);
  };

  const handleToggle = async (rule: NotificationRuleRecord) => {
    await onCreateRule({
      keyword: rule.keyword,
      channel: rule.channel,
      enabled: !rule.enabled,
    });
  };

  return (
    <div className="max-w-[1400px] mx-auto flex flex-col gap-6">
      <header className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#0f3d2e] via-[#255c43] to-[#c7a65b] p-8 text-white shadow-xl">
        <div className="absolute -right-16 -top-20 h-64 w-64 rounded-full bg-white/10 blur-2xl" />
        <div className="absolute bottom-0 right-16 h-32 w-32 rounded-full bg-[#e9d8a6]/20 blur-xl" />
        <div className="relative flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <div className="mb-3 inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-bold">
              <BellRing className="h-4 w-4" />
              규칙 기반 알림
            </div>
            <h1 className="text-3xl font-black tracking-tight">알림 규칙 관리</h1>
            <p className="mt-2 max-w-2xl text-sm text-white/75">
              분석 키워드와 채널을 연결해 자동 알림을 제어합니다. 같은 키워드/채널을 다시 등록하면 기존 규칙이 갱신됩니다.
            </p>
          </div>
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div className="rounded-2xl bg-white/15 p-4 backdrop-blur">
              <p className="text-white/65">전체 규칙</p>
              <p className="mt-1 text-2xl font-black">{rules.length}</p>
            </div>
            <div className="rounded-2xl bg-white/15 p-4 backdrop-blur">
              <p className="text-white/65">활성 규칙</p>
              <p className="mt-1 text-2xl font-black">{enabledCount}</p>
            </div>
          </div>
        </div>
      </header>

      {latestError && (
        <section className="rounded-xl border border-error/20 bg-error/5 p-4 text-sm">
          <p className="font-bold text-error">{latestError.code}</p>
          <p className="mt-1 text-on-surface">{latestError.message}</p>
          <p className="mt-1 text-on-surface-variant">traceId: {latestError.traceId}</p>
        </section>
      )}

      <section className="grid grid-cols-1 gap-5 lg:grid-cols-[420px_1fr]">
        <form onSubmit={handleSubmit} className="rounded-2xl border border-outline-variant/10 bg-surface-container-lowest p-5 shadow-sm">
          <div className="mb-5 flex items-center gap-2">
            <Plus className="h-5 w-5 text-primary" />
            <h2 className="text-lg font-bold">규칙 추가/갱신</h2>
          </div>
          <div className="space-y-3">
            <label className="block text-sm">
              <span className="mb-1 block text-xs font-bold text-on-surface-variant">키워드</span>
              <input
                required
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="예: 계약"
                className="w-full rounded-xl bg-surface-container-highest/50 px-3 py-2.5 outline-none focus:ring-2 focus:ring-primary/40"
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block text-xs font-bold text-on-surface-variant">채널</span>
              <input
                required
                value={channel}
                onChange={(event) => setChannel(event.target.value)}
                placeholder="slack"
                className="w-full rounded-xl bg-surface-container-highest/50 px-3 py-2.5 outline-none focus:ring-2 focus:ring-primary/40"
              />
            </label>
            <label className="flex items-center justify-between rounded-xl bg-surface-container-low p-3 text-sm">
              <span className="font-semibold">활성화</span>
              <input
                type="checkbox"
                checked={enabled}
                onChange={(event) => setEnabled(event.target.checked)}
                className="h-5 w-5 rounded border-outline-variant text-primary focus:ring-primary/20"
              />
            </label>
            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-primary px-4 py-3 text-sm font-bold text-white transition-all hover:brightness-110 disabled:opacity-50"
            >
              {submitting && <LoaderCircle className="h-4 w-4 animate-spin" />}
              규칙 저장
            </button>
          </div>
        </form>

        <section className="rounded-2xl border border-outline-variant/10 bg-surface-container-lowest p-5 shadow-sm">
          <div className="mb-5 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-primary" />
              <h2 className="text-lg font-bold">현재 규칙</h2>
            </div>
            {loading && <LoaderCircle className="h-5 w-5 animate-spin text-primary" />}
          </div>

          {!loading && rules.length === 0 && (
            <p className="rounded-xl bg-surface-container-low p-5 text-sm text-on-surface-variant">
              아직 등록된 규칙이 없습니다. 기본 로컬 규칙이 보이지 않으면 notification 서비스를 재시작해 주세요.
            </p>
          )}

          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {rules.map((rule) => (
              <article key={rule.ruleId} className="rounded-2xl border border-outline-variant/10 bg-surface-container-low p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant">keyword</p>
                    <h3 className="mt-1 text-xl font-black text-on-surface">{rule.keyword}</h3>
                  </div>
                  <span
                    className={`rounded-full px-2 py-1 text-[11px] font-black ${
                      rule.enabled ? 'bg-primary/10 text-primary' : 'bg-outline-variant/30 text-on-surface-variant'
                    }`}
                  >
                    {rule.enabled ? 'enabled' : 'disabled'}
                  </span>
                </div>
                <div className="mt-4 flex items-center justify-between rounded-xl bg-surface-container-lowest p-3 text-sm">
                  <span className="text-on-surface-variant">channel</span>
                  <span className="font-bold text-primary">{rule.channel}</span>
                </div>
                <p className="mt-3 text-[11px] text-on-surface-variant">
                  createdAt: {new Date(rule.createdAt).toLocaleString('ko-KR')}
                </p>
                <button
                  onClick={() => void handleToggle(rule)}
                  disabled={submitting}
                  className="mt-4 w-full rounded-xl border border-outline-variant/20 px-3 py-2 text-sm font-bold text-on-surface-variant transition-colors hover:bg-surface-container-high disabled:opacity-50"
                >
                  {rule.enabled ? '비활성화' : '활성화'}
                </button>
              </article>
            ))}
          </div>
        </section>
      </section>
    </div>
  );
}

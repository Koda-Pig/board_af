import { useEffect, useMemo, useState } from 'react'
import {
  ArrowLeft,
  BarChart3,
  Check,
  ChevronDown,
  CircleDot,
  Clock3,
  Grip,
  Info,
  MoreHorizontal,
  Mountain,
  Pencil,
  Play,
  Plus,
  RotateCcw,
  Search,
  Settings,
  SlidersHorizontal,
  Sparkles,
  Target,
  TimerReset,
  Trophy,
  UserRound,
  X,
} from 'lucide-react'

const BOARD_IMAGE_SIZE = { width: 960, height: 1280 }

const HOLDS = [
  ['h01', 154, 81], ['h02', 405, 77], ['h03', 598, 75], ['h04', 801, 74],
  ['h05', 114, 238], ['h06', 306, 209], ['h07', 649, 234], ['h08', 751, 233], ['h09', 845, 280],
  ['h10', 305, 277], ['h11', 404, 278], ['h12', 504, 272], ['h13', 697, 355],
  ['h14', 254, 396], ['h15', 595, 394], ['h16', 406, 435], ['h17', 697, 433],
  ['h18', 157, 477], ['h19', 356, 514], ['h20', 502, 479], ['h21', 644, 511], ['h22', 846, 507],
  ['h23', 456, 550], ['h24', 754, 588], ['h25', 308, 592],
  ['h26', 116, 678], ['h27', 502, 670], ['h28', 843, 705],
  ['h29', 306, 744], ['h30', 457, 785], ['h31', 648, 785],
  ['h32', 155, 866], ['h33', 457, 902], ['h34', 600, 944], ['h35', 800, 941], ['h36', 304, 983],
  ['h37', 257, 1107], ['h38', 442, 1108], ['h39', 708, 1109],
  ['h40', 157, 1204], ['h41', 300, 1205], ['h42', 522, 1208], ['h43', 807, 1208],
].map(([id, sourceX, sourceY]) => ({
  id,
  x: (sourceX / BOARD_IMAGE_SIZE.width) * 100,
  y: (sourceY / BOARD_IMAGE_SIZE.height) * 100,
}))

const INITIAL_PROBLEMS = [
  {
    id: 'tidepool', name: 'Tidepool', grade: 'V4', color: '#74c9df', accent: 'Sky', author: 'You', sends: 7,
    note: 'Stay square through the middle, then commit to the blue finish.',
    holds: [
      { id: 'h43', role: 'start' }, { id: 'h34', role: 'foot' }, { id: 'h31', role: 'hand' },
      { id: 'h21', role: 'hand' }, { id: 'h17', role: 'foot' }, { id: 'h13', role: 'finish' },
    ],
  },
  {
    id: 'moss-line', name: 'Moss line', grade: 'V2', color: '#97c27f', accent: 'Moss', author: 'You', sends: 12,
    note: 'A relaxed green warm-up with a long final reach.',
    holds: [
      { id: 'h37', role: 'start' }, { id: 'h27', role: 'hand' }, { id: 'h20', role: 'foot' },
      { id: 'h16', role: 'hand' }, { id: 'h06', role: 'finish' },
    ],
  },
  {
    id: 'chalk-ghost', name: 'Chalk ghost', grade: 'V6', color: '#e78b68', accent: 'Coral', author: 'Maya', sends: 2,
    note: 'Compression on the left panel. The h14 catch is the whole game.',
    holds: [
      { id: 'h40', role: 'start' }, { id: 'h29', role: 'hand' }, { id: 'h25', role: 'foot' },
      { id: 'h19', role: 'hand' }, { id: 'h14', role: 'hand' }, { id: 'h05', role: 'hand' }, { id: 'h01', role: 'finish' },
    ],
  },
  {
    id: 'golden-hour', name: 'Golden hour', grade: 'V3', color: '#e4b866', accent: 'Ochre', author: 'Jono', sends: 9,
    note: 'Use the timber rail as a sidepull and keep your hips in.',
    holds: [
      { id: 'h34', role: 'start' }, { id: 'h35', role: 'foot' }, { id: 'h24', role: 'hand' },
      { id: 'h23', role: 'hand' }, { id: 'h10', role: 'hand' }, { id: 'h04', role: 'finish' },
    ],
  },
]

const ROLE_META = {
  start: { label: 'Start', color: '#ea7d5f' },
  hand: { label: 'Hand', color: '#70c5dd' },
  foot: { label: 'Foot', color: '#d9d7cf' },
  finish: { label: 'Finish', color: '#e9bd62' },
}

const GRADE_ORDER = ['V0', 'V1', 'V2', 'V3', 'V4', 'V5', 'V6', 'V7', 'V8']

function IconButton({ label, children, className = '', ...props }) {
  return <button className={`icon-button ${className}`} aria-label={label} title={label} {...props}>{children}</button>
}

function Logo() {
  return (
    <div className="brand-mark" aria-label="Board AF home">
      <div className="logo-glyph"><Mountain size={19} strokeWidth={2.7} /></div>
      <div><strong>BOARD</strong><span>AF</span></div>
    </div>
  )
}

function Sidebar({ view, setView, startSetter }) {
  const items = [
    ['problems', <CircleDot size={19} />, 'Problems'],
    ['sessions', <BarChart3 size={19} />, 'Sessions'],
    ['settings', <Settings size={19} />, 'Board setup'],
  ]
  return (
    <aside className="sidebar">
      <Logo />
      <nav className="main-nav" aria-label="Main navigation">
        {items.map(([id, icon, label]) => (
          <button key={id} className={view === id ? 'active' : ''} onClick={() => setView(id)}>
            {icon}<span>{label}</span>
          </button>
        ))}
      </nav>
      <button className="new-problem-button" onClick={startSetter}><Plus size={17} /> New problem</button>
      <div className="sidebar-footer">
        <div className="avatar">JK</div>
        <div><strong>Josh’s board</strong><span>Home wall</span></div>
        <MoreHorizontal size={18} />
      </div>
    </aside>
  )
}

function AppHeader({ view, setView }) {
  return (
    <header className="app-header">
      <div className="mobile-brand"><Logo /></div>
      <button className="board-switcher">
        <span className="board-dot" /> Home board <ChevronDown size={15} />
      </button>
      <div className="board-spec"><span>20°</span><span>4.8 m</span><span>43 holds</span></div>
      <div className="header-actions">
        <IconButton label="Search"><Search size={18} /></IconButton>
        <button className="profile-button"><UserRound size={17} /><span>Josh</span></button>
      </div>
      <nav className="mobile-nav" aria-label="Mobile navigation">
        <button className={view === 'problems' ? 'active' : ''} onClick={() => setView('problems')}><CircleDot size={18} />Problems</button>
        <button className={view === 'sessions' ? 'active' : ''} onClick={() => setView('sessions')}><BarChart3 size={18} />Sessions</button>
        <button className={view === 'settings' ? 'active' : ''} onClick={() => setView('settings')}><Settings size={18} />Setup</button>
      </nav>
    </header>
  )
}

function BoardPhoto({ problem, isSetting, draftHolds, role, onHoldClick, compact = false }) {
  const activeHolds = isSetting ? draftHolds : (problem?.holds || [])
  const byId = useMemo(() => Object.fromEntries(activeHolds.map((hold) => [hold.id, hold])), [activeHolds])

  return (
    <div className={`board-photo-wrap ${isSetting ? 'is-setting' : ''} ${compact ? 'compact' : ''}`}>
      <img src="/home-board.png" alt="Josh's home climbing board" className="board-photo" />
      <div className="hold-layer" aria-label="Interactive climbing holds">
        {HOLDS.map((hold) => {
          const selected = byId[hold.id]
          const color = selected ? ROLE_META[selected.role].color : undefined
          return (
            <button
              key={hold.id}
              type="button"
              className={`hold-marker ${selected ? `selected ${selected.role}` : ''}`}
              style={{ left: `${hold.x}%`, top: `${hold.y}%`, '--hold-color': color }}
              onClick={() => onHoldClick?.(hold.id)}
              aria-label={`${hold.id}${selected ? `, ${ROLE_META[selected.role].label}` : ''}`}
              title={isSetting ? `${selected ? 'Change or remove' : 'Add'} ${hold.id}` : undefined}
            >
              {selected && selected.role === 'start' && <span className="role-letter">S</span>}
              {selected && selected.role === 'finish' && <Check size={14} strokeWidth={3} />}
            </button>
          )
        })}
      </div>
      {isSetting && (
        <div className="setting-tip"><Pencil size={14} /> Tap a hold to mark it as {ROLE_META[role].label.toLowerCase()}</div>
      )}
    </div>
  )
}

function ProblemCard({ problem, active, onClick }) {
  return (
    <button className={`problem-card ${active ? 'active' : ''}`} onClick={onClick}>
      <span className="problem-swatch" style={{ background: problem.color }} />
      <span className="problem-copy">
        <span className="problem-name">{problem.name}</span>
        <span className="problem-meta">{problem.author} · {problem.holds.length} holds</span>
      </span>
      <span className="grade-pill">{problem.grade}</span>
    </button>
  )
}

function ProblemPanel({ problems, selectedId, onSelect, onStart, startSetter }) {
  const [query, setQuery] = useState('')
  const [grade, setGrade] = useState('All')
  const selected = problems.find((p) => p.id === selectedId) || problems[0]
  const visible = problems.filter((p) =>
    (grade === 'All' || p.grade === grade) && p.name.toLowerCase().includes(query.toLowerCase()),
  )

  return (
    <section className="panel problems-panel">
      <div className="panel-heading-row">
        <div><p className="eyebrow">Problem library</p><h2>{problems.length} problems</h2></div>
        <IconButton label="Create a new problem" onClick={startSetter}><Plus size={19} /></IconButton>
      </div>
      <div className="search-field"><Search size={16} /><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Find a problem" /></div>
      <div className="grade-filter" aria-label="Grade filter">
        {['All', 'V2', 'V3', 'V4', 'V6'].map((item) => <button key={item} className={grade === item ? 'active' : ''} onClick={() => setGrade(item)}>{item}</button>)}
      </div>
      <div className="problem-list">
        {visible.map((problem) => <ProblemCard key={problem.id} problem={problem} active={problem.id === selectedId} onClick={() => onSelect(problem.id)} />)}
        {visible.length === 0 && <div className="empty-state">No problems match that filter.</div>}
      </div>
      {selected && (
        <div className="selected-summary">
          <div className="selected-title"><span className="problem-swatch" style={{ background: selected.color }} /><div><strong>{selected.name}</strong><span>{selected.grade} · {selected.sends} sends</span></div></div>
          <p>{selected.note}</p>
          <div className="legend">
            {Object.entries(ROLE_META).map(([key, meta]) => <span key={key}><i style={{ background: meta.color }} />{meta.label}</span>)}
          </div>
          <button className="primary-button" onClick={() => onStart(selected)}><Play size={17} fill="currentColor" /> Start climb</button>
        </div>
      )}
    </section>
  )
}

function SetterPanel({ draft, setDraft, role, setRole, saveDraft, cancelSetter }) {
  const update = (field, value) => setDraft((old) => ({ ...old, [field]: value }))
  const canSave = draft.name.trim() && draft.holds.length >= 2 && draft.holds.some((hold) => hold.role === 'start') && draft.holds.some((hold) => hold.role === 'finish')
  return (
    <section className="panel setter-panel">
      <div className="panel-heading-row">
        <div><p className="eyebrow">Route setter</p><h2>New problem</h2></div>
        <IconButton label="Close route setter" onClick={cancelSetter}><X size={19} /></IconButton>
      </div>
      <label className="form-label">Problem name<input value={draft.name} onChange={(e) => update('name', e.target.value)} placeholder="e.g. Gecko disco" autoFocus /></label>
      <div className="two-field-row">
        <label className="form-label">Grade<select value={draft.grade} onChange={(e) => update('grade', e.target.value)}>{GRADE_ORDER.map((g) => <option key={g}>{g}</option>)}</select></label>
        <label className="form-label">Marker<select value={draft.accent} onChange={(e) => update('accent', e.target.value)}><option>Sky</option><option>Coral</option><option>Ochre</option><option>Moss</option></select></label>
      </div>
      <div className="role-picker-label"><span>Choose hold type</span><span>{draft.holds.length} selected</span></div>
      <div className="role-picker">
        {Object.entries(ROLE_META).map(([key, meta]) => (
          <button key={key} className={role === key ? 'active' : ''} style={{ '--role-color': meta.color }} onClick={() => setRole(key)}><i />{meta.label}</button>
        ))}
      </div>
      <div className="setter-instruction">
        <Target size={18} />
        <div><strong>Build on the photo</strong><span>Pick a hold type, then tap holds on the board. Tap an assigned hold again to remove it.</span></div>
      </div>
      <div className="hold-counts">
        {Object.entries(ROLE_META).map(([key, meta]) => <span key={key}><i style={{ background: meta.color }} />{draft.holds.filter((h) => h.role === key).length} {meta.label.toLowerCase()}</span>)}
      </div>
      <label className="form-label">Setter notes<textarea value={draft.note} onChange={(e) => update('note', e.target.value)} placeholder="Beta, rules, or a message for the next climber…" /></label>
      <div className="setter-actions">
        <button className="ghost-button" onClick={() => update('holds', [])}><RotateCcw size={16} /> Clear</button>
        <button className="primary-button" onClick={saveDraft} disabled={!canSave} title={canSave ? 'Save problem' : 'Add a name, start hold, and finish hold'}><Check size={17} /> Save problem</button>
      </div>
    </section>
  )
}

function SessionPanel({ sessions, problems, onStart, onSelect }) {
  const totalSends = sessions.filter((s) => s.sent).length
  return (
    <section className="panel sessions-panel">
      <div className="panel-heading-row"><div><p className="eyebrow">Training log</p><h2>Your sessions</h2></div><IconButton label="Session filters"><SlidersHorizontal size={18} /></IconButton></div>
      <div className="stats-grid">
        <div><Trophy size={19} /><strong>{totalSends}</strong><span>Sends</span></div>
        <div><TimerReset size={19} /><strong>{sessions.length}</strong><span>Attempts</span></div>
        <div><Sparkles size={19} /><strong>{sessions.length ? Math.round((totalSends / sessions.length) * 100) : 0}%</strong><span>Send rate</span></div>
      </div>
      <h3 className="section-label">Recent attempts</h3>
      <div className="session-list">
        {sessions.map((session) => {
          const problem = problems.find((p) => p.id === session.problemId)
          return <button key={session.id} onClick={() => { if (problem) onSelect(problem.id) }}><span className={`session-result ${session.sent ? 'sent' : ''}`}>{session.sent ? <Check size={15} /> : <X size={15} />}</span><span><strong>{problem?.name || 'Unknown problem'}</strong><small>{session.date} · {session.time}</small></span><b>{problem?.grade}</b></button>
        })}
        {sessions.length === 0 && <div className="empty-state roomy"><Clock3 size={24} /><strong>No attempts yet</strong><span>Start a climb and your activity will show up here.</span></div>}
      </div>
      <h3 className="section-label">Up next</h3>
      {problems.slice(0, 2).map((problem) => <div className="up-next" key={problem.id}><span className="problem-swatch" style={{ background: problem.color }} /><div><strong>{problem.name}</strong><span>{problem.grade} · {problem.holds.length} holds</span></div><IconButton label={`Climb ${problem.name}`} onClick={() => onStart(problem)}><Play size={16} /></IconButton></div>)}
    </section>
  )
}

function SetupPanel() {
  return (
    <section className="panel setup-panel">
      <div className="panel-heading-row"><div><p className="eyebrow">Board setup</p><h2>Home board</h2></div><IconButton label="More board settings"><MoreHorizontal size={19} /></IconButton></div>
      <div className="setup-photo"><img src="/home-board.png" alt="Home board thumbnail" /><span><Check size={14} /> Active board</span></div>
      <div className="setup-details">
        <div><span>Wall angle</span><strong>20°</strong></div>
        <div><span>Climbing height</span><strong>4.8 m</strong></div>
        <div><span>Mapped holds</span><strong>43</strong></div>
        <div><span>Last reset</span><strong>12 Jul</strong></div>
      </div>
      <div className="info-card"><Info size={18} /><div><strong>Your board is mapped</strong><span>Every hold in the photo has an interactive target. Replace or move holds at your next reset, then remap here.</span></div></div>
      <button className="outline-button"><Pencil size={16} /> Edit board details</button>
    </section>
  )
}

function ClimbBar({ problem, elapsed, onCancel, onComplete }) {
  const mins = String(Math.floor(elapsed / 60)).padStart(2, '0')
  const secs = String(elapsed % 60).padStart(2, '0')
  return (
    <div className="climb-bar" role="dialog" aria-label={`Climbing ${problem.name}`}>
      <button className="climb-close" onClick={onCancel}><ArrowLeft size={19} /> Leave</button>
      <div className="climb-live"><span className="live-pulse" /><div><small>Climbing now</small><strong>{problem.name} <i>{problem.grade}</i></strong></div></div>
      <div className="climb-timer">{mins}<span>:</span>{secs}</div>
      <div className="climb-actions"><button className="attempt-button" onClick={() => onComplete(false)}><X size={17} /> Attempt</button><button className="send-button" onClick={() => onComplete(true)}><Check size={17} /> Log send</button></div>
    </div>
  )
}

export default function App() {
  const [view, setView] = useState('problems')
  const [problems, setProblems] = useState(() => {
    try { return JSON.parse(localStorage.getItem('board-af-problems-v1')) || INITIAL_PROBLEMS } catch { return INITIAL_PROBLEMS }
  })
  const [sessions, setSessions] = useState(() => {
    try { return JSON.parse(localStorage.getItem('board-af-sessions-v1')) || [] } catch { return [] }
  })
  const [selectedId, setSelectedId] = useState(problems[0]?.id)
  const [isSetting, setIsSetting] = useState(false)
  const [role, setRole] = useState('hand')
  const [draft, setDraft] = useState({ name: '', grade: 'V3', accent: 'Sky', note: '', holds: [] })
  const [climbing, setClimbing] = useState(null)
  const [elapsed, setElapsed] = useState(0)

  const selected = problems.find((p) => p.id === selectedId) || problems[0]

  useEffect(() => { localStorage.setItem('board-af-problems-v1', JSON.stringify(problems)) }, [problems])
  useEffect(() => { localStorage.setItem('board-af-sessions-v1', JSON.stringify(sessions)) }, [sessions])
  useEffect(() => {
    if (!climbing) return undefined
    const id = window.setInterval(() => setElapsed((n) => n + 1), 1000)
    return () => window.clearInterval(id)
  }, [climbing])

  const startSetter = () => {
    setView('problems')
    setIsSetting(true)
    setDraft({ name: '', grade: 'V3', accent: 'Sky', note: '', holds: [] })
  }

  const handleHoldClick = (id) => {
    if (!isSetting) return
    setDraft((old) => {
      const exists = old.holds.find((h) => h.id === id)
      return { ...old, holds: exists ? old.holds.filter((h) => h.id !== id) : [...old.holds, { id, role }] }
    })
  }

  const saveDraft = () => {
    if (!draft.name.trim() || draft.holds.length < 2 || !draft.holds.some((hold) => hold.role === 'start') || !draft.holds.some((hold) => hold.role === 'finish')) return
    const colors = { Sky: '#74c9df', Coral: '#e78b68', Ochre: '#e4b866', Moss: '#97c27f' }
    const problem = { ...draft, id: `${draft.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-${Date.now()}`, color: colors[draft.accent], author: 'You', sends: 0 }
    setProblems((old) => [problem, ...old])
    setSelectedId(problem.id)
    setIsSetting(false)
  }

  const startClimb = (problem) => {
    setSelectedId(problem.id)
    setView('problems')
    setIsSetting(false)
    setElapsed(0)
    setClimbing(problem)
  }

  const completeClimb = (sent) => {
    if (!climbing) return
    const now = new Date()
    const session = { id: `${now.getTime()}`, problemId: climbing.id, sent, date: 'Today', time: `${Math.floor(elapsed / 60)}m ${elapsed % 60}s` }
    setSessions((old) => [session, ...old])
    if (sent) setProblems((old) => old.map((p) => p.id === climbing.id ? { ...p, sends: p.sends + 1 } : p))
    setClimbing(null)
  }

  const showBoard = view === 'problems'

  return (
    <div className="app-shell">
      <Sidebar view={view} setView={(next) => { setView(next); setIsSetting(false) }} startSetter={startSetter} />
      <main className="main-area">
        <AppHeader view={view} setView={(next) => { setView(next); setIsSetting(false) }} />
        <div className={`workspace ${showBoard ? '' : 'single-view'}`}>
          {showBoard && (
            <section className="board-stage">
              <div className="stage-toolbar">
                <div>
                  <p className="eyebrow">{isSetting ? 'New problem' : 'Now viewing'}</p>
                  <h1>{isSetting ? 'Choose your holds' : selected?.name}</h1>
                </div>
                {!isSetting && selected && <span className="large-grade">{selected.grade}</span>}
                {isSetting && <div className="active-tool"><i style={{ background: ROLE_META[role].color }} />{ROLE_META[role].label} holds</div>}
              </div>
              <div className="board-frame">
                <BoardPhoto problem={selected} isSetting={isSetting} draftHolds={draft.holds} role={role} onHoldClick={handleHoldClick} />
                {!isSetting && selected && <div className="route-caption"><span className="problem-swatch" style={{ background: selected.color }} /><div><strong>{selected.name}</strong><small>{selected.holds.length} holds · set by {selected.author}</small></div><span className="route-number">{selected.grade}</span></div>}
              </div>
              <div className="board-footnote"><Grip size={15} /><span>{isSetting ? 'All mapped holds are active. Tap to add or remove.' : 'Colored rings mark this problem. Start is coral, finish is gold.'}</span><button><Info size={15} /> Board rules</button></div>
            </section>
          )}
          <aside className="right-rail">
            {view === 'problems' && (isSetting
              ? <SetterPanel draft={draft} setDraft={setDraft} role={role} setRole={setRole} saveDraft={saveDraft} cancelSetter={() => setIsSetting(false)} />
              : <ProblemPanel problems={problems} selectedId={selectedId} onSelect={setSelectedId} onStart={startClimb} startSetter={startSetter} />
            )}
            {view === 'sessions' && <SessionPanel sessions={sessions} problems={problems} onStart={startClimb} onSelect={(id) => { setSelectedId(id); setView('problems') }} />}
            {view === 'settings' && <SetupPanel />}
          </aside>
        </div>
      </main>
      {climbing && <ClimbBar problem={climbing} elapsed={elapsed} onCancel={() => setClimbing(null)} onComplete={completeClimb} />}
    </div>
  )
}

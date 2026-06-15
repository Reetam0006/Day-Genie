/**
 * view.js — Presentation / DOM layer
 * Pure rendering functions; no fetch calls, no business logic.
 * Reads from the DOM and writes HTML — nothing else.
 */

const View = {

  /* ── Screen switching ───────────────────────────────────────────────────── */

  showAuthScreen() {
    document.getElementById('auth-screen').style.display = 'flex';
    document.getElementById('app-screen').style.display  = 'none';
  },

  showAppScreen(username) {
    document.getElementById('auth-screen').style.display = 'none';
    document.getElementById('app-screen').style.display  = 'block';
    document.getElementById('topbar-username').textContent  = username;
    document.getElementById('avatar-initials').textContent = username[0].toUpperCase();
  },

  showTab(t) {
    document.getElementById('tab-login').style.display    = t === 'login'    ? '' : 'none';
    document.getElementById('tab-register').style.display = t === 'register' ? '' : 'none';
    document.querySelectorAll('.tab-bar button').forEach((b, i) =>
      b.classList.toggle('active', (t === 'login' && i === 0) || (t === 'register' && i === 1))
    );
  },

  /* ── Error messages ─────────────────────────────────────────────────────── */

  setAuthError(msg)  { document.getElementById('auth-error').textContent = msg; },
  setRegError(msg)   { document.getElementById('reg-error').textContent  = msg; },
  clearAuthErrors()  {
    document.getElementById('auth-error').textContent = '';
    document.getElementById('reg-error').textContent  = '';
  },

  /* ── Task lists ─────────────────────────────────────────────────────────── */

  renderTodayList(tasks) {
    this._renderList('today-list', tasks, 'No tasks for today 🌤️');
  },

  renderAllList(tasks) {
    this._renderList('all-list', tasks, 'No upcoming tasks 📭');
  },

  _renderList(containerId, tasks, emptyMsg) {
    const el = document.getElementById(containerId);
    if (!tasks || tasks.length === 0) {
      el.innerHTML = `<div class="empty"><span>📭</span>${emptyMsg}</div>`;
      return;
    }
    el.innerHTML = tasks.map(t => this._taskCard(t)).join('');
  },

  _riskClass(level) {
    if (level === 'HIGH')   return 'risk-high';
    if (level === 'MEDIUM') return 'risk-medium';
    if (level === 'LOW')    return 'risk-low';
    return '';
  },

  _taskCard(t) {
    const dt      = new Date(t.scheduledTime);
    const timeStr = dt.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
    const recs    = (t.recommendations || '').split('\n').filter(Boolean).join('<br/>');
    const feasibleBadge = t.feasible === true  ? '✅ Feasible'
                        : t.feasible === false ? '❌ Risky'
                        : '';
    return `
    <div class="task-card ${this._riskClass(t.riskLevel)}" id="task-${t.id}">
      <div class="task-header">
        <div class="task-title">${t.title}</div>
        <div style="display:flex;gap:6px;align-items:center">
          ${t.riskLevel && t.riskLevel !== 'UNKNOWN'
            ? `<span class="chip chip-risk chip-${t.riskLevel.toLowerCase()}">${t.riskLevel} RISK</span>` : ''}
          ${feasibleBadge ? `<span style="font-size:.8rem">${feasibleBadge}</span>` : ''}
        </div>
      </div>
      <div class="task-meta">
        <span class="chip chip-time">🕐 ${timeStr}</span>
        ${t.location  ? `<span class="chip chip-loc">📍 ${t.location}</span>` : ''}
        ${t.category  ? `<span class="chip chip-cat">${t.category}</span>`    : ''}
        ${t.priority  ? `<span class="chip chip-cat">${t.priority}</span>`    : ''}
      </div>
      ${t.weatherSummary ? `<div class="weather-row">
        ${t.weatherIcon
          ? `<img src="https://openweathermap.org/img/wn/${t.weatherIcon}.png" style="width:28px"/>`
          : '🌤️'}
        ${t.weatherSummary} · ${t.temperature ? t.temperature.toFixed(1) + '°C' : ''}
        ${t.distanceKm             ? ` · 📍 ${t.distanceKm.toFixed(1)} km`                                        : ''}
        ${t.estimatedTravelMinutes ? ` · 🚗 ${t.estimatedTravelMinutes} min (${t.trafficCondition || ''} traffic)` : ''}
      </div>` : ''}
      ${recs ? `<div class="recs">${recs}</div>` : ''}
      <div class="task-actions">
        <button class="btn-sm btn-complete" onclick="Controller.completeTask(${t.id})">✓ Done</button>
        <button class="btn-sm btn-refresh"  onclick="Controller.refreshTask(${t.id})">↻ Refresh</button>
        <button class="btn-sm btn-danger"   onclick="Controller.deleteTask(${t.id})">🗑 Delete</button>
      </div>
    </div>`;
  },

  /* ── Quick-add form ─────────────────────────────────────────────────────── */

  toggleQuickForm() {
    document.getElementById('quick-form').classList.toggle('open');
  },

  closeQuickForm() {
    document.getElementById('quick-form').classList.remove('open');
  },

  getQuickFormValues() {
    return {
      title:         document.getElementById('q-title').value.trim(),
      scheduledTime: document.getElementById('q-time').value,
      location:      document.getElementById('q-loc').value  || null,
      category:      document.getElementById('q-cat').value,
      priority:      document.getElementById('q-pri').value,
      description:   document.getElementById('q-desc').value || null
    };
  },

  clearQuickForm() {
    ['q-title','q-time','q-loc','q-desc'].forEach(id =>
      document.getElementById(id).value = ''
    );
  },

  /* ── NLP input ──────────────────────────────────────────────────────────── */

  getNlpInput() {
    return document.getElementById('nlp-input').value.trim();
  },

  clearNlpInput() {
    document.getElementById('nlp-input').value = '';
  },

  /* ── Auth form values ───────────────────────────────────────────────────── */

  getLoginValues() {
    return {
      username: document.getElementById('l-username').value.trim(),
      password: document.getElementById('l-password').value
    };
  },

  getRegisterValues() {
    return {
      username:        document.getElementById('r-username').value.trim(),
      email:           document.getElementById('r-email').value.trim(),
      password:        document.getElementById('r-password').value,
      defaultLocation: document.getElementById('r-location').value.trim() || 'Kolkata, India'
    };
  },

  /* ── View switching ─────────────────────────────────────────────────────── */

  showListView() {
    document.getElementById('list-view').style.display     = '';
    document.getElementById('calendar-view').style.display = 'none';
    document.getElementById('view-list-btn').classList.add('active');
    document.getElementById('view-cal-btn').classList.remove('active');
  },

  showCalendarView() {
    document.getElementById('list-view').style.display     = 'none';
    document.getElementById('calendar-view').style.display = '';
    document.getElementById('view-cal-btn').classList.add('active');
    document.getElementById('view-list-btn').classList.remove('active');
  },

  /* ── Calendar ───────────────────────────────────────────────────────────── */

  _calendar: null,
  _calendarTasks: [],

  renderCalendar(tasks) {
    this._calendarTasks = tasks || [];
    const el = document.getElementById('calendar');

    const events = this._calendarTasks
      .filter(t => t.scheduledTime)
      .map(t => ({
        id: String(t.id),
        title: t.title,
        start: t.scheduledTime,
        color: this._riskColor(t.riskLevel),
        textColor: '#ffffff',
        extendedProps: { task: t }
      }));

    if (this._calendar) {
      this._calendar.removeAllEvents();
      events.forEach(e => this._calendar.addEvent(e));
      return;
    }

    this._calendar = new FullCalendar.Calendar(el, {
      initialView: 'dayGridMonth',
      displayEventTime: false,
      height: 'auto',
      headerToolbar: {
        left: 'prev,next today',
        center: 'title',
        right: 'dayGridMonth,dayGridWeek,listWeek'
      },
      eventDisplay: 'block',
      dayMaxEvents: 3,
      events,
      eventClick: (info) => {
        this.openTaskModal(info.event.extendedProps.task);
      },
      eventDidMount: (info) => {
        const t = info.event.extendedProps.task;
        if (t.completed || t.status === 'COMPLETED') {
          info.el.style.opacity = '.55';
          info.el.style.textDecoration = 'line-through';
        }
      }
    });
    this._calendar.render();
  },

  _riskColor(level) {
    if (level === 'HIGH')   return '#e74c3c';
    if (level === 'MEDIUM') return '#f39c12';
    if (level === 'LOW')    return '#27ae60';
    return '#2c3e50';
  },

  /* ── Task detail modal ──────────────────────────────────────────────────── */

  openTaskModal(t) {
    const dt      = new Date(t.scheduledTime);
    const timeStr = dt.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
    const recs    = (t.recommendations || '').split('\n').filter(Boolean).join('<br/>');
    const feasibleBadge = t.feasible === true  ? '✅ Feasible'
                        : t.feasible === false ? '❌ Risky'
                        : '';
    document.getElementById('task-modal-content').innerHTML = `
      <div class="task-header">
        <div class="task-title">${t.title}</div>
        ${t.riskLevel && t.riskLevel !== 'UNKNOWN'
          ? `<span class="chip chip-risk chip-${t.riskLevel.toLowerCase()}">${t.riskLevel} RISK</span>` : ''}
      </div>
      <div class="task-meta">
        <span class="chip chip-time">🕐 ${timeStr}</span>
        ${t.location  ? `<span class="chip chip-loc">📍 ${t.location}</span>` : ''}
        ${t.category  ? `<span class="chip chip-cat">${t.category}</span>`    : ''}
        ${t.priority  ? `<span class="chip chip-cat">${t.priority}</span>`    : ''}
        ${feasibleBadge ? `<span class="chip chip-cat">${feasibleBadge}</span>` : ''}
      </div>
      ${t.description ? `<p style="margin-bottom:10px;color:var(--text)">${t.description}</p>` : ''}
      ${t.weatherSummary ? `<div class="weather-row">
        ${t.weatherIcon
          ? `<img src="https://openweathermap.org/img/wn/${t.weatherIcon}.png" style="width:28px"/>`
          : '🌤️'}
        ${t.weatherSummary} · ${t.temperature ? t.temperature.toFixed(1) + '°C' : ''}
        ${t.distanceKm             ? ` · 📍 ${t.distanceKm.toFixed(1)} km`                                        : ''}
        ${t.estimatedTravelMinutes ? ` · 🚗 ${t.estimatedTravelMinutes} min (${t.trafficCondition || ''} traffic)` : ''}
      </div>` : ''}
      ${recs ? `<div class="recs">${recs}</div>` : ''}
      <div class="task-actions">
        <button class="btn-sm btn-complete" onclick="Controller.completeTask(${t.id}); View.closeTaskModal();">✓ Done</button>
        <button class="btn-sm btn-refresh"  onclick="Controller.refreshTask(${t.id}); View.closeTaskModal();">↻ Refresh</button>
        <button class="btn-sm btn-danger"   onclick="Controller.deleteTask(${t.id}); View.closeTaskModal();">🗑 Delete</button>
      </div>
    `;
    document.getElementById('task-modal-backdrop').classList.add('open');
  },

  closeTaskModal(e) {
    if (e && e.target !== document.getElementById('task-modal-backdrop')) return;
    document.getElementById('task-modal-backdrop').classList.remove('open');
  },

  /* ── Footer ─────────────────────────────────────────────────────────────── */

  renderFooter() {
    const footer = document.createElement('footer');
    footer.className = 'app-footer';
    footer.innerHTML = `
      <div class="footer-inner">
        <div class="footer-brand">
          <span class="footer-logo">Day-Genie</span>
          <span class="footer-tagline">Context-Aware Intelligent Task Planner</span>
        </div>
        <div class="footer-center">
          <span>Minor Project — II &nbsp;|&nbsp; AY 2025–2026</span>
          <span>Narula Institute of Technology, Dept. of IT</span>
        </div>
        <div class="footer-team">
          <span>Sarfaraj Islam</span>
          <span>Reetam Chakraborty</span>
          <span>Anup Keshri</span>
          <span>Aniket Raj</span>
        </div>
      </div>
    `;
    document.body.appendChild(footer);
  },

  /* ── Toast ──────────────────────────────────────────────────────────────── */

  toast(msg, type = 'ok') {
    const t = document.createElement('div');
    t.className   = `toast ${type}`;
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 3000);
  }
};
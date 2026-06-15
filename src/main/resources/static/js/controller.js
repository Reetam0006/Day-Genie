/**
 * controller.js — Application logic / event orchestration
 * Wires Model ↔ View together; handles user events and coordinates data flow.
 */

const Controller = {

  /* ── Boot ───────────────────────────────────────────────────────────────── */

  _onDashboard() {
    return window.location.pathname.includes('dashboard');
  },

  init() {
    if (this._onDashboard()) {
      // dashboard.html: must be authenticated
      if (!Model.getToken()) {
        window.location.href = 'index.html';
        return;
      }
      View.showAppScreen(Model.getUser());
      View.renderFooter();   // ← footer injected here
      this.loadTasks();
    } else {
      // index.html: if already logged in, go straight to dashboard
      if (Model.getToken()) {
        window.location.href = 'dashboard.html';
      }
    }
  },

  /* ── Auth ───────────────────────────────────────────────────────────────── */

  showTab(t) {
    View.clearAuthErrors();
    View.showTab(t);
  },

  async login() {
    const { username, password } = View.getLoginValues();
    const data = await Model.login(username, password);
    if (data.success) {
      Model.saveSession(data.token, data.username);
      window.location.href = 'dashboard.html';
    } else {
      View.setAuthError(data.message || 'Login failed');
    }
  },

  async register() {
    const values = View.getRegisterValues();
    const data   = await Model.register(values);
    if (data.success) {
      Model.saveSession(data.token, data.username);
      window.location.href = 'dashboard.html';
    } else {
      View.setRegError(data.message || 'Registration failed');
    }
  },

  logout() {
    Model.clearSession();
    window.location.href = 'index.html';
  },

  /* ── Tasks ──────────────────────────────────────────────────────────────── */

  async loadTasks() {
    const [today, all] = await Promise.all([
      Model.fetchTodayTasks(),
      Model.fetchAllTasks()
    ]);
    View.renderTodayList(today);
    View.renderAllList(all);
    this._allTasks = all;
    View.renderCalendar(all);
  },

  /* ── View switching ─────────────────────────────────────────────────────── */

  switchView(view) {
    if (view === 'calendar') {
      View.showCalendarView();
      View.renderCalendar(this._allTasks || []);
    } else {
      View.showListView();
    }
  },

  /* ── NLP create ─────────────────────────────────────────────────────────── */

  async createFromNlp() {
    const raw = View.getNlpInput();
    if (!raw) return;
    const task = await Model.createTaskFromNlp(raw);
    if (task.id) {
      View.toast('Task created! 🎉', 'ok');
      View.clearNlpInput();
      this.loadTasks();
    } else {
      View.toast(task.message || 'Error creating task', 'err');
    }
  },

  /* ── Manual create ──────────────────────────────────────────────────────── */

  toggleQuickForm() {
    View.toggleQuickForm();
  },

  async createManual() {
    const values = View.getQuickFormValues();
    if (!values.title || !values.scheduledTime) {
      View.toast('Title and time are required', 'err');
      return;
    }
    const task = await Model.createTaskManual(values);
    if (task.id) {
      View.toast('Task created! 🎉', 'ok');
      View.clearQuickForm();
      View.closeQuickForm();
      this.loadTasks();
    } else {
      View.toast('Error creating task', 'err');
    }
  },

  /* ── Task actions ───────────────────────────────────────────────────────── */

  async completeTask(id) {
    await Model.completeTask(id);
    View.toast('Marked complete ✅', 'ok');
    this.loadTasks();
  },

  async refreshTask(id) {
    await Model.refreshTask(id);
    View.toast('Context refreshed ↻', 'ok');
    this.loadTasks();
  },

  async deleteTask(id) {
    if (!confirm('Delete this task?')) return;
    await Model.deleteTask(id);
    View.toast('Deleted 🗑', 'ok');
    this.loadTasks();
  }
};

/* ── Bootstrap ──────────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => Controller.init());
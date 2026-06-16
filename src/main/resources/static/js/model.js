/**
 * model.js — Data / API layer
 * Responsible for all fetch calls to the Day-Genie backend.
 * Every function returns the parsed JSON (or throws on network error).
 */

const API = ''; // same-origin; change to 'http://localhost:8080' for local dev

/* ── Token helpers ─────────────────────────────────────────────────────────── */
const Model = {

  getToken()           { return localStorage.getItem('dg_token')    || ''; },
  getUser()            { return localStorage.getItem('dg_user')     || ''; },
  getDefaultLocation() { return localStorage.getItem('dg_location') || ''; },
  saveSession(token, username, defaultLocation) {
    localStorage.setItem('dg_token', token);
    localStorage.setItem('dg_user',  username);
    if (defaultLocation) localStorage.setItem('dg_location', defaultLocation);
  },
  clearSession() {
    localStorage.removeItem('dg_token');
    localStorage.removeItem('dg_user');
    localStorage.removeItem('dg_location');
  },
  clearSession() {
    localStorage.removeItem('dg_token');
    localStorage.removeItem('dg_user');
  },

  _authHeaders() {
    return {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + this.getToken()
    };
  },

  /* ── Auth ─────────────────────────────────────────────────────────────────── */

  async login(username, password) {
    const res = await fetch(`${API}/api/auth/login`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ username, password })
    });
    return res.json();
  },

  async register({ username, email, password, defaultLocation }) {
    const res = await fetch(`${API}/api/auth/register`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ username, email, password, defaultLocation })
    });
    return res.json();
  },

  /* ── Tasks ────────────────────────────────────────────────────────────────── */

  async fetchTodayTasks() {
    const res = await fetch(`${API}/api/tasks/today`, { headers: this._authHeaders() });
    return res.json();
  },

  async fetchAllTasks() {
    const res = await fetch(`${API}/api/tasks`, { headers: this._authHeaders() });
    return res.json();
  },

  async createTaskFromNlp(rawInput) {
    const res = await fetch(`${API}/api/tasks/nlp`, {
      method:  'POST',
      headers: this._authHeaders(),
      body:    JSON.stringify({ rawInput })
    });
    return res.json();
  },

  async createTaskManual({ title, scheduledTime, location, category, priority, description }) {
    const res = await fetch(`${API}/api/tasks`, {
      method:  'POST',
      headers: this._authHeaders(),
      body:    JSON.stringify({ title, scheduledTime, location, category, priority, description })
    });
    return res.json();
  },

  async completeTask(id) {
    const res = await fetch(`${API}/api/tasks/${id}`, {
      method:  'PUT',
      headers: this._authHeaders(),
      body:    JSON.stringify({ status: 'COMPLETED' })
    });
    return res.json();
  },

  async refreshTask(id) {
    const res = await fetch(`${API}/api/tasks/${id}/refresh`, {
      method:  'POST',
      headers: this._authHeaders()
    });
    return res.json();
  },

  async deleteTask(id) {
    const res = await fetch(`${API}/api/tasks/${id}`, {
      method:  'DELETE',
      headers: this._authHeaders()
    });
    return res.json();
  }
};

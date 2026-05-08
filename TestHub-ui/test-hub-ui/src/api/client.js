const BASE      = '/api';
const TOKEN_KEY = 'testhub_token';
const USER_KEY  = 'testhub_user';

async function request(path, options = {}) {
  const token = localStorage.getItem(TOKEN_KEY);

  const res = await fetch(BASE + path, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
      ...options.headers,
    },
    ...options,
  });

  // Token expiré ou invalide → rediriger vers /login
  if (res.status === 401) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    window.location.href = '/login';
    return;
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw Object.assign(
      new Error(err.message || 'Erreur serveur'),
      { status: res.status, data: err }
    );
  }

  return res.status === 204 ? null : res.json();
}

export const projectApi = {

  getAll:        ()   => request('/projects'),
  getById:       (id) => request(`/projects/${id}`),
  getFiles:      (id) => request(`/projects/${id}/files`),
  delete:        (id) => request(`/projects/${id}`, { method: 'DELETE' }),
  reindex:       (id) => request(`/projects/${id}/reindex`,      { method: 'POST' }),
  reinstallVenv: (id) => request(`/projects/${id}/reinstall-venv`, { method: 'POST' }),

  /** Logs persistés du setup venv — avec token ✅ */
  getSetupLogs: (id) => request(`/projects/${id}/setup-logs`),

  /** Création via ZIP (multipart) */
  createFromZip: (name, description, testsDir, zipFile) => {
    const form = new FormData();
    form.append('name', name);
    if (description) form.append('description', description);
    form.append('testsDir', testsDir || 'Tests');
    form.append('file', zipFile);
    return request('/projects/zip', { method: 'POST', headers: {}, body: form });
  },

  /** Création via Bitbucket (JSON) */
  createFromGit: (payload) =>
    request('/projects/git', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  /** Récupère la liste des branches d'un repo Bitbucket */
  listBranches: (repositoryUrl, username, appPassword) =>
    request('/projects/git/branches', {
      method: 'POST',
      body: JSON.stringify({ repositoryUrl, username, appPassword }),
    }),

  /** git pull sur un projet existant */
  pull: (id, username, appPassword) =>
    request(`/projects/${id}/pull`, {
      method: 'POST',
      body: JSON.stringify({ username, appPassword }),
    }),
};

export const runApi = {
  getAll:       ()          => request('/runs'),
  getById:      (id)        => request(`/runs/${id}`),
  getByProject: (projectId) => request(`/runs/by-project/${projectId}`),

  /** Logs persistés d'un run — avec token ✅ */
  getLogs: (id) => request(`/runs/${id}/logs`),

  launch: (payload) => request('/runs', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
};

/**
 * URLs des rapports Robot Framework.
 * Reports en permitAll côté Spring Security — pas besoin du token.
 * Mais on le passe quand même pour compatibilité future.
 */
export const reportUrl = {
  report: (runId) => `${BASE}/reports/${runId}/report`,
  log:    (runId) => `${BASE}/reports/${runId}/log`,
  output: (runId) => `${BASE}/reports/${runId}/output`,
};
const BASE = '/api';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });
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

  getAll:   () => request('/projects'),
  getById:  (id) => request(`/projects/${id}`),
  getFiles: (id) => request(`/projects/${id}/files`),
  delete:   (id) => request(`/projects/${id}`, { method: 'DELETE' }),
  reindex:  (id) => request(`/projects/${id}/reindex`, { method: 'POST' }),
  reinstallVenv: (id) => request(`/projects/${id}/reinstall-venv`, { method: 'POST' }),

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

  /**
   * Récupère la liste des branches d'un repo Bitbucket.
   * Utilise git ls-remote — ne clone pas le repo.
   * @returns {Promise<string[]>} ex: ["main", "develop", "TRN", "UAT"]
   */
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
  launch:       (payload)   => request('/runs', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
};

export const reportUrl = {
  report: (runId) => `/api/reports/${runId}/report`,
  log:    (runId) => `/api/reports/${runId}/log`,
  output: (runId) => `/api/reports/${runId}/output`,
};
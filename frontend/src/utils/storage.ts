const TOKEN_KEY = 'yulu_token';
const SESSION_KEY = 'yulu_session_id';
const ROLE_KEY = 'yulu_role';
const USERNAME_KEY = 'yulu_username';

export const getToken = () => localStorage.getItem(TOKEN_KEY);

export const setToken = (token: string) => localStorage.setItem(TOKEN_KEY, token);

export const setRole = (role: string) => localStorage.setItem(ROLE_KEY, role);

export const getRole = () => localStorage.getItem(ROLE_KEY);

export const setUsername = (username: string) => localStorage.setItem(USERNAME_KEY, username);

export const getUsername = () => localStorage.getItem(USERNAME_KEY);

export const clearToken = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
  localStorage.removeItem(USERNAME_KEY);
};

export const getCurrentSessionId = (): number | null => {
  const v = localStorage.getItem(SESSION_KEY);
  if (!v) return null;
  const n = Number(v);
  return Number.isNaN(n) ? null : n;
};

export const setCurrentSessionId = (id: number) =>
  localStorage.setItem(SESSION_KEY, String(id));

export const clearCurrentSessionId = () => localStorage.removeItem(SESSION_KEY);




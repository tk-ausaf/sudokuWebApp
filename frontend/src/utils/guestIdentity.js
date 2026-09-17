const STORAGE_KEY = 'sudoku_guest_id';

// Used only if localStorage is unavailable (private browsing, storage disabled) - keeps the
// current page load working, just without persistence across reloads.
let inMemoryFallbackId = null;

/**
 * A random id generated once in this browser and reused for every request thereafter - the
 * backend trusts it as-is (see GuestSessionFilter's Javadoc for why: no signed cookie can survive
 * the frontend and backend being separate deployed origins, so guest identity is now a
 * deliberately client-asserted value instead. Low stakes if spoofed - no credentials or PII ride
 * on it, only which anonymous puzzle attempts/games a browser is treated as owning.
 */
export function getGuestId() {
  try {
    let id = localStorage.getItem(STORAGE_KEY);
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem(STORAGE_KEY, id);
    }
    return id;
  } catch {
    if (!inMemoryFallbackId) {
      inMemoryFallbackId = crypto.randomUUID();
    }
    return inMemoryFallbackId;
  }
}

/** Called after a successful login - the guest id has done its job (see AuthContext.login) and isn't needed once authenticated. */
export function clearGuestId() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // localStorage unavailable - nothing was persisted to begin with
  }
}
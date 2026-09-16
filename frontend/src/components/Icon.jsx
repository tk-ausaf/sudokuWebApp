// Small hand-drawn stroke icon set (no icon library dependency) - consistent 20x20, currentColor
// stroke, used anywhere a labeled button would otherwise take up more room than it's worth.
const PATHS = {
  menu: (
    <>
      <line x1="3" y1="6" x2="17" y2="6" />
      <line x1="3" y1="10" x2="17" y2="10" />
      <line x1="3" y1="14" x2="17" y2="14" />
    </>
  ),
  undo: (
    <>
      <path d="M7 5 3 9l4 4" />
      <path d="M3 9h9a4.5 4.5 0 0 1 0 9h-1.5" />
    </>
  ),
  save: (
    <>
      <rect x="3" y="3" width="14" height="14" rx="1.5" />
      <rect x="6.5" y="3" width="5" height="4.5" />
      <rect x="6" y="11.5" width="6" height="4.5" />
    </>
  ),
  refresh: (
    <>
      <path d="M4 9.5a6 6 0 0 1 10.2-4.3" />
      <path d="M14.4 2.5v3h-3" />
      <path d="M16 9.5a6 6 0 0 1-10.2 4.3" />
      <path d="M3.6 16.5v-3h3" />
    </>
  ),
  settings: (
    <>
      <line x1="3" y1="6" x2="17" y2="6" />
      <circle cx="12" cy="6" r="1.8" />
      <line x1="3" y1="14" x2="17" y2="14" />
      <circle cx="8" cy="14" r="1.8" />
    </>
  ),
};

export default function Icon({ name, size = 18, className }) {
  const shape = PATHS[name];
  if (!shape) return null;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {shape}
    </svg>
  );
}
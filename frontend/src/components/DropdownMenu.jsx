import { createContext, useContext, useEffect, useRef, useState } from 'react';

const CloseContext = createContext(() => {});

/**
 * A button that reveals a menu of actions/toggles, closing on an outside click, Escape, or
 * (by default) after a DropdownItem is selected. Use this instead of adding more always-visible
 * buttons - new items can be dropped into an existing menu without changing its footprint.
 */
export default function DropdownMenu({ trigger, label, align = 'end', children }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const close = () => setOpen(false);

  useEffect(() => {
    if (!open) return undefined;
    function handlePointerDown(event) {
      if (rootRef.current && !rootRef.current.contains(event.target)) {
        close();
      }
    }
    function handleKeyDown(event) {
      if (event.key === 'Escape') close();
    }
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  return (
    <div className="dropdown" ref={rootRef}>
      <button
        type="button"
        className="dropdown__trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        onClick={() => setOpen((prev) => !prev)}
      >
        {trigger}
      </button>
      {open && (
        <CloseContext.Provider value={close}>
          <div className={`dropdown__menu dropdown__menu--${align}`} role="menu">
            {children}
          </div>
        </CloseContext.Provider>
      )}
    </div>
  );
}

/** A clickable menu item; closes the menu after firing `onSelect` unless `closeOnSelect` is false. */
export function DropdownItem({ onSelect, disabled, closeOnSelect = true, children }) {
  const close = useContext(CloseContext);
  return (
    <button
      type="button"
      role="menuitem"
      className="dropdown__item"
      disabled={disabled}
      onClick={() => {
        onSelect?.();
        if (closeOnSelect) close();
      }}
    >
      {children}
    </button>
  );
}

/** A checkbox menu item (e.g. a persistent setting) that stays open when toggled. */
export function DropdownToggleItem({ checked, onChange, children }) {
  return (
    <label className="dropdown__item dropdown__item--toggle" role="menuitemcheckbox" aria-checked={checked}>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      {children}
    </label>
  );
}
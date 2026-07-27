import { useState } from 'react'

interface PasswordFieldProps {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  hint?: string
}

export default function PasswordField({ id, label, value, onChange, hint }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="password-field">
        <input
          id={id}
          className="input"
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          required
          autoComplete="current-password"
        />
        <button
          type="button"
          className="password-toggle"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'パスワードを隠す' : 'パスワードを表示'}
        >
          {visible ? '🙈' : '👁'}
        </button>
      </div>
      {hint && <span className="hint">{hint}</span>}
    </div>
  )
}

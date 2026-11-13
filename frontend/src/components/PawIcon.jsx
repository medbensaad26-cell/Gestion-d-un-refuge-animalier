export default function PawIcon({ className = '' }) {
  return (
    <svg className={className} viewBox="0 0 32 32" fill="currentColor" aria-hidden="true">
      <ellipse cx="7.4" cy="11" rx="3.3" ry="4.2" transform="rotate(-25 7.4 11)" />
      <ellipse cx="14" cy="6.8" rx="3.2" ry="4.2" transform="rotate(-8 14 6.8)" />
      <ellipse cx="22" cy="8" rx="3.2" ry="4.2" transform="rotate(14 22 8)" />
      <ellipse cx="27" cy="14.5" rx="3" ry="4" transform="rotate(28 27 14.5)" />
      <path d="M7.4 22.9c.4-3.3 4-5 6.1-7.6 2-2.4 5.9-2 7.5.4 1.8 2.6 5 4.7 4.6 8-.4 4-3.9 5-7.1 3.4-1.9-.9-3.1-.8-5 .1-3.1 1.4-6.7-.4-6.1-4.3Z" />
    </svg>
  )
}

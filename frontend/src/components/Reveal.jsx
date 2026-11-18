import { useEffect, useRef } from 'react'

export default function Reveal({ children, className = '' }) {
  const node = useRef(null)
  useEffect(() => {
    if (!('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const element = node.current
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        element.classList.add('is-revealed')
        observer.disconnect()
      }
    }, { threshold: .08 })
    element.classList.add('will-reveal')
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  return <div ref={node} className={`reveal ${className}`}>{children}</div>
}

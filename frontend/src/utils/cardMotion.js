// Transform only the hovered element. No React render loop or animation dependency.
export function tiltCard(event) {
  if (!window.matchMedia('(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)').matches) return
  const element = event.currentTarget
  const bounds = element.getBoundingClientRect()
  element.style.setProperty('--tilt-x', `${-((event.clientY - bounds.top) / bounds.height - .5) * 5}deg`)
  element.style.setProperty('--tilt-y', `${((event.clientX - bounds.left) / bounds.width - .5) * 5}deg`)
}

export function resetCard(event) {
  event.currentTarget.style.removeProperty('--tilt-x')
  event.currentTarget.style.removeProperty('--tilt-y')
}

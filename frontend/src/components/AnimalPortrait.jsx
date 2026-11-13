import PawIcon from './PawIcon.jsx'

export default function AnimalPortrait({ species }) {
  const value = species?.toLocaleLowerCase('fr').normalize('NFD').replace(/[\u0300-\u036f]/g, '') ?? ''
  const cat = value.includes('chat') || value.includes('felin')
  const dog = value.includes('chien') || value.includes('canin')
  if (!cat && !dog) return <PawIcon className="animal-paw" />
  return (
    <svg viewBox="0 0 180 142" fill="none" className="animal-portrait" aria-hidden="true">
      {cat ? <>
        <path d="m47 65-1-42 38 25 36-25 2 43c11 39-6 58-38 58-31 0-48-20-37-59Z" fill="currentColor" opacity=".78" />
        <path d="m55 50-1-13 14 11m36-1 10-10 1 13" fill="#F3DAC5" />
        <path d="m64 80 7 3m29 0 7-3" stroke="#344B3D" strokeWidth="4" strokeLinecap="round" />
        <path d="m79 94 5 5 5-5" fill="#F3DAC5" />
        <path d="M84 99v6m-9 0c5 5 13 5 18 0m-41-9-17-4m17 11-17 2m80-9 17-4m-17 11 17 2" stroke="#344B3D" strokeWidth="2" strokeLinecap="round" />
      </> : <>
        <path d="M60 42c-21-6-34 14-32 43 1 20 16 24 26 13l6-56Zm59 0c21-6 34 14 32 43-1 20-16 24-26 13l-6-56Z" fill="currentColor" opacity=".62" />
        <path d="M48 65c0-26 15-39 41-39s42 14 42 39l-4 30c-3 24-17 34-38 34s-36-13-39-35l-2-29Z" fill="currentColor" />
        <path d="M70 93c-9-20 46-20 38 0-4 12-13 18-19 18-7 0-15-6-19-18Z" fill="#F4E2CC" />
        <ellipse cx="69" cy="73" rx="4" ry="5" fill="#344B3D" />
        <ellipse cx="110" cy="73" rx="4" ry="5" fill="#344B3D" />
        <path d="M82 87c4-3 10-3 14 0 3 3-2 10-7 10-4 0-10-7-7-10Z" fill="#344B3D" />
        <path d="M89 97v6m-9 0c5 5 13 5 18 0" stroke="#344B3D" strokeWidth="2" strokeLinecap="round" />
      </>}
    </svg>
  )
}

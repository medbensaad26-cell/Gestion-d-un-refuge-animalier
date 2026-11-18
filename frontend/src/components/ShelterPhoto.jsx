import { useState } from 'react'
import PawIcon from './PawIcon.jsx'

const descriptions = {
  golden: 'Golden retriever au pelage doré, le regard tourné vers l’objectif',
  puppy: 'Chiot roux et blanc installé sur un tapis clair',
  kittens: 'Petit chat au pelage clair et aux yeux bleus',
  friends: 'Deux chiots dorés assis côte à côte dans les fleurs',
}

export default function ShelterPhoto({ name = 'golden', className = '', priority = false, sizes = '(max-width: 700px) 100vw, 50vw' }) {
  const [failed, setFailed] = useState(false)
  if (failed) return <span className={`photo-fallback ${className}`} role="img" aria-label="Photographie illustrative indisponible"><PawIcon /><span>Un regard à découvrir</span></span>
  return <img className={`shelter-photo ${className}`} src={`/images/${name}-640.webp`} srcSet={`/images/${name}-640.webp 640w, /images/${name}-1280.webp 1280w`} sizes={sizes} width="640" height={name === 'puppy' ? '960' : name === 'kittens' ? '669' : name === 'friends' ? '480' : '427'} alt={`${descriptions[name]}. Photographie d’illustration.`} loading={priority ? 'eager' : 'lazy'} fetchPriority={priority ? 'high' : 'auto'} decoding="async" onError={() => setFailed(true)} />
}

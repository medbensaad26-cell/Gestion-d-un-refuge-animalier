import { useState } from 'react'
import PawIcon from './PawIcon.jsx'
import ShelterPhoto from './ShelterPhoto.jsx'

export default function AnimalPortrait({ species, variant = 0, imageUrl = null, name = '' }) {
  const [failedUrl, setFailedUrl] = useState(null)
  if (imageUrl && failedUrl !== imageUrl) {
    return <span className="animal-media"><img className="shelter-photo" src={imageUrl} alt={name ? `Photo de ${name}` : 'Photo de l’animal'} loading="lazy" decoding="async" onError={() => setFailedUrl(imageUrl)} /></span>
  }
  const value = species?.toLocaleLowerCase('fr').normalize('NFD').replace(/[̀-ͯ]/g, '') ?? ''
  const cat = value.includes('chat') || value.includes('felin')
  const dog = value.includes('chien') || value.includes('canin')
  return <span className="animal-media">
    {cat || dog ? <ShelterPhoto name={cat ? 'kittens' : variant % 2 ? 'puppy' : 'golden'} sizes="(max-width: 600px) 100vw, (max-width: 1000px) 50vw, 33vw" /> : <span className="photo-fallback"><PawIcon className="animal-paw" /><span>Un profil à découvrir</span></span>}
    <span className="illustration-label">{cat || dog ? 'Photo illustrative' : 'Photo non disponible'}</span>
  </span>
}

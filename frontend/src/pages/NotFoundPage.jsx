import { Link } from 'react-router'
import PawIcon from '../components/PawIcon.jsx'

export default function NotFoundPage() {
  return (
    <section className="not-found container">
      <PawIcon className="feedback-paw" />
      <p className="eyebrow">Page introuvable · 404</p>
      <h1>Vous avez suivi<br />une autre piste.</h1>
      <p>Cette page n’existe pas. Revenons au refuge pour retrouver nos animaux.</p>
      <Link className="button button-primary" to="/">Revenir à l’accueil <span aria-hidden="true">↗</span></Link>
    </section>
  )
}

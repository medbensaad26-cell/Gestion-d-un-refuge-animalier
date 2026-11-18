import { useEffect } from 'react'
import { Link, useOutletContext } from 'react-router'
import Icon from '../components/Icon.jsx'
import PawIcon from '../components/PawIcon.jsx'
import ShelterPhoto from '../components/ShelterPhoto.jsx'

export default function AdminDashboardPage() {
  const { summary, refresh } = useOutletContext()
  useEffect(() => {
    const original = document.title
    document.title = 'Tableau de bord · Pawtale'
    return () => { document.title = original }
  }, [])
  return <section className="dashboard-overview" aria-labelledby="dashboard-title">
    <p className="eyebrow">VOTRE REFUGE, EN UN REGARD</p>
    <div className="dashboard-heading"><div><h1 id="dashboard-title">Tableau de bord</h1><p>Bonjour, {summary.admin.nom}. Heureux de vous retrouver.</p></div><button className="button button-outline" type="button" onClick={refresh}>Actualiser <span aria-hidden="true">↻</span></button></div>
    <div className="dashboard-welcome"><div><span className="eyebrow">CHAQUE GESTE COMPTE</span><h2>De l’attention aujourd’hui.<br /><em>De belles histoires demain.</em></h2><p>Les animaux, leurs nouvelles et les projets d’adoption : tout votre refuge, au même endroit.</p><Link className="button button-light" to="/admin/animaux/nouveau">Accueillir un animal <Icon /></Link></div><div className="dashboard-welcome-photo"><ShelterPhoto name="friends" priority /><span>Photo illustrative</span></div></div>
    <p className="dashboard-description">Les chiffres du refuge · Informations du serveur, tous statuts confondus.</p>
    <dl className="dashboard-metrics">
      {[['animaux', 'Animaux au refuge', 'Animaux enregistrés', 'heart'], ['annonces', 'Annonces', 'Publications enregistrées', 'news'], ['demandes', 'Demandes d’adoption', 'Demandes reçues', 'mail']].map(([key, label, description, icon]) => <div className={`dashboard-metric metric-${key}`} key={key}><dt><span>{label}</span><span className="metric-icon">{key === 'animaux' ? <PawIcon /> : <Icon name={icon} />}</span></dt><dd><span className="dashboard-count">{summary[key].toLocaleString('fr-FR')}</span><span className="dashboard-count-caption">{description}</span></dd></div>)}
    </dl>
    <div className="dashboard-section-heading"><h2>À vous de prendre le relais.</h2><span>Les essentiels du quotidien</span></div>
    <div className="dashboard-quick-links">
      <div className="dashboard-note"><span className="quick-link-icon"><PawIcon /></span><h2>Les animaux du refuge.</h2><p>Consultez les fiches, recherchez un animal ou enregistrez une nouvelle arrivée.</p><Link to="/admin/animaux">Gérer les animaux <Icon /></Link></div>
      <div className="dashboard-note"><span className="quick-link-icon"><Icon name="news" /></span><h2>Les annonces du refuge.</h2><p>Préparez une annonce et publiez-la pour la rendre visible aux visiteurs.</p><Link to="/admin/annonces">Gérer les annonces <Icon /></Link></div>
      <div className="dashboard-note"><span className="quick-link-icon"><Icon name="mail" /></span><h2>Les demandes d’adoption.</h2><p>Consultez les coordonnées des demandeurs et examinez les demandes en attente.</p><Link to="/admin/demandes">Examiner les demandes <Icon /></Link></div>
    </div>
  </section>
}

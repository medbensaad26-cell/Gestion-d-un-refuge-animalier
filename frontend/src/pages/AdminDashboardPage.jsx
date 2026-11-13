import { useEffect } from 'react'
import { Link, useOutletContext } from 'react-router'

export default function AdminDashboardPage() {
  const { summary, refresh } = useOutletContext()
  useEffect(() => {
    const original = document.title
    document.title = 'Tableau de bord · Le Refuge'
    return () => { document.title = original }
  }, [])
  return <section aria-labelledby="dashboard-title">
    <p className="eyebrow">Vue d’ensemble du refuge</p>
    <div className="dashboard-heading"><div><h1 id="dashboard-title">Tableau de bord</h1><p>Bonjour, {summary.admin.nom}.</p></div><button className="button button-outline" type="button" onClick={refresh}>Actualiser</button></div>
    <p className="dashboard-description">Retrouvez les chiffres du refuge, tous statuts confondus.</p>
    <dl className="dashboard-metrics">
      {[['animaux', 'Animaux au refuge', 'Animaux enregistrés'], ['annonces', 'Annonces', 'Publications enregistrées'], ['demandes', 'Demandes d’adoption', 'Demandes reçues']].map(([key, label, description]) => <div className={`dashboard-metric metric-${key}`} key={key}><dt>{label}</dt><dd><span className="dashboard-count">{summary[key].toLocaleString('fr-FR')}</span><span className="dashboard-count-caption">{description}</span></dd></div>)}
    </dl>
    <div className="dashboard-note"><h2>Les animaux du refuge.</h2><p>Consultez les fiches, recherchez un animal ou enregistrez une nouvelle arrivée.</p><Link to="/admin/animaux">Gérer les animaux <span aria-hidden="true">→</span></Link></div>
    <div className="dashboard-note announcement-dashboard-note"><h2>Les annonces du refuge.</h2><p>Préparez une annonce et publiez-la pour la rendre visible aux visiteurs.</p><Link to="/admin/annonces">Gérer les annonces <span aria-hidden="true">→</span></Link></div>
    <div className="dashboard-note announcement-dashboard-note"><h2>Les demandes d’adoption.</h2><p>Consultez les coordonnées des demandeurs et examinez les demandes en attente.</p><Link to="/admin/demandes">Examiner les demandes <span aria-hidden="true">→</span></Link></div>
  </section>
}

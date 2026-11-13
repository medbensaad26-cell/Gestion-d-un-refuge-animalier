import { Link } from 'react-router'
import AnnouncementsFeed from './AnnouncementsFeed.jsx'

export default function HomeAnnouncements() {
  return <section className="home-announcements container" aria-labelledby="home-announcements-title">
    <div className="section-heading"><div><p className="eyebrow">Les nouvelles du refuge</p><h2 id="home-announcements-title">Nos dernières annonces.</h2></div><Link className="all-announcements-link" to="/annonces">Toutes les annonces <span aria-hidden="true">↗</span></Link></div>
    <p className="section-description">Des animaux à découvrir et de nouvelles histoires à écrire. Retrouvez les dernières annonces publiées par le refuge.</p>
    <AnnouncementsFeed limit={3} headingLevel={3} />
  </section>
}

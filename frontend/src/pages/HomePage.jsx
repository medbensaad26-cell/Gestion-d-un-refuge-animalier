import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import AnimalCard from '../components/AnimalCard.jsx'
import AnimalPortrait from '../components/AnimalPortrait.jsx'
import PawIcon from '../components/PawIcon.jsx'
import ShelterPhoto from '../components/ShelterPhoto.jsx'
import Icon from '../components/Icon.jsx'
import Reveal from '../components/Reveal.jsx'
import { getAnimals } from '../services/animals.js'
import HomeAnnouncements from '../components/HomeAnnouncements.jsx'

const faqs = [
  ['Comment commencer une adoption ?', 'Découvrez les profils des animaux, puis choisissez « Faire une demande d’adoption » sur la fiche d’un animal disponible. Renseignez vos coordonnées : le refuge pourra vous recontacter pour la suite.'],
  ['Le matching garantit-il une adoption réussie ?', 'Le score indique uniquement l’accord avec vos préférences d’espèce, de tranche d’âge et de sexe. Il ne remplace pas une rencontre ni un échange avec le refuge sur les besoins de l’animal.'],
  ['Puis-je faire une demande sans créer de compte ?', 'Oui. Le formulaire public demande uniquement votre nom, votre adresse e-mail et votre téléphone. L’espace administrateur est réservé à la gestion du refuge.'],
  ['Une demande réserve-t-elle l’animal ?', 'Non. Une demande reste en attente d’examen. La disponibilité est vérifiée avant l’envoi et la décision finale appartient au refuge.'],
]

export default function HomePage() {
  const [request, setRequest] = useState(0)
  const [state, setState] = useState({ status: 'loading', animals: [] })
  const [filters, setFilters] = useState({ q: '', species: '', status: '' })

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    getAnimals({ signal: controller.signal })
      .then(animals => { if (active) setState({ status: 'success', animals }) })
      .catch(() => { if (active && !controller.signal.aborted) setState({ status: 'error', animals: [] }) })
    return () => { active = false; controller.abort() }
  }, [request])

  function retry() { setState({ status: 'loading', animals: [] }); setRequest(value => value + 1) }
  const normalize = value => (value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('fr')
  const filtered = Boolean(filters.q || filters.species || filters.status)
  const visible = state.animals.filter(animal => (!filters.species || normalize(animal.espece) === normalize(filters.species)) && (!filters.status || animal.statut === filters.status) && (!filters.q || normalize(`${animal.nom} ${animal.espece} ${animal.race ?? ''}`).includes(normalize(filters.q.trim()))))
  function discover(species) { setFilters({ q: '', species, status: 'DISPONIBLE' }); document.getElementById('animaux')?.scrollIntoView({ block: 'start' }) }

  return <>
    <section className="hero-section">
      <div className="container hero-grid">
        <div className="hero-copy">
          <p className="eyebrow"><span />DE PETITES PATTES, DE GRANDES HISTOIRES</p>
          <h1>Une rencontre.<br /><em>Toute une vie.</em></h1>
          <p className="hero-description">Il y a des regards qui changent tout.<br />Découvrez les animaux du refuge et faites le premier pas vers une belle histoire, ensemble.</p>
          <div className="hero-actions"><Link className="button button-primary" to="/#animaux">Rencontrer nos animaux <Icon /></Link><Link className="hero-secondary" to="/trouver-compagnon"><Icon name="sparkle" />Trouver mon compagnon</Link></div>
          <div className="hero-note"><span className="hero-note-icon"><Icon name="heart" /></span><p>Une adoption réfléchie.<br /><strong>Une nouvelle chance à offrir.</strong></p></div>
        </div>
        <div className="hero-art">
          <div className="hero-orbit" aria-hidden="true" /><PawIcon className="hero-floating-paw" />
          <figure className="hero-photo"><ShelterPhoto name="golden" priority /><figcaption>Photographie illustrative · Helena Lopes / Pexels</figcaption></figure>
          <div className="hero-stamp" aria-hidden="true"><Icon name="heart" /><span>UNE PLACE<br />POUR CHACUN</span></div>
          <div className="hero-cat"><ShelterPhoto name="kittens" priority sizes="180px" /><span>Photo illustrative</span></div>
          <div className="hero-photo-note"><span><PawIcon /></span><div>Et si votre belle histoire<br /><strong>commençait ici ?</strong></div></div>
        </div>
      </div>
    </section>
    <div className="values-strip"><div className="container"><span><Icon name="heart" />Chaque animal compte</span><span><Icon name="shield" />Une adoption responsable</span><span><Icon name="leaf" />À chacun son rythme</span></div></div>

    <section className="discovery-section container" aria-label="Découvrir les chiens et les chats"><div><p className="eyebrow">LE BONHEUR A PLUSIEURS VISAGES</p><h2>Plutôt truffe humide<br /> ou <em>petits ronrons ?</em></h2></div><button type="button" className="discovery-card" onClick={() => discover('Chien')}><span className="discovery-photo"><ShelterPhoto name="puppy" sizes="150px" /></span><span className="discovery-copy"><strong>Les chiens</strong><span>Découvrir les chiens disponibles</span><Icon /></span></button><button type="button" className="discovery-card" onClick={() => discover('Chat')}><span className="discovery-photo"><ShelterPhoto name="kittens" sizes="150px" /></span><span className="discovery-copy"><strong>Les chats</strong><span>Découvrir les chats disponibles</span><Icon /></span></button></section>

    <section className="animals-section container" id="animaux" aria-labelledby="animals-title">
      <Reveal><div className="section-heading"><div><p className="eyebrow">ILS ONT TANT À PARTAGER</p><h2 id="animals-title">Votre prochain <em>coup de cœur.</em></h2></div><Link className="text-link" to="/trouver-compagnon">Besoin d’un petit coup de patte ? <Icon /></Link></div>
        <p className="section-description">Un regard, une personnalité, une place dans votre vie. Apprenez à connaître les pensionnaires du refuge.</p></Reveal>
      {state.status === 'success' && state.animals.some(animal => animal.statut === 'DISPONIBLE') && <div className="available-spotlight"><p>À adopter<br /><strong>en ce moment</strong></p>{state.animals.filter(animal => animal.statut === 'DISPONIBLE').slice(0, 3).map(animal => <Link key={animal.id} to={`/animaux/${animal.id}`} aria-label={`Découvrir ${animal.nom}, disponible à l’adoption`}><span className="spotlight-photo"><AnimalPortrait species={animal.espece} variant={animal.id} imageUrl={animal.imageUrl} name={animal.nom} /></span><span><strong>{animal.nom}</strong><small>{animal.espece} · Disponible</small></span><Icon /></Link>)}</div>}
      <div className="catalogue-toolbar">
        <div className="species-tabs" role="group" aria-label="Filtrer par espèce">{[['', 'Tous les animaux'], ['Chien', 'Les chiens'], ['Chat', 'Les chats']].map(([species, label]) => <button key={species} type="button" aria-pressed={filters.species === species} onClick={() => setFilters(previous => ({ ...previous, species }))}>{species === '' && <PawIcon />}{label}</button>)}</div>
        <form className="catalogue-search" role="search" aria-label="Rechercher les animaux du refuge" onSubmit={event => event.preventDefault()}><label className="search-input"><Icon name="search" /><span className="sr-only">Rechercher un animal</span><input type="search" placeholder="Un nom, une race…" value={filters.q} onChange={event => setFilters(previous => ({ ...previous, q: event.target.value }))} /></label><label className="catalogue-status"><span className="sr-only">Statut de l’animal</span><select value={filters.status} onChange={event => setFilters(previous => ({ ...previous, status: event.target.value }))}><option value="">Tous les statuts</option><option value="DISPONIBLE">À adopter</option><option value="EN_SOIN">En soins</option><option value="ADOPTE">Adoptés</option></select></label></form>
      </div>
      {state.status === 'loading' && <div className="catalogue-loading" role="status"><span className="sr-only">Chargement des animaux du refuge.</span><div className="animals-grid" aria-hidden="true">{[0, 1, 2].map(n => <div className="animal-skeleton" key={n}><div /><span /><span /></div>)}</div></div>}
      {state.status === 'error' && <div className="feedback-panel" role="alert"><span className="feedback-icon" aria-hidden="true">↻</span><h3>Les animaux se font un peu attendre.</h3><p>La liste est momentanément indisponible. Vous pouvez réessayer dans un instant.</p><button className="button button-outline" type="button" onClick={retry}>Réessayer</button></div>}
      {state.status === 'success' && <><div className="catalogue-result-line"><p className="animal-count" role="status">{visible.length} {visible.length === 1 ? 'animal à découvrir' : 'animaux à découvrir'}</p>{filtered && <button className="text-button" type="button" onClick={() => setFilters({ q: '', species: '', status: '' })}>Réinitialiser les filtres</button>}</div>{visible.length ? <div className="animals-grid">{visible.map((animal, index) => <AnimalCard key={animal.id} animal={animal} index={index} />)}</div> : <div className="feedback-panel" role="status"><PawIcon className="feedback-paw" /><h3>{filtered ? 'Pas encore de rencontre avec ces critères.' : 'La liste est encore toute calme.'}</h3><p>{filtered ? 'Essayez un autre nom, une autre espèce ou élargissez les statuts.' : 'Aucun animal n’est présenté pour le moment. Revenez bientôt.'}</p></div>}</>}
      <p className="catalogue-photo-note">Les informations proviennent des fiches du refuge. Les images marquées « Photo illustrative » ne représentent pas les animaux présentés.</p>
    </section>

    <section className="matching-teaser container" aria-labelledby="matching-teaser-title">
      <div className="teaser-images"><div><ShelterPhoto name="puppy" sizes="250px" /></div><div><ShelterPhoto name="kittens" sizes="200px" /></div><span>Photos illustratives</span><PawIcon /></div>
      <div className="teaser-copy"><p className="eyebrow"><Icon name="sparkle" />UNE RENCONTRE QUI VOUS RESSEMBLE</p><h2 id="matching-teaser-title">Le début d’une histoire.<br /><em>La vôtre, peut-être.</em></h2><p>Plutôt chien ou chat ? Jeune ou senior ? Trois préférences pour découvrir les animaux disponibles qui vous correspondent.</p><Link className="button button-light" to="/trouver-compagnon">Trouvez votre compagnon idéal <Icon /></Link><span className="teaser-footnote">3 questions facultatives · Sans création de compte</span></div>
    </section>

    <section className="journey-section container" id="parcours" aria-labelledby="journey-title"><Reveal><div className="section-heading"><div><p className="eyebrow">UNE ADOPTION, PAS À PAS</p><h2 id="journey-title">De la découverte<br />à une <em>nouvelle vie.</em></h2></div><p className="section-side-copy">Une belle rencontre se prépare.<br />Prenez le temps de faire connaissance.</p></div></Reveal><div className="journey-grid">{[['search', '01', 'Laissez-vous rencontrer', 'Explorez les profils ou laissez vos préférences vous guider vers vos premières rencontres.'], ['heart', '02', 'Faites le premier pas', 'Un animal vous touche ? Consultez sa fiche et envoyez votre demande avec vos coordonnées.'], ['home', '03', 'Écrivez la suite ensemble', 'Le refuge examine votre demande. Un échange permettra de préparer la suite de votre projet.']].map(([icon, number, title, description]) => <Reveal key={number}><article className="journey-card"><div className="journey-card-top"><span><Icon name={icon} /></span><span>{number}</span></div><h3>{title}</h3><p>{description}</p></article></Reveal>)}</div></section>

    <section className="about-section" id="refuge" aria-labelledby="about-title"><div className="container about-grid"><Reveal className="about-photo"><ShelterPhoto name="friends" /><span>Photographie illustrative · Chevanon Photography / Pexels</span><div className="about-photo-tag"><Icon name="heart" />Un peu de patience.<br />Beaucoup d’attention.</div></Reveal><Reveal className="about-copy"><p className="eyebrow">L’ESPRIT DU REFUGE</p><h2 id="about-title">Un lieu de passage.<br /><em>Une chance pour la vie.</em></h2><p>Accueillir, prendre soin et offrir une nouvelle chance : chaque animal mérite de trouver sa place, à son rythme.</p><p>Au-delà d’une fiche, il y a un être vivant, ses besoins et sa singularité. Prenez le temps de le découvrir avant d’imaginer votre rencontre.</p><Link className="text-link" to="/#animaux">Faire connaissance avec les animaux <Icon /></Link></Reveal></div></section>

    <HomeAnnouncements />

    <section className="faq-section container" id="questions" aria-labelledby="faq-title"><div><p className="eyebrow">AVANT LA RENCONTRE</p><h2 id="faq-title">Vous vous posez<br /><em>des questions ?</em></h2><p>C’est déjà un beau premier pas.<br />Voici quelques repères pour commencer.</p><PawIcon className="faq-paw" /></div><div className="faq-list">{faqs.map(([question, answer]) => <details key={question}><summary>{question}<span aria-hidden="true">+</span></summary><p>{answer}</p></details>)}</div></section>
    <section className="closing-section"><div className="container"><PawIcon /><h2>Une petite place chez vous.<br /><em>Une immense place dans leur vie.</em></h2><Link className="button button-primary" to="/#animaux">Rencontrer les animaux <Icon /></Link></div></section>
  </>
}

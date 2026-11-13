const statuses = {
  DISPONIBLE: { label: 'Disponible', cardLabel: 'À rencontrer', className: 'available', message: 'Cet animal est disponible à l’adoption.' },
  EN_SOIN: { label: 'En soins', cardLabel: 'En soins', className: 'care', message: 'Cet animal est actuellement en soins au refuge.' },
  ADOPTE: { label: 'Adopté', cardLabel: 'Adopté', className: 'adopted', message: 'Cet animal a déjà été adopté.' },
}

export function getAnimalStatus(value) {
  return statuses[value?.trim().toUpperCase()] ?? { label: 'Non renseigné', cardLabel: 'Statut à préciser', className: 'unknown', message: 'Le statut de cet animal n’est pas renseigné.' }
}

export function ageLabel(age) {
  if (!Number.isInteger(age) || age < 0) return 'Âge non renseigné'
  if (age === 0) return 'Moins d’un an'
  return `${age} ${age === 1 ? 'an' : 'ans'}`
}

export function sexLabel(sex) {
  if (!sex?.trim()) return 'Sexe non renseigné'
  const normalized = sex.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toUpperCase()
  if (normalized === 'MALE' || normalized === 'M') return 'Mâle'
  if (normalized === 'FEMELLE' || normalized === 'F') return 'Femelle'
  return sex
}

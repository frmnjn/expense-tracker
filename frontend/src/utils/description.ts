export function splitDescriptionItems(description: string): string[] | null {
  const items = description
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item !== '')

  return items.length >= 2 ? items : null
}

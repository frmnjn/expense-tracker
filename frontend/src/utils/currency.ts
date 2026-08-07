const CURRENCY_FORMAT = new Intl.NumberFormat('id-ID', {
  maximumFractionDigits: 0,
})

export function formatCurrency(value: number): string {
  return `Rp${CURRENCY_FORMAT.format(value)}`
}

const PERIOD_MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']

/** Periode dari sebuah tanggal (aturan sama seperti backend: mulai tanggal 25). */
export function periodOf(date: Date): string {
  let y = date.getFullYear()
  let m = date.getMonth()
  if (date.getDate() < 25) {
    m -= 1
    if (m < 0) {
      m = 11
      y -= 1
    }
  }
  return `${y}-${PERIOD_MONTHS[m]}-${PERIOD_MONTHS[(m + 1) % 12]}`
}

/** Parse "YYYY-MON-MON" -> LocalDate awal periode (tanggal 25). Null bila format tidak dikenal. */
export function parsePeriodStart(period: string): Date | null {
  const match = /^(\d{4})-([A-Za-z]{3})-([A-Za-z]{3})$/.exec(period.trim())
  if (!match) return null
  const month = PERIOD_MONTHS.indexOf(match[2].toUpperCase())
  if (month < 0) return null
  return new Date(Number(match[1]), month, 25)
}

/** Periode sebelumnya dari sebuah periode. Mengembalikan string yang sama bila gagal parse. */
export function previousPeriod(period: string): string {
  const start = parsePeriodStart(period)
  if (!start) return period
  return periodOf(new Date(start.getFullYear(), start.getMonth() - 1, 25))
}

/** Tanggal akhir periode (hari ke-25 bulan berikutnya, batas eksklusif). */
export function periodEnd(period: string): Date | null {
  const start = parsePeriodStart(period)
  if (!start) return null
  return new Date(start.getFullYear(), start.getMonth() + 1, 25)
}

/**
 * Jumlah hari yang sudah berjalan dalam periode (0-based, dibatasi hingga hari
 * terakhir periode). Dipakai untuk perbandingan date-to-date dengan periode
 * sebelumnya.
 */
export function elapsedDays(period: string, today: Date): number | null {
  const start = parsePeriodStart(period)
  const end = periodEnd(period)
  if (!start || !end) return null
  const total = Math.round((end.getTime() - start.getTime()) / 86_400_000)
  const elapsed = Math.floor((today.getTime() - start.getTime()) / 86_400_000)
  return Math.max(0, Math.min(elapsed, total - 1))
}

/** Periode berjalan (saat ini). */
export function currentPeriod(): string {
  return periodOf(new Date())
}

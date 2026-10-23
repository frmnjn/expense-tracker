import type { PeriodRange } from '../types/expense'

const PERIOD_MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']

function defaultPayday(year: number, month: number): Date {
  const payday = new Date(year, month, 25)
  while (payday.getDay() === 0 || payday.getDay() === 6) {
    payday.setDate(payday.getDate() - 1)
  }
  return payday
}

function parseIsoDate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return null
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

function findRange(period: string, calendar: PeriodRange[]): PeriodRange | null {
  return calendar.find((range) => range.period === period) ?? null
}

/** Periode dari sebuah tanggal (fallback bila calendar dari API belum tersedia). */
export function periodOf(date: Date): string {
  let y = date.getFullYear()
  let m = date.getMonth()
  if (date < defaultPayday(y, m)) {
    m -= 1
    if (m < 0) {
      m = 11
      y -= 1
    }
  }
  return `${y}-${PERIOD_MONTHS[m]}-${PERIOD_MONTHS[(m + 1) % 12]}`
}

/** Tanggal awal periode. Override dari backend dipakai bila tersedia. */
export function parsePeriodStart(period: string, calendar: PeriodRange[] = []): Date | null {
  const range = findRange(period, calendar)
  if (range) return parseIsoDate(range.start)
  const match = /^(\d{4})-([A-Za-z]{3})-([A-Za-z]{3})$/.exec(period.trim())
  if (!match) return null
  const month = PERIOD_MONTHS.indexOf(match[2].toUpperCase())
  if (month < 0) return null
  return defaultPayday(Number(match[1]), month)
}

/** Nama periode sebelumnya dari sebuah periode. */
export function previousPeriod(period: string): string {
  const match = /^(\d{4})-([A-Za-z]{3})-([A-Za-z]{3})$/.exec(period.trim())
  if (!match) return period
  let year = Number(match[1])
  let month = PERIOD_MONTHS.indexOf(match[2].toUpperCase())
  if (month < 0) return period
  month -= 1
  if (month < 0) {
    month = 11
    year -= 1
  }
  return `${year}-${PERIOD_MONTHS[month]}-${PERIOD_MONTHS[(month + 1) % 12]}`
}

/** Tanggal akhir periode (awal periode berikutnya, batas eksklusif). */
export function periodEnd(period: string, calendar: PeriodRange[] = []): Date | null {
  const range = findRange(period, calendar)
  if (range) return parseIsoDate(range.end)
  const start = parsePeriodStart(period)
  if (!start) return null
  return defaultPayday(start.getFullYear(), start.getMonth() + 1)
}

/**
 * Jumlah hari periode yang sudah berjalan, termasuk hari ini (1-based).
 * Hari pertama periode = 1. Dibatasi hingga panjang periode.
 */
export function elapsedDayCount(period: string, today: Date, calendar: PeriodRange[] = []): number | null {
  const start = parsePeriodStart(period, calendar)
  const end = periodEnd(period, calendar)
  if (!start || !end) return null
  if (today < start) return 0
  const total = Math.round((end.getTime() - start.getTime()) / 86_400_000)
  const elapsed = Math.floor((today.getTime() - start.getTime()) / 86_400_000) + 1
  return Math.max(1, Math.min(elapsed, total))
}

/** Periode berjalan. Gunakan currentPeriod dari API agar override ikut diperhitungkan. */
export function currentPeriod(calendar: PeriodRange[] = []): string {
  const now = new Date()
  const active = calendar.find((range) => {
    const start = parseIsoDate(range.start)
    const end = parseIsoDate(range.end)
    return start !== null && end !== null && now >= start && now < end
  })
  return active?.period ?? periodOf(now)
}

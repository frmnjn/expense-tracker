import { formatCurrency } from './currency'
import type { BudgetSummary, Expense } from '../types/expense'

export interface BudgetHealthInfo {
  name: string
  balance: number
  alertThreshold: number
}

export type InsightTone = 'positive' | 'warning' | 'danger' | 'info'

export interface Insight {
  text: string
  tone: InsightTone
}

export interface InsightsInput {
  selectedTotal: number
  prevTotal: number | null
  byBudget: BudgetSummary[]
  budgets: BudgetHealthInfo[]
  expenses: Expense[]
  /** Label perbandingan; default "dibanding periode sebelumnya". */
  comparisonNote?: string
}

const INDONESIAN_DATE = new Intl.DateTimeFormat('id-ID', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
})

/** Format tanggal ke contoh: "Sabtu, 5 September 2026". */
function formatIndonesianDate(date: Date): string {
  return INDONESIAN_DATE.format(date)
}

/** Expense dengan nominal positif terbesar; null bila tidak ada. */
function largestExpense(expenses: Expense[]): Expense | null {
  return expenses.reduce<Expense | null>(
    (max, e) => (e.amount > 0 && e.amount > (max?.amount ?? 0) ? e : max),
    null,
  )
}

/** Tanggal dengan total pengeluaran (netto) terbesar; null bila tidak ada yang positif. */
function busiestDay(expenses: Expense[]): { date: Date; total: number } | null {
  const totals = new Map<string, number>()
  for (const e of expenses) {
    const date = new Date(e.dateTime.replace(' ', 'T'))
    if (Number.isNaN(date.getTime())) continue
    const key = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
    totals.set(key, (totals.get(key) ?? 0) + e.amount)
  }
  let best: { date: Date; total: number } | null = null
  for (const e of expenses) {
    const date = new Date(e.dateTime.replace(' ', 'T'))
    if (Number.isNaN(date.getTime())) continue
    const key = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
    const total = totals.get(key) ?? 0
    if (total > 0 && (!best || total > best.total)) {
      best = { date, total }
    }
  }
  return best
}

/**
 * Menghasilkan insight deterministik dari data aktual (semua yang relevan).
 * Semua kalimat faktual dan tanpa penilaian subjektif.
 */
export function buildInsights(input: InsightsInput): Insight[] {
  const { selectedTotal, prevTotal, byBudget, budgets, expenses } = input
  const note = input.comparisonNote ?? 'dibanding periode sebelumnya'

  if (selectedTotal <= 0) {
    return [{ text: 'Belum ada pengeluaran di periode ini.', tone: 'info' }]
  }

  const insights: Insight[] = []

  if (prevTotal !== null && prevTotal > 0 && selectedTotal !== prevTotal) {
    const pct = Math.abs(Math.round(((selectedTotal - prevTotal) / prevTotal) * 1000) / 10)
    if (selectedTotal < prevTotal) {
      insights.push({ text: `Pengeluaran turun ${pct}% ${note}.`, tone: 'positive' })
    } else {
      insights.push({ text: `Pengeluaran naik ${pct}% ${note}.`, tone: 'warning' })
    }
  }

  if (byBudget.length > 0) {
    const top = byBudget[0]
    const share = Math.round((top.amount / selectedTotal) * 100)
    insights.push({
      text: `${top.budget} menjadi pengeluaran terbesar dengan ${share}%.`,
      tone: 'info',
    })
  }

  const overBudget = budgets
    .filter((b) => b.balance < 0)
    .sort((a, b) => a.balance - b.balance)
  const approaching = budgets
    .filter((b) => b.balance >= 0 && b.alertThreshold > 0 && b.balance < b.alertThreshold)
    .sort((a, b) => a.balance - b.balance)

  if (overBudget.length > 1) {
    const total = overBudget.reduce((s, b) => s + Math.abs(b.balance), 0)
    insights.push({
      text: `Periode ini total pengeluaran melebihi budget ${formatCurrency(total)} dari ${overBudget.length} budget.`,
      tone: 'danger',
    })
  }
  for (const b of overBudget) {
    insights.push({
      text: `${b.name} sudah melebihi budget (kelebihan ${formatCurrency(Math.abs(b.balance))}).`,
      tone: 'danger',
    })
  }
  for (const b of approaching) {
    insights.push({
      text: `${b.name} mendekati batas budget (tersisa ${formatCurrency(b.balance)}, ambang ${formatCurrency(b.alertThreshold)}).`,
      tone: 'warning',
    })
  }

  const largest = largestExpense(expenses)
  if (largest) {
    const date = new Date(largest.dateTime.replace(' ', 'T'))
    const dateText = Number.isNaN(date.getTime()) ? '' : ` pada ${formatIndonesianDate(date)}`
    insights.push({
      text: `${largest.name} (${formatCurrency(largest.amount)})${dateText} jadi pengeluaran terbesar periode ini.`,
      tone: 'info',
    })
  }

  const busiest = busiestDay(expenses)
  if (busiest) {
    insights.push({
      text: `Pengeluaran terbanyak pada ${formatIndonesianDate(busiest.date)} (${formatCurrency(busiest.total)}).`,
      tone: 'info',
    })
  }

  return insights
}

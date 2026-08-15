import { formatCurrency } from './currency'
import type { BudgetSummary } from '../types/expense'

export interface BudgetHealthInfo {
  name: string
  balance: number
  alertThreshold: number
}

export type InsightTone = 'positive' | 'warning' | 'info'

export interface Insight {
  text: string
  tone: InsightTone
}

export interface InsightsInput {
  selectedTotal: number
  prevTotal: number | null
  byBudget: BudgetSummary[]
  budgets: BudgetHealthInfo[]
  /** Label perbandingan; default "dibanding periode sebelumnya". */
  comparisonNote?: string
}

const CONCENTRATION_RATIO = 0.5

/**
 * Menghasilkan maksimal 3 insight deterministik dari data aktual.
 * Semua kalimat faktual dan tanpa penilaian subjektif.
 */
export function buildInsights(input: InsightsInput): Insight[] {
  const { selectedTotal, prevTotal, byBudget, budgets } = input
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

  if (insights.length < 3 && byBudget.length > 0) {
    const top = byBudget[0]
    const share = Math.round((top.amount / selectedTotal) * 100)
    insights.push({
      text: `${top.budget} menjadi pengeluaran terbesar dengan ${share}%.`,
      tone: 'info',
    })
  }

  if (insights.length < 3) {
    const urgent = budgets
      .filter((b) => b.alertThreshold > 0 && b.balance < b.alertThreshold)
      .sort((a, b) => a.balance - b.balance)[0]
    if (urgent) {
      insights.push({
        text: `${urgent.name} mendekati batas budget (tersisa ${formatCurrency(urgent.balance)}, ambang ${formatCurrency(urgent.alertThreshold)}).`,
        tone: 'warning',
      })
    }
  }

  if (insights.length < 3 && byBudget.length > 0) {
    const top = byBudget[0]
    if (top.amount / selectedTotal >= CONCENTRATION_RATIO) {
      insights.push({
        text: `${Math.round((top.amount / selectedTotal) * 100)}% pengeluaran terkonsentrasi di ${top.budget}.`,
        tone: 'info',
      })
    }
  }

  return insights.slice(0, 3)
}

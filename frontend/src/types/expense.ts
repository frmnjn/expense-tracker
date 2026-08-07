export interface ExpenseRequest {
  dateTime: string
  name: string
  budget: string
  amount: number
  description?: string
}

export interface BudgetOption {
  name: string
  balance: number
}

export interface OptionsResponse {
  budgets: BudgetOption[]
}

export interface Expense {
  id: string
  dateTime: string
  name: string
  budget: string
  amount: number
  description?: string
}

export interface PeriodsResponse {
  periods: string[]
}

export interface ExpensesResponse {
  expenses: Expense[]
}

export interface BudgetSummary {
  budget: string
  amount: number
}

export interface SummaryResponse {
  period: string
  total: number
  count: number
  byBudget: BudgetSummary[]
}

export interface TopUp {
  id: string
  dateTime: string
  budget: string
  amount: number
  description?: string
}

export interface TopUpsResponse {
  topUps: TopUp[]
}

export interface TopUpRequest {
  dateTime?: string
  budget: string
  amount: number
  description?: string
}

export interface TrendPoint {
  period: string
  total: number
  count: number
}

export interface TrendResponse {
  periods: TrendPoint[]
}

export interface ApiResponse<T> {
  success: boolean
  message?: string
  data?: T
}

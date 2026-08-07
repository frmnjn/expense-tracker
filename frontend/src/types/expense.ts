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

export interface ApiResponse<T> {
  success: boolean
  message?: string
  data?: T
}

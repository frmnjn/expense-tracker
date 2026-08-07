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

export interface ApiResponse<T> {
  success: boolean
  message?: string
  data?: T
}

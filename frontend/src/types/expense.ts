export interface ExpenseRequest {
  dateTime: string
  name: string
  budget: string
  bank: string
  amount: number
  description?: string
}

export interface OptionsResponse {
  budgets: string[]
  banks: string[]
}

export interface ApiResponse<T> {
  success: boolean
  message?: string
  data?: T
}

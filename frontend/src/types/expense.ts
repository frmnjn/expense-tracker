export interface ExpenseRequest {
  dateTime: string
  name: string
  budget: string
  amount: number
  description?: string
}

export interface OptionsResponse {
  budgets: string[]
}

export interface ApiResponse<T> {
  success: boolean
  message?: string
  data?: T
}

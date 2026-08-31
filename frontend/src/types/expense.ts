export interface ExpenseRequest {
  dateTime: string
  name: string
  budget: string
  amount: number
  description?: string
  invoiceId?: string
}

export interface BudgetOption {
  name: string
  balance: number
  alertThreshold: number
  description?: string
}

export interface BudgetCreateRequest {
  name: string
  balance?: number
  alertThreshold?: number
  description?: string
}

export interface BudgetUpdateRequest {
  name: string
  balance?: number
  alertThreshold?: number
  description?: string
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
  hasPhoto: boolean
  photoType?: 'image' | 'pdf' | string
  photoName?: string
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
  count: number
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

export interface Invoice {
  id: string
  createdAt: string
  status: 'ANALYZING' | 'TO_REVIEW' | 'SUBMITTED' | 'ERROR' | 'NOT_INVOICE' | string
  type: 'image' | 'pdf'
  name?: string
}

export interface InvoicesResponse {
  invoices: Invoice[]
}

export interface AiInvoiceItem {
  name: string
  amount: number
  suggestedBudget?: string
}

export interface AiAnalysis {
  storeName?: string
  total?: number
  dateTime?: string
  items: AiInvoiceItem[]
}

export interface InvoiceDetail {
  id: string
  type: 'image' | 'pdf'
  status: string
  errorMessage?: string
  name?: string
  analysis?: AiAnalysis
}

export interface BatchExpenseItem {
  name: string
  budget: string
  amount: number
  description?: string
}

export interface BatchExpenseRequest {
  dateTime: string
  invoiceId?: string
  groups: BatchExpenseItem[]
}

export interface ApiResponse<T> {
  success: boolean
  message?: string
  data?: T
}

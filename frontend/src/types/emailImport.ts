export interface EmailImport {
  id: string
  sender: string
  subject: string
  receivedAt: string
  transactionAt?: string
  merchant?: string
  amount?: number
  description?: string
  suggestedBudget?: string
  suggestedCategory?: string
  parseMethod: string
  status: 'PENDING_REVIEW' | 'IMPORTED' | 'DISCARDED' | 'FAILED' | 'SCANNED' | string
  errorMessage?: string
  expenseId?: string
}

export interface EmailImportsResponse {
  imports: EmailImport[]
}

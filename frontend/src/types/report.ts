export interface PeriodReport {
  period: string
  fileName: string
  sizeBytes: number
  generatedAt: string
}

export interface PeriodReportsResponse {
  reports: PeriodReport[]
}

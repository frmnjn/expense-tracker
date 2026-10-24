import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type {
  CategoriesResponse,
  Expense,
  ExpensesResponse,
  InvoiceDetail,
  InvoicesResponse,
  OptionsResponse,
  PeriodsResponse,
  SummaryResponse,
  TopUpsResponse,
  TrendResponse,
  VoiceNoteDetail,
  VoiceNotesResponse,
} from '../types/expense'
import type { EmailImportsResponse } from '../types/emailImport'
import type { PeriodReportsResponse } from '../types/report'

interface DemoData {
  currentPeriod: string
  periods: string[]
  calendar: PeriodsResponse['calendar']
  options: OptionsResponse
  expensesByPeriod: Record<string, Expense[]>
  summaryByPeriod: Record<string, SummaryResponse>
  topUps: TopUpsResponse['topUps']
  invoices: Array<InvoicesResponse['invoices'][number] & { period: string }>
  invoiceDetails: Record<string, InvoiceDetail>
  voiceNotes: Array<VoiceNotesResponse['voiceNotes'][number] & { period: string }>
  voiceNoteDetails: Record<string, VoiceNoteDetail>
  emailImports: EmailImportsResponse['imports']
}

const BASE = import.meta.env.BASE_URL
const READ_ONLY_MESSAGE = 'Ini cuma demo — data nggak bisa diubah.'

let dataPromise: Promise<DemoData> | null = null

function fetchJson<T>(path: string): Promise<T> {
  return fetch(`${BASE}${path}`, { cache: 'no-store' }).then((response) => {
    if (!response.ok) {
      throw new Error(`Gagal memuat data demo (${response.status})`)
    }
    return response.json() as Promise<T>
  })
}

function loadData(): Promise<DemoData> {
  if (!dataPromise) {
    dataPromise = fetchJson<DemoData>('demo/data.json').catch((error) => {
      dataPromise = null
      throw error
    })
  }
  return dataPromise
}

function loadReports(): Promise<PeriodReportsResponse> {
  return fetchJson<PeriodReportsResponse>('demo/reports.json').catch(() => ({ reports: [] }))
}

function paramsOf(config: InternalAxiosRequestConfig): URLSearchParams {
  const params = new URLSearchParams()
  const query = (config.url ?? '').split('?')[1]
  if (query) {
    new URLSearchParams(query).forEach((value, key) => params.set(key, value))
  }
  const configParams = config.params as Record<string, unknown> | undefined
  if (configParams) {
    for (const [key, value] of Object.entries(configParams)) {
      if (value !== undefined && value !== null && value !== '') {
        params.set(key, String(value))
      }
    }
  }
  return params
}

function response<T>(config: InternalAxiosRequestConfig, data: T): AxiosResponse<{ success: boolean; data: T }> {
  return {
    data: { success: true, data },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }
}

function failure(config: InternalAxiosRequestConfig, status: number, message: string): Promise<never> {
  return Promise.reject({
    name: 'AxiosError',
    isAxiosError: true,
    config,
    message,
    response: {
      data: { success: false, message },
      status,
      statusText: status === 403 ? 'Forbidden' : 'Not Found',
      headers: {},
      config,
    },
  })
}

function findExpense(data: DemoData, id: string): Expense | null {
  for (const expenses of Object.values(data.expensesByPeriod)) {
    const found = expenses.find((expense) => expense.id === id)
    if (found) return found
  }
  return null
}

function findBudgetCategories(data: DemoData, budgetName: string): CategoriesResponse {
  const budget = data.options.budgets.find((item) => item.name === budgetName)
  return { categories: budget?.categories ?? [] }
}

export async function demoAdapter(config: InternalAxiosRequestConfig): Promise<AxiosResponse> {
  const method = (config.method ?? 'get').toLowerCase()
  if (method !== 'get') {
    return failure(config, 403, READ_ONLY_MESSAGE)
  }

  const path = (config.url ?? '').split('?')[0]
  const params = paramsOf(config)

  if (path === '/health') {
    return response(config, {})
  }
  if (path === '/reports') {
    return response(config, await loadReports())
  }

  const data = await loadData()

  if (path === '/options') {
    return response(config, data.options)
  }
  if (path === '/periods') {
    return response(config, {
      periods: data.periods,
      currentPeriod: data.currentPeriod,
      calendar: data.calendar,
    } satisfies PeriodsResponse)
  }
  if (path === '/expenses') {
    const period = params.get('period') ?? ''
    return response(config, { expenses: data.expensesByPeriod[period] ?? [] } satisfies ExpensesResponse)
  }
  if (path === '/summary') {
    const period = params.get('period') ?? ''
    return response(
      config,
      data.summaryByPeriod[period] ?? { period, total: 0, count: 0, byBudget: [] },
    )
  }
  if (path === '/trend') {
    const months = Math.max(1, Number(params.get('months') ?? 3))
    const periods = data.periods.slice(0, months).reverse()
    return response(config, {
      periods: periods.map((period) => ({
        period,
        total: data.summaryByPeriod[period]?.total ?? 0,
        count: data.summaryByPeriod[period]?.count ?? 0,
      })),
    } satisfies TrendResponse)
  }
  if (path === '/topups') {
    return response(config, { topUps: data.topUps } satisfies TopUpsResponse)
  }
  if (path === '/categories') {
    return response(config, findBudgetCategories(data, params.get('budget') ?? ''))
  }
  if (path === '/invoices') {
    if (params.get('scan') === 'true') {
      const period = params.get('period')
      const invoices = period
        ? data.invoices.filter((invoice) => invoice.period === period)
        : data.invoices
      return response(config, { invoices } satisfies InvoicesResponse)
    }
    const date = params.get('date')
    if (!date) {
      return failure(config, 400, 'Date is required')
    }
    const day = date.slice(0, 10)
    const period = data.calendar.find((range) => day >= range.start && day < range.end)?.period
    const invoices = data.invoices.filter((invoice) => invoice.period === period)
    return response(config, { invoices } satisfies InvoicesResponse)
  }
  if (path === '/voice-notes') {
    const period = params.get('period')
    const voiceNotes = period
      ? data.voiceNotes.filter((note) => note.period === period)
      : data.voiceNotes
    return response(config, { voiceNotes } satisfies VoiceNotesResponse)
  }
  if (path === '/email-imports') {
    const status = params.get('status')
    const imports =
      status && status !== 'ALL'
        ? data.emailImports.filter((item) => item.status === status)
        : data.emailImports
    return response(config, { imports } satisfies EmailImportsResponse)
  }
  if (path === '/email-imports/poll/status') {
    return response(config, { running: false })
  }

  const invoiceMatch = /^\/invoices\/([^/]+)$/.exec(path)
  if (invoiceMatch) {
    const id = decodeURIComponent(invoiceMatch[1])
    const detail = data.invoiceDetails[id]
    return detail
      ? response(config, detail)
      : response(config, { id, type: 'image', status: 'ERROR' } satisfies InvoiceDetail)
  }

  const voiceNoteMatch = /^\/voice-notes\/([^/]+)$/.exec(path)
  if (voiceNoteMatch) {
    const id = decodeURIComponent(voiceNoteMatch[1])
    const detail = data.voiceNoteDetails[id]
    return detail
      ? response(config, detail)
      : response(config, { id, status: 'ERROR' } satisfies VoiceNoteDetail)
  }

  const expenseMatch = /^\/expenses\/([^/]+)$/.exec(path)
  if (expenseMatch) {
    return response(config, findExpense(data, decodeURIComponent(expenseMatch[1])))
  }

  return failure(config, 404, 'Endpoint demo tidak ditemukan')
}

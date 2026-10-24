export const isDemoMode = import.meta.env.VITE_DEMO_MODE === 'true'

export function demoAssetUrl(fileName: string): string {
  return `${import.meta.env.BASE_URL}demo/assets/${fileName}`
}

export function demoReportUrl(fileName: string): string {
  return `${import.meta.env.BASE_URL}demo/reports/${fileName}`
}

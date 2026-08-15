const ACCESS_KEY = 'expense-access-code'

export function getAccessCode(): string {
  try {
    return localStorage.getItem(ACCESS_KEY) ?? ''
  } catch {
    return ''
  }
}

export function setAccessCode(code: string) {
  try {
    localStorage.setItem(ACCESS_KEY, code)
  } catch {
    // abaikan bila storage tidak tersedia
  }
}

export function clearAccessCode() {
  try {
    localStorage.removeItem(ACCESS_KEY)
  } catch {
    // abaikan
  }
}

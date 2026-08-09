function randomHex(bytes: number): string {
  const buf = new Uint8Array(bytes)
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(buf)
  } else {
    for (let i = 0; i < bytes; i++) {
      buf[i] = Math.floor(Math.random() * 256)
    }
  }
  return Array.from(buf, (x) => x.toString(16).padStart(2, '0')).join('')
}

export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  const hex = randomHex(16)
  // set UUID version 4 dan variant RFC4122 (fallback tanpa crypto.randomUUID,
  // karena randomUUID hanya tersedia di secure context / localhost)
  const h = hex.slice(0, 12) + '4' + hex.slice(13, 16) + '8' + hex.slice(17)
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`
}

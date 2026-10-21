import { chromium } from 'playwright'
import fs from 'node:fs/promises'

const base = (process.env.DEMO_URL || 'https://frmnjn.my.id/expense-tracker-demo/').replace(/\/?$/, '/')
const pin = process.env.DEMO_PIN || '2026'
const outDir = process.env.OUT_DIR || '/out'
const pages = [
  ['dashboard', 'dashboard'],
  ['catat', 'catat'],
  ['riwayat', 'riwayat'],
  ['scan', 'scan'],
  ['voice', 'voice'],
  ['inbox', 'inbox'],
]

await fs.mkdir(outDir, { recursive: true })

const browser = await chromium.launch({ headless: true })
const errors = []

async function capture(viewport, suffix, mobile) {
  const context = await browser.newContext({
    viewport,
    deviceScaleFactor: 2,
    colorScheme: 'dark',
    locale: 'id-ID',
    isMobile: mobile,
    hasTouch: mobile,
    serviceWorkers: 'block',
  })
  await context.addInitScript((code) => {
    localStorage.setItem('expense-access-code', code)
  }, pin)
  const page = await context.newPage()
  page.on('pageerror', (error) => errors.push(`${suffix}: ${error.message}`))

  for (const [name, path] of pages) {
    await page.goto(`${base}${path}`, { waitUntil: 'networkidle', timeout: 60000 })
    await page.waitForTimeout(1200)
    await page.screenshot({
      path: `${outDir}/${name}-${suffix}.jpg`,
      type: 'jpeg',
      quality: 84,
      fullPage: ['dashboard', 'catat', 'inbox', 'scan'].includes(name),
    })
    console.log(`captured ${name}-${suffix}`)
  }
  await context.close()

  const lockContext = await browser.newContext({
    viewport,
    deviceScaleFactor: 2,
    colorScheme: 'dark',
    locale: 'id-ID',
    isMobile: mobile,
    hasTouch: mobile,
    serviceWorkers: 'block',
  })
  const lockPage = await lockContext.newPage()
  await lockPage.goto(`${base}lock`, { waitUntil: 'networkidle', timeout: 60000 })
  await lockPage.waitForTimeout(700)
  await lockPage.screenshot({ path: `${outDir}/lock-${suffix}.jpg`, type: 'jpeg', quality: 84 })
  console.log(`captured lock-${suffix}`)
  await lockContext.close()
}

await capture({ width: 1440, height: 900 }, 'desktop', false)
await capture({ width: 390, height: 844 }, 'mobile', true)
await browser.close()

if (errors.length) {
  console.error(errors.join('\n'))
  process.exit(1)
}

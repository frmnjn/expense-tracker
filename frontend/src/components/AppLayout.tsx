import { Suspense, useEffect, useRef } from 'react'
import { AppShell, Box, Center, Group, Loader, Stack, Text, UnstyledButton } from '@mantine/core'
import {
  IconHome,
  IconPlus,
  IconReceipt,
  IconMail,
  IconHistory,
} from '@tabler/icons-react'
import { NavLink, Outlet } from 'react-router-dom'
import ColorSchemeToggle from './ColorSchemeToggle'
import InstallButton from './InstallButton'
import { OfflineBanner } from './OfflineBanner'
import { PullToRefresh } from './PullToRefresh'
import { useScrollMemory } from '../hooks/useScrollMemory'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: IconHome },
  { to: '/scan', label: 'Scan', icon: IconReceipt },
  { to: '/catat', label: 'Catat', icon: IconPlus },
  { to: '/inbox', label: 'Inbox', icon: IconMail },
  { to: '/riwayat', label: 'Riwayat', icon: IconHistory },
]

function AppLayout() {
  const mainRef = useRef<HTMLDivElement>(null)
  useScrollMemory(mainRef)

  // Saat keyboard HP muncul dan menyusutkan viewport, field yang difokus bisa
  // berakhir di bawah keyboard. Setelah animasi keyboard selesai, geser field
  // ke tengah area yang terlihat agar user tidak perlu scroll manual.
  useEffect(() => {
    const onFocusIn = (event: FocusEvent) => {
      const target = event.target as HTMLElement | null
      if (!target || !target.matches('input, textarea, select, [contenteditable="true"]')) return
      window.setTimeout(() => {
        target.scrollIntoView({ block: 'center', behavior: 'smooth' })
      }, 300)
    }
    document.addEventListener('focusin', onFocusIn)
    return () => document.removeEventListener('focusin', onFocusIn)
  }, [])

  return (
    <AppShell
      header={{ height: 64 }}
      navbar={{ width: 240, breakpoint: 'sm' }}
      padding={{ base: 'md', sm: 'xl' }}
      className="app-shell"
    >
      <AppShell.Header className="app-header">
        <Group h="100%" px={{ base: 'md', sm: 'xl' }} justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap" style={{ minWidth: 0 }}>
            <Box className="brand-mark">Rp</Box>
            <Stack gap={0} visibleFrom="sm">
              <Text fw={800} lh={1.1}>Expense Tracker</Text>
              <Text size="xs" c="dimmed">Keuangan pribadi</Text>
            </Stack>
            <Text fw={800} hiddenFrom="sm" truncate style={{ minWidth: 0 }}>Expense Tracker</Text>
          </Group>
          <Group gap="xs" wrap="nowrap" style={{ flexShrink: 0 }}>
            <InstallButton />
            <ColorSchemeToggle />
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="md" className="app-navbar">
        <Stack gap={6}>
          <Text size="xs" fw={700} c="dimmed" tt="uppercase" px="sm" mb={4} visibleFrom="sm">
            Menu
          </Text>
          {navItems.map((item) => {
            const Icon = item.icon
            return (
              <UnstyledButton key={item.to} component={NavLink} to={item.to} className="nav-item">
                <span className="nav-icon">
                  <Icon size={18} stroke={1.8} />
                </span>
                <span>{item.label}</span>
              </UnstyledButton>
            )
          })}
        </Stack>

        <Box mt="auto" px="sm" pb="sm" visibleFrom="sm">
          <Text size="xs" c="dimmed">Kelola pengeluaran tanpa ribet.</Text>
        </Box>
      </AppShell.Navbar>

      <AppShell.Main ref={mainRef}>
        <Box className="page-shell">
          <OfflineBanner />
          <Suspense
            fallback={
              <Center py="xl">
                <Loader />
              </Center>
            }
          >
            <Outlet />
          </Suspense>
        </Box>
        <PullToRefresh scrollRef={mainRef} />
      </AppShell.Main>
    </AppShell>
  )
}

export default AppLayout

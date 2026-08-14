import { AppShell, Box, Group, Stack, Text, UnstyledButton } from '@mantine/core'
import { NavLink, Outlet } from 'react-router-dom'
import ColorSchemeToggle from './ColorSchemeToggle'
import InstallButton from './InstallButton'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: '⌂' },
  { to: '/catat', label: 'Catat Pengeluaran', icon: '+' },
  { to: '/scan', label: 'Scan Struk', icon: '◉' },
  { to: '/riwayat', label: 'Riwayat', icon: '≡' },
]

function AppLayout() {
  return (
    <AppShell
      header={{ height: 64 }}
      navbar={{ width: 240, breakpoint: 'sm' }}
      padding={{ base: 'md', sm: 'xl' }}
      className="app-shell"
    >
      <AppShell.Header className="app-header">
        <Group h="100%" px={{ base: 'md', sm: 'xl' }} justify="space-between">
          <Group gap="sm">
            <Box className="brand-mark">Rp</Box>
            <Stack gap={0} visibleFrom="sm">
              <Text fw={800} lh={1.1}>Expense Tracker</Text>
              <Text size="xs" c="dimmed">Personal finance</Text>
            </Stack>
            <Text fw={800} hiddenFrom="sm">Expense Tracker</Text>
          </Group>
          <Group gap="xs">
            <InstallButton />
            <ColorSchemeToggle />
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="md" className="app-navbar">
        <Stack gap={6}>
          <Text size="xs" fw={700} c="dimmed" tt="uppercase" px="sm" mb={4}>
            Menu
          </Text>
          {navItems.map((item) => (
            <UnstyledButton key={item.to} component={NavLink} to={item.to} className="nav-item">
              <span className="nav-icon">{item.icon}</span>
              <span>{item.label}</span>
            </UnstyledButton>
          ))}
        </Stack>

        <Box mt="auto" px="sm" pb="sm" visibleFrom="sm">
          <Text size="xs" c="dimmed">Kelola pengeluaran tanpa ribet.</Text>
        </Box>
      </AppShell.Navbar>

      <AppShell.Main>
        <Box className="page-shell">
          <Outlet />
        </Box>
      </AppShell.Main>
    </AppShell>
  )
}

export default AppLayout

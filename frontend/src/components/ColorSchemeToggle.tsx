import { ActionIcon, Tooltip, useMantineColorScheme } from '@mantine/core'

function ColorSchemeToggle() {
  const { colorScheme, toggleColorScheme } = useMantineColorScheme()
  const dark = colorScheme === 'dark'

  return (
    <Tooltip label={dark ? 'Mode terang' : 'Mode gelap'}>
      <ActionIcon variant="subtle" size="lg" onClick={toggleColorScheme} aria-label="Toggle color scheme">
        {dark ? <span>☀️</span> : <span>🌙</span>}
      </ActionIcon>
    </Tooltip>
  )
}

export default ColorSchemeToggle

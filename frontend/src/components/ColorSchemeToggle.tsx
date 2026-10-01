import { ActionIcon, Tooltip, useMantineColorScheme } from '@mantine/core'
import { IconMoon, IconSun } from '@tabler/icons-react'

function ColorSchemeToggle() {
  const { colorScheme, toggleColorScheme } = useMantineColorScheme()
  const dark = colorScheme === 'dark'

  return (
    <Tooltip label={dark ? 'Mode terang' : 'Mode gelap'}>
      <ActionIcon variant="subtle" size="lg" onClick={toggleColorScheme} aria-label="Ubah mode warna">
        {dark ? <IconSun size={20} /> : <IconMoon size={20} />}
      </ActionIcon>
    </Tooltip>
  )
}

export default ColorSchemeToggle

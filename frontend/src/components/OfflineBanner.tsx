import { useEffect, useState } from 'react'
import { Group, Paper, Text } from '@mantine/core'
import { IconWifiOff } from '@tabler/icons-react'

export function OfflineBanner() {
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine))

  useEffect(() => {
    const goOnline = () => setOnline(true)
    const goOffline = () => setOnline(false)
    window.addEventListener('online', goOnline)
    window.addEventListener('offline', goOffline)
    return () => {
      window.removeEventListener('online', goOnline)
      window.removeEventListener('offline', goOffline)
    }
  }, [])

  if (online) return null

  return (
    <Paper withBorder p="xs" radius="md" mb="md" bg="var(--mantine-color-orange-light)">
      <Group gap="xs" wrap="nowrap" justify="center">
        <IconWifiOff size={16} />
        <Text size="sm">Tidak ada koneksi internet. Perubahan tidak akan tersimpan.</Text>
      </Group>
    </Paper>
  )
}

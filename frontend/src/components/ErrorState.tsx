import { Button, Stack, Text } from '@mantine/core'
import { IconAlertTriangle } from '@tabler/icons-react'
import { useQueryClient } from '@tanstack/react-query'

export function ErrorState({
  message = 'Gagal memuat data.',
  onRetry,
}: {
  message?: string
  onRetry?: () => void
}) {
  const queryClient = useQueryClient()

  return (
    <Stack align="center" gap="xs" py="md">
      <IconAlertTriangle size={24} aria-hidden />
      <Text size="sm" c="red" ta="center">
        {message}
      </Text>
      <Button
        size="compact-sm"
        variant="light"
        onClick={onRetry ?? (() => queryClient.invalidateQueries())}
      >
        Coba lagi
      </Button>
    </Stack>
  )
}

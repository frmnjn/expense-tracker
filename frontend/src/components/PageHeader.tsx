import type { ReactNode } from 'react'
import { Group, Stack, Text, Title } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'

export function PageHeader({
  eyebrow,
  title,
  subtitle,
  right,
  titleSize = 'clamp(1.65rem, 5vw, 2.1rem)',
}: {
  eyebrow: string
  title: string
  subtitle?: string
  right?: ReactNode
  titleSize?: string
}) {
  const isMobile = useMediaQuery('(max-width: 48em)')

  const heading = (
    <div>
      <Text size="sm" c="blue" fw={700} mb={4} tt="uppercase">
        {eyebrow}
      </Text>
      <Title order={1} size={titleSize}>
        {title}
      </Title>
      {subtitle && (
        <Text c="dimmed" mt={5}>
          {subtitle}
        </Text>
      )}
    </div>
  )

  // Di mobile kontrol (mis. Select periode) ditaruh di bawah judul full-width
  // agar tidak terpotong; di desktop tetap sebaris di kanan.
  if (isMobile) {
    return (
      <Stack gap="sm">
        {heading}
        {right}
      </Stack>
    )
  }

  return (
    <Group justify="space-between" align="flex-end" wrap="nowrap">
      {heading}
      {right}
    </Group>
  )
}

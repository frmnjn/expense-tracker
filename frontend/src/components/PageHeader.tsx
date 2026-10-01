import type { ReactNode } from 'react'
import { Group, Text, Title } from '@mantine/core'

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
  return (
    <Group justify="space-between" align="flex-end" wrap="nowrap">
      <div>
        <Text size="sm" c="brand" fw={700} mb={4} tt="uppercase">
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
      {right}
    </Group>
  )
}

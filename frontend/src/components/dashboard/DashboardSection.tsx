import type { ReactNode } from 'react'
import { Divider, Group, Stack, Text } from '@mantine/core'

export function DashboardSection({
  title,
  subtitle,
  action,
  children,
}: {
  title: string
  subtitle?: string
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <section>
      <Group justify="space-between" align="baseline" mb="xs">
        <div>
          <Text fw={700} size="md">
            {title}
          </Text>
          {subtitle && (
            <Text size="xs" c="dimmed" mt={1}>
              {subtitle}
            </Text>
          )}
        </div>
        {action}
      </Group>
      <Divider mb="md" />
      <Stack gap="sm">{children}</Stack>
    </section>
  )
}

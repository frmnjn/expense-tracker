import { Group, Skeleton, Stack, Text } from '@mantine/core'
import { IconAlertOctagon, IconAlertTriangle, IconBulb } from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'
import type { Insight } from '../../utils/insights'
import { DashboardSection } from './DashboardSection'

function iconFor(tone: Insight['tone']): { Icon: Icon; color: string } {
  switch (tone) {
    case 'positive':
      return { Icon: IconBulb, color: 'teal' }
    case 'warning':
      return { Icon: IconAlertTriangle, color: 'orange' }
    case 'danger':
      return { Icon: IconAlertOctagon, color: 'red' }
    default:
      return { Icon: IconBulb, color: 'brand' }
  }
}

export function FinancialInsights({
  insights,
  isLoading,
  isError,
  hasPeriod,
}: {
  insights: Insight[]
  isLoading: boolean
  isError: boolean
  hasPeriod: boolean
}) {
  return (
    <DashboardSection title="Wawasan" subtitle="Hal penting yang perlu kamu tahu">
      {!hasPeriod ? (
        <Text size="sm" c="dimmed" py="sm">
          Pilih periode untuk melihat insight.
        </Text>
      ) : isLoading ? (
        <Stack gap="sm">
          <Skeleton h={20} />
          <Skeleton h={20} />
        </Stack>
      ) : isError ? (
        <Text size="sm" c="red">
          Insight tidak dapat dihitung.
        </Text>
      ) : insights.length === 0 ? (
        <Text size="sm" c="dimmed" py="sm">
          Tidak ada insight untuk ditampilkan.
        </Text>
      ) : (
        <Stack gap="sm">
          {insights.map((insight, i) => {
            const meta = iconFor(insight.tone)
            const ToneIcon = meta.Icon
            return (
              <Group key={i} gap="sm" align="flex-start" wrap="nowrap">
                <Text c={meta.color} aria-hidden style={{ display: 'flex' }}>
                  <ToneIcon size={18} />
                </Text>
                <Text size="sm">{insight.text}</Text>
              </Group>
            )
          })}
        </Stack>
      )}
    </DashboardSection>
  )
}

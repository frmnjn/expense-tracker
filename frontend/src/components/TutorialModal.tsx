import { Button, Group, Modal, Progress, Stack, Text, ThemeIcon, Title } from '@mantine/core'
import { IconBook2, IconCheck, IconChevronLeft, IconChevronRight } from '@tabler/icons-react'
import type { TutorialStep } from '../utils/tutorialSteps'

export function TutorialModal({
  opened,
  label,
  steps,
  stepIndex,
  onClose,
  onNext,
  onBack,
}: {
  opened: boolean
  label: string
  steps: TutorialStep[]
  stepIndex: number
  onClose: () => void
  onNext: () => void
  onBack: () => void
}) {
  const step = steps[stepIndex]

  if (!step) return null

  const isLast = stepIndex === steps.length - 1

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={`Panduan · ${label}`}
      centered
      size="lg"
      radius="lg"
      closeOnClickOutside={false}
    >
      <Stack gap="lg">
        <Stack gap={6}>
          <Progress value={((stepIndex + 1) / steps.length) * 100} size="sm" radius="xl" />
          <Text size="xs" c="dimmed" fw={600}>
            {stepIndex + 1} dari {steps.length}
          </Text>
        </Stack>

        <Stack gap="xs">
          <Group gap="sm" wrap="nowrap" align="flex-start">
            <ThemeIcon variant="light" size="lg" radius="md">
              <IconBook2 size={20} />
            </ThemeIcon>
            <Title order={3} size="h4">
              {step.title}
            </Title>
          </Group>
          <Text c="dimmed" style={{ lineHeight: 1.6 }}>
            {step.body}
          </Text>
        </Stack>

        <Group justify="space-between" align="center" mt="md" gap="xs">
          <Button variant="subtle" color="gray" onClick={onClose}>
            Lewati
          </Button>
          <Group gap="xs" wrap="nowrap">
            <Button
              variant="default"
              onClick={onBack}
              disabled={stepIndex === 0}
              leftSection={<IconChevronLeft size={16} />}
            >
              Kembali
            </Button>
            {isLast ? (
              <Button onClick={onClose} leftSection={<IconCheck size={16} />}>
                Selesai
              </Button>
            ) : (
              <Button onClick={onNext} rightSection={<IconChevronRight size={16} />}>
                Lanjut
              </Button>
            )}
          </Group>
        </Group>
      </Stack>
    </Modal>
  )
}

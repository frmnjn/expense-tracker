import { Anchor, Container, Group } from '@mantine/core'
import ExpenseForm from '../components/ExpenseForm'
import InstallButton from '../components/InstallButton'

function ExpensePage() {
  return (
    <Container
      size="xs"
      px="md"
      py="lg"
      styles={{
        root: {
          '@media (max-width: 40em)': {
            paddingTop: 'var(--mantine-spacing-md)',
            paddingBottom: 'calc(var(--mantine-spacing-md) + 80px)',
          },
        },
      }}
    >
      <Group justify="space-between" mb="xs">
        <Anchor href="/riwayat" size="sm">
          Riwayat
        </Anchor>
        <InstallButton />
      </Group>
      <ExpenseForm />
    </Container>
  )
}

export default ExpensePage

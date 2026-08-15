import { Container, Stack, Text, Title } from '@mantine/core'
import ExpenseForm from '../components/ExpenseForm'

function ExpensePage() {
  return (
    <Container
      size="sm"
      px={{ base: 0, sm: 'md' }}
      py={{ base: 'md', sm: 'md' }}
      pb={{ base: 'calc(96px + env(safe-area-inset-bottom, 0px))', sm: 'md' }}
    >
      <Stack gap="lg">
        <div>
          <Text size="sm" c="blue" fw={700} mb={4}>
            PENGELUARAN BARU
          </Text>
          <Title order={1} size="clamp(1.65rem, 5vw, 2.1rem)" lh={1.15}>
            Catat pengeluaran
          </Title>
          <Text c="dimmed" mt={6}>
            Simpan transaksi dan pantau sisa budget kamu.
          </Text>
        </div>
        <ExpenseForm />
      </Stack>
    </Container>
  )
}

export default ExpensePage

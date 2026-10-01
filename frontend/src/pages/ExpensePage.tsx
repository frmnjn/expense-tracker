import { Container, Stack } from '@mantine/core'
import ExpenseForm from '../components/ExpenseForm'
import { PageHeader } from '../components/PageHeader'

function ExpensePage() {
  return (
    <Container
      size="sm"
      px={{ base: 0, sm: 'md' }}
      py={{ base: 'md', sm: 'md' }}
      pb={{ base: 'calc(96px + env(safe-area-inset-bottom, 0px))', sm: 'md' }}
    >
      <Stack gap="lg">
        <PageHeader
          eyebrow="Pengeluaran Baru"
          title="Catat pengeluaran"
          subtitle="Simpan transaksi dan pantau sisa budget kamu."
        />
        <ExpenseForm />
      </Stack>
    </Container>
  )
}

export default ExpensePage

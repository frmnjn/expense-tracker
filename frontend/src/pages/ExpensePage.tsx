import { Container } from '@mantine/core'
import ExpenseForm from '../components/ExpenseForm'

function ExpensePage() {
  return (
    <Container size="xs" py="xl">
      <ExpenseForm />
    </Container>
  )
}

export default ExpensePage

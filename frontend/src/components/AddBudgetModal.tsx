import { useState } from 'react'
import { Button, Group, Modal, NumberInput, Stack, TextInput } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useCreateBudget } from '../hooks/useBudgets'

function AddBudgetModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const createBudget = useCreateBudget()
  const [name, setName] = useState('')
  const [balance, setBalance] = useState<string | number>('')

  const submitDisabled = name.trim() === '' || createBudget.isPending

  const handleClose = () => {
    setName('')
    setBalance('')
    onClose()
  }

  const handleSubmit = () => {
    createBudget.mutate(
      {
        name: name.trim(),
        balance: balance === '' ? undefined : Number(balance),
      },
      {
        onSuccess: () => {
          notifications.show({ title: 'Berhasil', message: 'Budget ditambahkan', color: 'green' })
          handleClose()
        },
        onError: (error) => {
          const message =
            (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
            'Terjadi kesalahan, coba lagi'
          notifications.show({ title: 'Gagal', message, color: 'red' })
        },
      },
    )
  }

  return (
    <Modal opened={opened} onClose={handleClose} title="Tambah Budget" centered>
      <Stack>
        <TextInput
          label="Nama"
          placeholder="Nama budget"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          maxLength={255}
          required
          size="md"
        />
        <NumberInput
          label="Saldo awal (opsional)"
          placeholder="0"
          value={balance}
          onChange={setBalance}
          prefix="Rp"
          thousandSeparator="."
          decimalSeparator=","
          size="md"
        />
        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={handleClose}>
            Batal
          </Button>
          <Button onClick={handleSubmit} loading={createBudget.isPending} disabled={submitDisabled}>
            Tambah
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}

export default AddBudgetModal

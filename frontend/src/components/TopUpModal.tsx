import { useState } from 'react'
import { Button, Group, Modal, NumberInput, Stack, Text, TextInput } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useCreateTopUp } from '../hooks/useTopUps'
import { formatCurrency } from '../utils/currency'

function TopUpModal({
  opened,
  budget,
  balance,
  onClose,
}: {
  opened: boolean
  budget: string
  balance?: number
  onClose: () => void
}) {
  const createTopUp = useCreateTopUp()
  const [amount, setAmount] = useState<string | number>('')
  const [description, setDescription] = useState('')

  const submitDisabled = Number(amount) <= 0 || createTopUp.isPending

  const handleClose = () => {
    setAmount('')
    setDescription('')
    onClose()
  }

  const handleSubmit = () => {
    createTopUp.mutate(
      {
        budget,
        amount: Number(amount),
        description: description.trim() === '' ? undefined : description.trim(),
      },
      {
        onSuccess: () => {
          notifications.show({ title: 'Berhasil', message: 'Saldo ditambahkan', color: 'green' })
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
    <Modal opened={opened} onClose={handleClose} title={`Tambah Saldo — ${budget}`} centered>
      <Stack>
        {balance !== undefined && (
          <Text size="sm" c="dimmed">
            Saldo saat ini: <Text span fw={600} c={balance < 0 ? 'red' : undefined}>{formatCurrency(balance)}</Text>
          </Text>
        )}
        <NumberInput
          label="Nominal"
          placeholder="0"
          value={amount}
          onChange={setAmount}
          min={1}
          allowNegative={false}
          prefix="Rp"
          thousandSeparator="."
          decimalSeparator=","
          required
          size="md"
        />
        <TextInput
          label="Description (opsional)"
          placeholder="Catatan tambahan"
          value={description}
          onChange={(e) => setDescription(e.currentTarget.value)}
          maxLength={255}
          size="md"
        />
        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={handleClose}>
            Batal
          </Button>
          <Button onClick={handleSubmit} loading={createTopUp.isPending} disabled={submitDisabled}>
            Tambah Saldo
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}

export default TopUpModal

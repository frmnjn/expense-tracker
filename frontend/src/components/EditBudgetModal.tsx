import { useEffect, useState } from 'react'
import { Button, Group, Modal, NumberInput, Stack, TextInput } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useUpdateBudget } from '../hooks/useBudgets'

function EditBudgetModal({
  budget,
  balance,
  alertThreshold,
  onClose,
}: {
  budget: string | null
  balance: number | undefined
  alertThreshold: number | undefined
  onClose: () => void
}) {
  const updateBudget = useUpdateBudget()
  const [name, setName] = useState('')
  const [newBalance, setNewBalance] = useState<string | number>('')
  const [newThreshold, setNewThreshold] = useState<string | number>('')

  useEffect(() => {
    if (budget) {
      setName(budget)
      setNewBalance(balance ?? '')
      setNewThreshold(alertThreshold ?? '')
    }
  }, [budget, balance, alertThreshold])

  const submitDisabled = name.trim() === '' || updateBudget.isPending

  const handleSubmit = () => {
    if (!budget) return
    updateBudget.mutate(
      {
        name: budget,
        request: {
          name: name.trim(),
          balance: newBalance === '' ? undefined : Number(newBalance),
          alertThreshold: newThreshold === '' ? 0 : Number(newThreshold),
        },
      },
      {
        onSuccess: () => {
          notifications.show({ title: 'Berhasil', message: 'Budget diperbarui', color: 'green' })
          onClose()
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
    <Modal opened={!!budget} onClose={onClose} title="Edit Budget" centered>
      <Stack>
        <TextInput
          label="Nama"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          maxLength={255}
          required
          size="md"
        />
        <NumberInput
          label="Saldo"
          value={newBalance}
          onChange={setNewBalance}
          prefix="Rp"
          thousandSeparator="."
          decimalSeparator=","
          size="md"
        />
        <NumberInput
          label="Ambang notifikasi"
          placeholder="0"
          description="Kirim notifikasi saat saldo < ambang. 0 = nonaktif."
          value={newThreshold}
          onChange={setNewThreshold}
          min={0}
          prefix="Rp"
          thousandSeparator="."
          decimalSeparator=","
          size="md"
        />
        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={onClose}>
            Batal
          </Button>
          <Button onClick={handleSubmit} loading={updateBudget.isPending} disabled={submitDisabled}>
            Simpan
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}

export default EditBudgetModal

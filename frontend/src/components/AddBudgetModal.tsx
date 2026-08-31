import { useState } from 'react'
import { Button, Group, Modal, NumberInput, Stack, TextInput } from '@mantine/core'
import { useCreateBudget } from '../hooks/useBudgets'
import { useToast } from '../components/Toast'

function AddBudgetModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const createBudget = useCreateBudget()
  const toast = useToast()
  const [name, setName] = useState('')
  const [balance, setBalance] = useState<string | number>('')
  const [alertThreshold, setAlertThreshold] = useState<string | number>('')
  const [description, setDescription] = useState('')

  const submitDisabled = name.trim() === '' || createBudget.isPending

  const handleClose = () => {
    setName('')
    setBalance('')
    setAlertThreshold('')
    setDescription('')
    onClose()
  }

  const handleSubmit = () => {
    createBudget.mutate(
      {
        name: name.trim(),
        balance: balance === '' ? undefined : Number(balance),
        alertThreshold: alertThreshold === '' ? 0 : Number(alertThreshold),
        description: description.trim() === '' ? undefined : description.trim(),
      },
      {
        onSuccess: () => {
          toast.success('Budget ditambahkan', { title: 'Berhasil' })
          handleClose()
        },
        onError: (error) => {
          const message =
            (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
            'Terjadi kesalahan, coba lagi'
          toast.error(message, { title: 'Gagal' })
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
        <NumberInput
          label="Ambang notifikasi (opsional)"
          placeholder="0"
          description="Kirim notifikasi saat saldo < ambang. 0 = nonaktif."
          value={alertThreshold}
          onChange={setAlertThreshold}
          min={0}
          prefix="Rp"
          thousandSeparator="."
          decimalSeparator=","
          size="md"
        />
        <TextInput
          label="Deskripsi (opsional)"
          placeholder="Budget ini untuk apa?"
          value={description}
          onChange={(e) => setDescription(e.currentTarget.value)}
          maxLength={500}
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

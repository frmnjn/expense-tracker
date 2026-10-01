import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Group, Modal, NumberInput, Select, Stack, Text, TextInput } from '@mantine/core'
import { DateTimePicker } from '@mantine/dates'
import dayjs from 'dayjs'
import { useImportEmail } from '../hooks/useEmailImports'
import { useOptions } from '../hooks/useOptions'
import { useToast } from '../components/Toast'
import { getErrorMessage } from '../utils/error'
import { formatCurrency } from '../utils/currency'
import type { EmailImport } from '../types/emailImport'
import type { ExpenseRequest } from '../types/expense'

function ImportEmailModal({ item, onClose }: { item: EmailImport; onClose: () => void }) {
  const importMutation = useImportEmail()
  const { data: options } = useOptions()
  const toast = useToast()

  const [name, setName] = useState(item.merchant ?? '')
  const [amount, setAmount] = useState<string | number>(item.amount ?? '')
  const [budget, setBudget] = useState(item.suggestedBudget ?? '')
  const [category, setCategory] = useState<string | null>(item.suggestedCategory ?? null)
  const [dateTime, setDateTime] = useState(
    item.transactionAt ? dayjs(item.transactionAt).format('YYYY-MM-DD HH:mm') : dayjs().format('YYYY-MM-DD HH:mm'),
  )
  const [description, setDescription] = useState(item.description ?? '')
  const [duplicateMessage, setDuplicateMessage] = useState<string | null>(null)

  const budgets = options?.budgets

  useEffect(() => {
    if (!budget && budgets && budgets.length > 0) {
      const suggested = budgets.find((b) => b.name === item.suggestedBudget)
      setBudget(suggested?.name ?? budgets[0].name)
    }
  }, [budget, budgets, item.suggestedBudget])

  const categoryOptions = (budgets?.find((b) => b.name === budget)?.categories ?? []).map((c) => ({
    value: c.name,
    label: c.name,
  }))

  const submitDisabled = !name.trim() || !budget || Number(amount) <= 0 || !dateTime || importMutation.isPending

  const problems = useMemo(() => {
    const list: string[] = []
    if (!name.trim()) list.push('Nama belum diisi')
    if (!budget) list.push('Budget belum dipilih')
    if (Number(amount) <= 0) list.push('Nominal harus lebih dari 0')
    if (!dateTime) list.push('Waktu belum diisi')
    return list
  }, [name, budget, amount, dateTime])

  const submit = (force: boolean) => {
    const request: ExpenseRequest = {
      dateTime: dayjs(dateTime).format('YYYY-MM-DD HH:mm'),
      name: name.trim(),
      budget,
      category: category ?? undefined,
      amount: Number(amount),
      description: description.trim() === '' ? undefined : description.trim(),
    }
    importMutation.mutate(
      { id: item.id, request, force },
      {
        onSuccess: () => {
          toast.success('Transaksi dicatat sebagai pengeluaran', { title: 'Berhasil' })
          onClose()
        },
        onError: (error) => {
          const status = (error as { response?: { status?: number } }).response?.status
          if (status === 409) {
            setDuplicateMessage(getErrorMessage(error))
            return
          }
          toast.error(getErrorMessage(error), { title: 'Gagal' })
        },
      },
    )
  }

  if (duplicateMessage) {
    return (
      <Modal opened onClose={onClose} title="Kemungkinan Duplikat" centered>
        <Stack>
          <Alert color="orange" title="Nominal sama sudah tercatat">
            {duplicateMessage}
          </Alert>
          <Text size="sm" c="dimmed">
            Nominal sama bisa saja transaksi berbeda. Pilih "Tetap Impor" bila memang transaksi baru.
          </Text>
          <Group justify="flex-end" mt="md">
            <Button variant="default" onClick={onClose}>
              Batal
            </Button>
            <Button color="orange" onClick={() => submit(true)} loading={importMutation.isPending}>
              Tetap Impor
            </Button>
          </Group>
        </Stack>
      </Modal>
    )
  }

  return (
    <Modal opened onClose={onClose} title="Impor Transaksi" centered>
      <Stack>
        <Text size="xs" c="dimmed">
          {item.merchant ?? item.subject} · {formatCurrency(item.amount ?? 0)}
        </Text>
        <TextInput
          label="Nama"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          maxLength={255}
          required
          size="md"
        />
        <NumberInput
          label="Nominal"
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
        <Select
          label="Budget"
          placeholder="Pilih budget"
          data={(budgets ?? []).map((b) => b.name)}
          value={budget}
          onChange={(v) => {
            setBudget(v ?? '')
            setCategory(null)
          }}
          searchable
          required
          size="md"
        />
        <Select
          label="Category"
          placeholder={budget ? 'Uncategorized' : 'Pilih budget dulu'}
          data={categoryOptions}
          value={category}
          onChange={setCategory}
          searchable
          clearable
          disabled={!budget || categoryOptions.length === 0}
          size="md"
          comboboxProps={{ withinPortal: false }}
        />
        <DateTimePicker
          label="Waktu"
          value={dateTime}
          onChange={(v) => setDateTime(v ? dayjs(v).format('YYYY-MM-DD HH:mm') : dayjs().format('YYYY-MM-DD HH:mm'))}
          valueFormat="DD MMM YYYY HH:mm"
          dropdownType="modal"
          required
          size="md"
        />
        <TextInput
          label="Deskripsi (opsional)"
          value={description}
          onChange={(e) => setDescription(e.currentTarget.value)}
          maxLength={255}
          size="md"
        />
        {problems.length > 0 && (
          <Stack gap={2}>
            {problems.map((problem) => (
              <Text key={problem} size="xs" c="orange">
                ⚠ {problem}
              </Text>
            ))}
          </Stack>
        )}
        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={onClose}>
            Batal
          </Button>
          <Button onClick={() => submit(false)} loading={importMutation.isPending} disabled={submitDisabled}>
            Impor
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}

export default ImportEmailModal

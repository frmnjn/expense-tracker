import { useMemo, useState } from 'react'
import {
  Box,
  Button,
  NumberInput,
  Paper,
  SegmentedControl,
  Select,
  Stack,
  TextInput,
  Title,
} from '@mantine/core'
import { DateTimePicker } from '@mantine/dates'
import { notifications } from '@mantine/notifications'
import dayjs from 'dayjs'
import { useCreateExpense } from '../hooks/useCreateExpense'
import { useOptions } from '../hooks/useOptions'

const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm'
const DATE_TIME_SECONDS_FORMAT = 'YYYY-MM-DD HH:mm:ss'

function ExpenseForm() {
  const [mode, setMode] = useState('now')
  const [dateTime, setDateTime] = useState<string>(dayjs().format(DATE_TIME_SECONDS_FORMAT))
  const [name, setName] = useState('')
  const [budget, setBudget] = useState<string | null>(null)
  const [amount, setAmount] = useState<string | number>('')
  const [description, setDescription] = useState('')

  const { data: options, isPending: optionsLoading } = useOptions()
  const createExpense = useCreateExpense()

  const nowDisabled = mode === 'now'
  const displayValue = nowDisabled ? dayjs().format(DATE_TIME_SECONDS_FORMAT) : dateTime

  const submitDisabled =
    name.trim() === '' || !budget || Number(amount) <= 0 || createExpense.isPending

  const handleSubmit = () => {
    createExpense.mutate(
      {
        dateTime: dayjs(displayValue).format(DATE_TIME_FORMAT),
        name: name.trim(),
        budget: budget ?? '',
        amount: Number(amount),
        description: description.trim() === '' ? undefined : description.trim(),
      },
      {
        onSuccess: () => {
          notifications.show({
            title: 'Berhasil',
            message: 'Pengeluaran berhasil disimpan',
            color: 'green',
          })
          setName('')
          setBudget(null)
          setAmount('')
          setDescription('')
          setDateTime(dayjs().format(DATE_TIME_SECONDS_FORMAT))
        },
        onError: (error) => {
          const message =
            (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
            'Terjadi kesalahan, coba lagi'
          notifications.show({
            title: 'Gagal',
            message,
            color: 'red',
          })
        },
      },
    )
  }

  const budgetOptions = useMemo(
    () => (options?.budgets ?? []).map((value) => ({ value, label: value })),
    [options],
  )

  return (
    <Paper withBorder p="lg" radius="md" shadow="sm">
      <Stack>
        <Title order={3}>Catat Pengeluaran</Title>

        <div>
          <SegmentedControl
            value={mode}
            onChange={setMode}
            fullWidth
            data={[
              { value: 'now', label: 'Waktu Sekarang' },
              { value: 'manual', label: 'Waktu Manual' },
            ]}
          />
        </div>

        <DateTimePicker
          label="Waktu"
          placeholder="Pilih waktu"
          value={displayValue}
          onChange={(value) => setDateTime(value ?? '')}
          valueFormat={DATE_TIME_FORMAT}
          required
          disabled={nowDisabled}
          dropdownType="modal"
          size="md"
        />

        <TextInput
          label="Name"
          placeholder="Nama pengeluaran"
          value={name}
          onChange={(event) => setName(event.currentTarget.value)}
          maxLength={255}
          required
          size="md"
        />

        <Select
          label="Budget"
          placeholder={optionsLoading ? 'Memuat...' : 'Pilih budget'}
          data={budgetOptions}
          value={budget}
          onChange={setBudget}
          searchable
          required
          disabled={optionsLoading}
          size="md"
        />

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
          suffix=",-"
          required
          size="md"
        />

        <TextInput
          label="Description (opsional)"
          placeholder="Catatan tambahan"
          value={description}
          onChange={(event) => setDescription(event.currentTarget.value)}
          maxLength={255}
          size="md"
        />

        <Box
          mt="md"
          style={{
            position: 'sticky',
            bottom: 0,
            paddingTop: 'var(--mantine-spacing-sm)',
            paddingBottom: 'var(--mantine-spacing-sm)',
            backgroundColor: 'var(--mantine-color-body)',
          }}
        >
          <Button onClick={handleSubmit} loading={createExpense.isPending} disabled={submitDisabled} fullWidth size="md">
            Save
          </Button>
        </Box>
      </Stack>
    </Paper>
  )
}

export default ExpenseForm

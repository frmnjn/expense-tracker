import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Group, Modal, Select, Stack, Text } from '@mantine/core'
import { DatePickerInput } from '@mantine/dates'
import dayjs from 'dayjs'
import { useDeletePaydayOverride, useSavePaydayOverride } from '../hooks/useExpenses'
import { useToast } from '../components/Toast'
import { getErrorMessage } from '../utils/error'
import type { PeriodRange } from '../types/expense'

function isoDate(date: Date): string {
  return dayjs(date).format('YYYY-MM-DD')
}

function monthBounds(period: string): { minDate: Date; maxDate: Date } | null {
  const match = /^(\d{4})-([A-Za-z]{3})/.exec(period)
  if (!match) return null
  const months = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']
  const month = months.indexOf(match[2].toUpperCase())
  if (month < 0) return null
  const year = Number(match[1])
  return { minDate: new Date(year, month, 1), maxDate: new Date(year, month + 1, 0) }
}

function PaydaySettingsModal({
  opened,
  calendar,
  currentPeriod,
  onClose,
}: {
  opened: boolean
  calendar: PeriodRange[]
  currentPeriod: string
  onClose: () => void
}) {
  const save = useSavePaydayOverride()
  const reset = useDeletePaydayOverride()
  const toast = useToast()
  const [selectedPeriod, setSelectedPeriod] = useState(currentPeriod)
  const [date, setDate] = useState<string | null>(null)

  const options = useMemo(() => {
    const currentIndex = calendar.findIndex((range) => range.period === currentPeriod)
    const slice = currentIndex >= 0 ? calendar.slice(currentIndex, currentIndex + 7) : calendar.slice(-7)
    return slice.map((range) => ({
      value: range.period,
      label: range.overridden ? `${range.period} (custom)` : range.period,
    }))
  }, [calendar, currentPeriod])

  const selected = calendar.find((range) => range.period === selectedPeriod) ?? null
  const bounds = monthBounds(selectedPeriod)

  useEffect(() => {
    if (!opened) return
    // oxlint-disable-next-line react/set-state-in-effect -- reset form saat modal dibuka
    setSelectedPeriod(currentPeriod)
  }, [opened, currentPeriod])

  useEffect(() => {
    const current = calendar.find((range) => range.period === selectedPeriod)
    if (!current) {
      // oxlint-disable-next-line react/set-state-in-effect -- ikuti periode yang dipilih
      setDate(null)
      return
    }
    // oxlint-disable-next-line react/set-state-in-effect -- isi tanggal dari kalender periode
    setDate(dayjs(current.start).format('YYYY-MM-DD'))
  }, [calendar, selectedPeriod])

  const handleSave = () => {
    if (!date) return
    save.mutate(
      { period: selectedPeriod, date },
      {
        onSuccess: () => {
          toast.success('Tanggal gajian disimpan', { title: 'Berhasil' })
          onClose()
        },
        onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
      },
    )
  }

  const handleReset = () => {
    reset.mutate(selectedPeriod, {
      onSuccess: () => {
        toast.success('Tanggal gajian dikembalikan ke default', { title: 'Berhasil' })
        onClose()
      },
      onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
    })
  }

  const pending = save.isPending || reset.isPending

  return (
    <Modal opened={opened} onClose={onClose} title="Atur Tanggal Gajian" centered>
      <Stack>
        <Text size="sm" c="dimmed">
          Default tanggal 25. Bila jatuh pada Sabtu/Minggu, otomatis mundur ke hari kerja sebelumnya.
          Gunakan override untuk tanggal merah atau gajian yang maju/mundur.
        </Text>

        <Select
          label="Periode"
          data={options}
          value={selectedPeriod}
          onChange={(value) => setSelectedPeriod(value ?? currentPeriod)}
          searchable
          size="md"
        />

        <DatePickerInput
          label="Tanggal gajian"
          value={date}
          onChange={(value) => setDate(value ? isoDate(new Date(value)) : null)}
          valueFormat="DD MMM YYYY"
          dropdownType="modal"
          minDate={bounds?.minDate}
          maxDate={bounds?.maxDate}
          size="md"
        />

        {selected?.overridden && (
          <Text size="xs" c="dimmed">
            Periode ini memakai tanggal custom: {selected.start}.
          </Text>
        )}

        <Alert color="blue" variant="light">
          <Text size="xs">
            Menyimpan override akan menghitung ulang periode expense, invoice, dan rekaman suara yang
            tanggalnya berada di periode ini.
          </Text>
        </Alert>

        <Group justify="space-between">
          <Button variant="light" color="red" onClick={handleReset} disabled={pending || !selected?.overridden}>
            Reset ke default
          </Button>
          <Button onClick={handleSave} loading={save.isPending} disabled={!date || pending}>
            Simpan
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}

export default PaydaySettingsModal

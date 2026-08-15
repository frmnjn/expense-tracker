import { useRef, useState } from 'react'
import { Button, Grid, Group, Image, Loader, Modal, Stack, Text } from '@mantine/core'
import { useInvoices } from '../hooks/useInvoices'
import { getInvoicePhotoUrl } from '../services/expense'
import { InvoiceThumb } from './InvoiceThumb'

export type PhotoSelection =
  | { kind: 'new'; file: File }
  | { kind: 'existing'; invoiceId: string; name?: string }

function PhotoInput({
  value,
  onChange,
  dateTime,
}: {
  value: PhotoSelection | null
  onChange: (value: PhotoSelection | null) => void
  dateTime: string | null
}) {
  const [opened, setOpened] = useState(false)
  const [pickingExisting, setPickingExisting] = useState(false)
  const cameraRef = useRef<HTMLInputElement>(null)
  const galleryRef = useRef<HTMLInputElement>(null)

  const invoices = useInvoices(pickingExisting ? dateTime : null)

  const pick = (file: File | null) => {
    onChange(file ? { kind: 'new', file } : null)
    setOpened(false)
  }

  const pickExisting = (invoiceId: string, name?: string) => {
    onChange({ kind: 'existing', invoiceId, name })
    setOpened(false)
  }

  const clear = () => onChange(null)

  const fileName =
    value?.kind === 'new' ? value.file.name : value?.kind === 'existing' ? value.name : undefined

  return (
    <>
      {value ? (
        <Group align="center" wrap="nowrap">
          <Image
            src={value.kind === 'existing' ? getInvoicePhotoUrl(value.invoiceId) : URL.createObjectURL(value.file)}
            alt="Preview invoice"
            mah={140}
            maw={120}
            fit="contain"
            radius="md"
            style={{ flexShrink: 0 }}
          />
          <Stack gap={6} style={{ flex: 1, minWidth: 0 }}>
            {fileName ? (
              <Text size="sm" c="dimmed" truncate title={fileName}>
                {fileName}
              </Text>
            ) : null}
            <Button variant="subtle" color="red" size="sm" onClick={clear} style={{ alignSelf: 'flex-start' }}>
              Hapus
            </Button>
          </Stack>
        </Group>
      ) : (
        <Button variant="light" fullWidth onClick={() => setOpened(true)}>
          📷 Tambah Foto
        </Button>
      )}

      <input
        ref={cameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        style={{ display: 'none' }}
        onChange={(e) => pick(e.target.files?.[0] ?? null)}
      />
      <input
        ref={galleryRef}
        type="file"
        accept="image/*,application/pdf"
        style={{ display: 'none' }}
        onChange={(e) => pick(e.target.files?.[0] ?? null)}
      />

      <Modal opened={opened} onClose={() => setOpened(false)} title="Tambah Foto Invoice" centered>
        {pickingExisting ? (
          <Stack>
            <Group justify="space-between">
              <Text size="sm">Pilih foto yang sudah ada di periode ini</Text>
              <Button variant="subtle" size="xs" onClick={() => setPickingExisting(false)}>
                ← Kembali
              </Button>
            </Group>
            {invoices.isPending ? (
              <Loader />
            ) : invoices.data && invoices.data.invoices.length > 0 ? (
              <Grid gap="xs">
                {invoices.data.invoices.map((invoice) => (
                  <Grid.Col key={invoice.id} span={4}>
                    <InvoiceThumb
                      type={invoice.type}
                      url={getInvoicePhotoUrl(invoice.id)}
                      onClick={() => pickExisting(invoice.id, invoice.name)}
                    />
                  </Grid.Col>
                ))}
              </Grid>
            ) : (
              <Text size="sm" c="dimmed">
                Tidak ada foto di periode ini.
              </Text>
            )}
          </Stack>
        ) : (
          <Stack>
            <Button fullWidth onClick={() => cameraRef.current?.click()}>
              📷 Ambil Foto (Kamera)
            </Button>
            <Button fullWidth variant="light" onClick={() => galleryRef.current?.click()}>
              🖼 Dari Galeri
            </Button>
            <Button fullWidth variant="subtle" onClick={() => setPickingExisting(true)}>
              📁 Pakai Foto Periode Ini
            </Button>
          </Stack>
        )}
      </Modal>
    </>
  )
}

export default PhotoInput

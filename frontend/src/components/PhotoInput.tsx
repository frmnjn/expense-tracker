import { useRef, useState } from 'react'
import { Button, Group, Image, Modal, Stack } from '@mantine/core'

function PhotoInput({ value, onChange }: { value: File | null; onChange: (file: File | null) => void }) {
  const [opened, setOpened] = useState(false)
  const cameraRef = useRef<HTMLInputElement>(null)
  const galleryRef = useRef<HTMLInputElement>(null)

  const pick = (file: File | null) => {
    onChange(file)
    setOpened(false)
  }

  return (
    <>
      {value ? (
        <Group align="flex-start">
          <Image
            src={URL.createObjectURL(value)}
            alt="Preview invoice"
            mah={200}
            fit="contain"
            radius="md"
            style={{ flex: 1 }}
          />
          <Button variant="subtle" color="red" onClick={() => onChange(null)}>
            Hapus
          </Button>
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
        accept="image/*"
        style={{ display: 'none' }}
        onChange={(e) => pick(e.target.files?.[0] ?? null)}
      />

      <Modal opened={opened} onClose={() => setOpened(false)} title="Tambah Foto Invoice" centered>
        <Stack>
          <Button fullWidth onClick={() => cameraRef.current?.click()}>
            📷 Ambil Foto (Kamera)
          </Button>
          <Button fullWidth variant="light" onClick={() => galleryRef.current?.click()}>
            🖼 Dari Galeri
          </Button>
        </Stack>
      </Modal>
    </>
  )
}

export default PhotoInput

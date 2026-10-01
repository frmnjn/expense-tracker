import { useState } from 'react'
import { ActionIcon, Button, Group, Modal, Paper, Stack, Text, TextInput } from '@mantine/core'
import { IconPencil, IconTrash } from '@tabler/icons-react'
import { useOptions } from '../hooks/useOptions'
import { useCreateCategory, useDeleteCategory, useUpdateCategory } from '../hooks/useCategories'
import { getErrorMessage } from '../utils/error'
import { useToast } from './Toast'

function ManageCategoriesModal({ budget, onClose }: { budget: string; onClose: () => void }) {
  const { data: options } = useOptions()
  const createCategory = useCreateCategory()
  const updateCategory = useUpdateCategory()
  const deleteCategory = useDeleteCategory()
  const toast = useToast()

  const categories = options?.budgets.find((b) => b.name === budget)?.categories ?? []

  const [editingId, setEditingId] = useState<number | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  const reset = () => {
    setEditingId(null)
    setName('')
    setDescription('')
  }

  const submit = () => {
    const trimmed = name.trim()
    if (!trimmed) return
    const onSuccess = () => {
      toast.success(editingId ? 'Kategori diperbarui' : 'Kategori ditambahkan', { title: 'Berhasil' })
      reset()
    }
    const onError = (error: unknown) => toast.error(getErrorMessage(error), { title: 'Gagal' })
    if (editingId != null) {
      updateCategory.mutate(
        { id: editingId, request: { name: trimmed, description: description.trim() || undefined } },
        { onSuccess, onError },
      )
    } else {
      createCategory.mutate(
        { budgetName: budget, name: trimmed, description: description.trim() || undefined },
        { onSuccess, onError },
      )
    }
  }

  const startEdit = (id: number, categoryName: string, categoryDescription?: string) => {
    setEditingId(id)
    setName(categoryName)
    setDescription(categoryDescription ?? '')
  }

  const handleDelete = (id: number) => {
    deleteCategory.mutate(id, {
      onSuccess: () => toast.success('Kategori dihapus', { title: 'Berhasil' }),
      onError: (error) => toast.error(getErrorMessage(error), { title: 'Gagal' }),
    })
  }

  const isPending = createCategory.isPending || updateCategory.isPending

  return (
    <Modal opened onClose={onClose} title={`Kelola Kategori — ${budget}`} centered size="md">
      <Stack gap="sm">
        {categories.length === 0 ? (
          <Text size="sm" c="dimmed">
            Belum ada kategori. Tambahkan kategori untuk budget ini.
          </Text>
        ) : (
          categories.map((c) => (
            <Paper key={c.id} withBorder p="sm" radius="md">
              <Group justify="space-between" wrap="nowrap">
                <div style={{ minWidth: 0 }}>
                  <Text fw={600} truncate>
                    {c.name}
                  </Text>
                  {c.description && (
                    <Text size="xs" c="dimmed" truncate>
                      {c.description}
                    </Text>
                  )}
                </div>
                <Group gap={4} wrap="nowrap">
                  <ActionIcon
                    variant="light"
                    color="blue"
                    onClick={() => startEdit(c.id, c.name, c.description)}
                    aria-label="Edit kategori"
                  >
                    <IconPencil size={16} />
                  </ActionIcon>
                  <ActionIcon
                    variant="light"
                    color="red"
                    onClick={() => handleDelete(c.id)}
                    aria-label="Hapus kategori"
                  >
                    <IconTrash size={16} />
                  </ActionIcon>
                </Group>
              </Group>
            </Paper>
          ))
        )}

        <TextInput
          label={editingId ? 'Ubah kategori' : 'Kategori baru'}
          placeholder="Nama kategori"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          maxLength={255}
          size="md"
        />
        <TextInput
          label="Deskripsi (opsional)"
          placeholder="Dipakai AI untuk memilih kategori"
          value={description}
          onChange={(e) => setDescription(e.currentTarget.value)}
          maxLength={500}
          size="md"
        />
        <Group justify="flex-end">
          {editingId && (
            <Button variant="default" onClick={reset}>
              Batal edit
            </Button>
          )}
          <Button onClick={submit} loading={isPending} disabled={!name.trim()}>
            {editingId ? 'Simpan' : 'Tambah'}
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}

export default ManageCategoriesModal

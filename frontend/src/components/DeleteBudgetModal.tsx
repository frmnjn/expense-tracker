import { Button, Group, Modal, Text } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useDeleteBudget } from '../hooks/useBudgets'

function DeleteBudgetModal({ name, onClose }: { name: string | null; onClose: () => void }) {
  const deleteBudget = useDeleteBudget()

  const handleDelete = () => {
    if (!name) return
    deleteBudget.mutate(name, {
      onSuccess: () => {
        notifications.show({ title: 'Berhasil', message: 'Budget dihapus', color: 'green' })
        onClose()
      },
      onError: (error) => {
        const message =
          (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Terjadi kesalahan, coba lagi'
        notifications.show({ title: 'Gagal', message, color: 'red' })
      },
    })
  }

  return (
    <Modal opened={!!name} onClose={onClose} title="Hapus budget" centered>
      <Text>Yakin ingin menghapus budget "{name}"?</Text>
      <Group justify="flex-end" mt="lg">
        <Button variant="default" onClick={onClose}>
          Batal
        </Button>
        <Button color="red" loading={deleteBudget.isPending} onClick={handleDelete}>
          Hapus
        </Button>
      </Group>
    </Modal>
  )
}

export default DeleteBudgetModal

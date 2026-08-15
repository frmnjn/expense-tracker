import { Button, Group, Modal, Text } from '@mantine/core'
import { useDeleteBudget } from '../hooks/useBudgets'
import { useToast } from '../components/Toast'

function DeleteBudgetModal({ name, onClose }: { name: string | null; onClose: () => void }) {
  const deleteBudget = useDeleteBudget()
  const toast = useToast()

  const handleDelete = () => {
    if (!name) return
    deleteBudget.mutate(name, {
      onSuccess: () => {
        toast.success('Budget dihapus', { title: 'Berhasil' })
        onClose()
      },
      onError: (error) => {
        const message =
          (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Terjadi kesalahan, coba lagi'
        toast.error(message, { title: 'Gagal' })
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

import { Button, Group, Modal, Text } from '@mantine/core'
import { useDeleteInvoice } from '../hooks/useScan'
import { useToast } from './Toast'
import type { Invoice } from '../types/expense'

function DeleteInvoiceModal({ invoice, onClose }: { invoice: Invoice | null; onClose: () => void }) {
  const deleteInvoice = useDeleteInvoice()
  const toast = useToast()

  const handleDelete = () => {
    if (!invoice) return
    deleteInvoice.mutate(invoice.id, {
      onSuccess: () => {
        toast.success('Struk dihapus', { title: 'Berhasil' })
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
    <Modal opened={!!invoice} onClose={onClose} title="Hapus struk" centered>
      <Text>Yakin ingin menghapus struk ini?</Text>
      <Group justify="flex-end" mt="lg">
        <Button variant="default" onClick={onClose}>
          Batal
        </Button>
        <Button color="red" loading={deleteInvoice.isPending} onClick={handleDelete}>
          Hapus
        </Button>
      </Group>
    </Modal>
  )
}

export default DeleteInvoiceModal

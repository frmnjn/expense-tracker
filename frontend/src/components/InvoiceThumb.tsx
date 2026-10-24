import { Button, Image, Stack } from '@mantine/core'
import { IconFileTypePdf } from '@tabler/icons-react'
import { usePdfViewer } from './PdfViewerContext'

export function InvoiceThumb({
  type,
  url,
  alt,
  h = 100,
  onClick,
}: {
  type: string
  url: string
  alt?: string
  h?: number
  onClick?: () => void
}) {
  const { openPdf } = usePdfViewer()

  if (type === 'pdf') {
    return (
      <Stack
        align="center"
        justify="center"
        gap={2}
        h={h}
        onClick={onClick}
        style={{ cursor: onClick ? 'pointer' : undefined }}
      >
        <IconFileTypePdf size={28} aria-hidden />
        <Button
          variant="subtle"
          size="compact-xs"
          onClick={(event) => {
            event.stopPropagation()
            openPdf({ url, title: alt ?? 'Invoice' })
          }}
        >
          Lihat PDF
        </Button>
      </Stack>
    )
  }
  return <Image src={url} alt={alt} fit="cover" h={h} radius="md" style={{ cursor: onClick ? 'pointer' : undefined }} onClick={onClick} />
}

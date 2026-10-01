import { Anchor, Image, Stack } from '@mantine/core'
import { IconFileTypePdf } from '@tabler/icons-react'

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
        <Anchor href={url} target="_blank" size="xs" onClick={(e) => e.stopPropagation()}>
          Lihat PDF
        </Anchor>
      </Stack>
    )
  }
  return <Image src={url} alt={alt} fit="cover" h={h} radius="md" style={{ cursor: onClick ? 'pointer' : undefined }} onClick={onClick} />
}

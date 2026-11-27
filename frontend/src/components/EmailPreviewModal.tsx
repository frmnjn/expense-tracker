import { Group, Loader, Modal, Stack, Text } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { useEmailBody } from '../hooks/useEmailImports'
import { ErrorState } from './ErrorState'

function toHtmlDocument(body: string): string {
  if (/<html[\s>]/i.test(body)) return body
  return `<!doctype html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<style>
  html, body { margin: 0; padding: 12px; background: #fff; color: #111; font-family: Arial, sans-serif; font-size: 14px; }
  img { max-width: 100%; height: auto; }
  table { max-width: 100%; }
  a { color: #1c7ed6; }
</style>
</head>
<body>${body}</body>
</html>`
}

export function EmailPreviewModal({
  opened,
  emailId,
  subject,
  sender,
  onClose,
}: {
  opened: boolean
  emailId: string
  subject?: string
  sender?: string
  onClose: () => void
}) {
  const isMobile = useMediaQuery('(max-width: 48em)')
  const { data, isPending, isError, refetch } = useEmailBody(emailId, opened)

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={subject?.trim() || 'Email asli'}
      fullScreen={isMobile}
      centered={!isMobile}
      size="xl"
      zIndex={3000}
      styles={
        isMobile
          ? { body: { height: 'calc(100dvh - 60px)', display: 'flex', flexDirection: 'column' } }
          : undefined
      }
    >
      {sender && (
        <Text size="xs" c="dimmed" mb="xs" truncate title={sender}>
          {sender}
        </Text>
      )}
      {isPending ? (
        <Group justify="center" py="xl">
          <Loader />
        </Group>
      ) : isError ? (
        <ErrorState message="Gagal memuat email asli." onRetry={() => void refetch()} />
      ) : data?.format === 'HTML' ? (
        <iframe
          title="Preview email"
          sandbox=""
          referrerPolicy="no-referrer"
          srcDoc={toHtmlDocument(data.body)}
          style={{
            width: '100%',
            height: isMobile ? '100%' : '70vh',
            border: 0,
            borderRadius: 8,
            background: '#fff',
            flex: isMobile ? 1 : undefined,
          }}
        />
      ) : (
        <Stack
          style={{
            height: isMobile ? '100%' : '70vh',
            overflow: 'auto',
            background: '#fff',
            borderRadius: 8,
            padding: 12,
          }}
        >
          <Text
            size="sm"
            style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', color: '#111' }}
          >
            {data?.body || 'Email tidak memiliki isi teks.'}
          </Text>
        </Stack>
      )}
    </Modal>
  )
}

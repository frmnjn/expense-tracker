import {
  Button,
  Container,
  Group,
  Loader,
  Paper,
  Stack,
  Table,
  Text,
} from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { IconDownload, IconEye, IconFileText, IconRefresh } from '@tabler/icons-react'
import dayjs from 'dayjs'
import { PageHeader } from '../components/PageHeader'
import { AppPagination } from '../components/AppPagination'
import { ErrorState } from '../components/ErrorState'
import { useReports } from '../hooks/useReports'
import { getReportDownloadUrl, getReportViewUrl } from '../services/reports'
import type { PeriodReport } from '../types/report'

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`
}

function ReportActions({ report }: { report: PeriodReport }) {
  return (
    <Group gap="xs" justify="flex-end" wrap="nowrap">
      <Button
        component="a"
        href={getReportViewUrl(report.period)}
        target="_blank"
        rel="noreferrer"
        variant="light"
        size="xs"
        leftSection={<IconEye size={15} />}
      >
        Lihat
      </Button>
      <Button
        component="a"
        href={getReportDownloadUrl(report.period)}
        download={report.fileName}
        target="_blank"
        rel="noreferrer"
        variant="light"
        size="xs"
        leftSection={<IconDownload size={15} />}
      >
        Unduh
      </Button>
    </Group>
  )
}

function ReportsPage() {
  const { data, isPending, isError, isFetching, refetch } = useReports()
  const isMobile = useMediaQuery('(max-width: 48em)')
  const reports = data?.reports ?? []

  return (
    <Container
      size="md"
      py="lg"
      pb={{ base: 'calc(96px + env(safe-area-inset-bottom, 0px))', sm: 'lg' }}
    >
      <Stack gap="lg">
        <PageHeader
          eyebrow="Laporan"
          title="Laporan Periode"
          subtitle="Semua report PDF periode yang sudah dibuat."
          right={
            <Button
              variant="light"
              size="sm"
              leftSection={<IconRefresh size={16} />}
              onClick={() => refetch()}
              loading={isFetching}
            >
              Segarkan
            </Button>
          }
        />

        {isPending ? (
          <Group justify="center" py="xl">
            <Loader />
          </Group>
        ) : isError ? (
          <ErrorState message="Gagal memuat daftar laporan." onRetry={() => refetch()} />
        ) : reports.length === 0 ? (
          <Paper withBorder p="xl" radius="md">
            <Stack align="center" gap="sm">
              <IconFileText size={40} aria-hidden />
              <Text fw={600}>Belum ada laporan</Text>
              <Text size="sm" c="dimmed" ta="center">
                Report PDF akan muncul di sini setelah periode baru dimulai.
              </Text>
            </Stack>
          </Paper>
        ) : (
          <>
            <Text size="sm" c="dimmed">
              {reports.length} laporan
            </Text>
            <AppPagination data={reports}>
              {(pageData) =>
                isMobile ? (
                  <Stack gap="sm">
                    {pageData.map((report) => (
                      <Paper key={report.period} withBorder p="md" radius="md">
                        <Stack gap="sm">
                          <Group justify="space-between" align="flex-start" wrap="nowrap">
                            <Stack gap={2} style={{ minWidth: 0 }}>
                              <Text fw={700}>{report.period}</Text>
                              <Text size="xs" c="dimmed">
                                {dayjs(report.generatedAt).format('DD MMM YYYY HH:mm')}
                              </Text>
                            </Stack>
                            <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                              {formatSize(report.sizeBytes)}
                            </Text>
                          </Group>
                          <Group grow gap="xs">
                            <Button
                              component="a"
                              href={getReportViewUrl(report.period)}
                              target="_blank"
                              rel="noreferrer"
                              variant="light"
                              leftSection={<IconEye size={16} />}
                            >
                              Lihat
                            </Button>
                            <Button
                              component="a"
                              href={getReportDownloadUrl(report.period)}
                              download={report.fileName}
                              target="_blank"
                              rel="noreferrer"
                              variant="light"
                              leftSection={<IconDownload size={16} />}
                            >
                              Unduh
                            </Button>
                          </Group>
                        </Stack>
                      </Paper>
                    ))}
                  </Stack>
                ) : (
                  <Paper withBorder radius="md">
                    <Table highlightOnHover>
                      <Table.Thead>
                        <Table.Tr>
                          <Table.Th>Periode</Table.Th>
                          <Table.Th>Dibuat</Table.Th>
                          <Table.Th>Ukuran</Table.Th>
                          <Table.Th ta="right">Aksi</Table.Th>
                        </Table.Tr>
                      </Table.Thead>
                      <Table.Tbody>
                        {pageData.map((report) => (
                          <Table.Tr key={report.period}>
                            <Table.Td>
                              <Text fw={600}>{report.period}</Text>
                            </Table.Td>
                            <Table.Td>
                              {dayjs(report.generatedAt).format('DD MMM YYYY HH:mm')}
                            </Table.Td>
                            <Table.Td>{formatSize(report.sizeBytes)}</Table.Td>
                            <Table.Td ta="right">
                              <ReportActions report={report} />
                            </Table.Td>
                          </Table.Tr>
                        ))}
                      </Table.Tbody>
                    </Table>
                  </Paper>
                )
              }
            </AppPagination>
          </>
        )}
      </Stack>
    </Container>
  )
}

export default ReportsPage

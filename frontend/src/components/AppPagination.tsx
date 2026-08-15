import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Group, Pagination, Select } from '@mantine/core'

const PAGE_SIZE_OPTIONS = [5, 10, 15, 25]

/**
 * Pagination reusable: membagi data menjadi halaman, menampilkan daftar lewat
 * render-prop, plus kontrol Pagination (siblings+ellipsis) & pemilih ukuran
 * halaman dalam satu baris. Warna mengikuti theme (Mantine primaryColor).
 */
export function AppPagination<T>({
  data,
  defaultPageSize = 10,
  children,
}: {
  data: T[]
  defaultPageSize?: number
  children: (pageData: T[]) => ReactNode
}) {
  const [activePage, setActivePage] = useState(1)
  const [pageSize, setPageSize] = useState(defaultPageSize)

  const totalPages = Math.max(1, Math.ceil(data.length / pageSize))
  const safePage = Math.min(activePage, totalPages)
  const pageData = data.slice((safePage - 1) * pageSize, safePage * pageSize)

  useEffect(() => {
    if (activePage !== safePage) {
      setActivePage(safePage)
    }
  }, [activePage, safePage])

  const changePageSize = (value: string | null) => {
    setPageSize(Number(value) || defaultPageSize)
    setActivePage(1)
  }

  return (
    <>
      {children(pageData)}
      {totalPages > 1 && (
        <Group justify="space-between" wrap="wrap" gap="sm">
          <Pagination
            value={safePage}
            onChange={setActivePage}
            total={totalPages}
            size="md"
            radius="md"
            siblings={1}
            boundaries={1}
          />
          <Select
            size="xs"
            w={110}
            aria-label="Baris per halaman"
            value={String(pageSize)}
            onChange={changePageSize}
            data={PAGE_SIZE_OPTIONS.map((n) => ({ value: String(n), label: `${n} / halaman` }))}
          />
        </Group>
      )}
    </>
  )
}

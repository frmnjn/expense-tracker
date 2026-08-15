import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Group, Pagination, Select } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'

const PAGE_SIZE_OPTIONS = [5, 10, 15, 25]

/**
 * Pagination reusable: membagi data menjadi halaman, menampilkan daftar lewat
 * render-prop, plus kontrol Pagination (siblings+ellipsis) & pemilih ukuran
 * halaman dalam satu baris. Warna mengikuti theme (Mantine primaryColor).
 * Default ukuran halaman: 5 di mobile, 10 di desktop (bisa di-override via
 * defaultPageSize).
 */
export function AppPagination<T>({
  data,
  defaultPageSize,
  children,
}: {
  data: T[]
  defaultPageSize?: number
  children: (pageData: T[]) => ReactNode
}) {
  const isMobile = useMediaQuery('(max-width: 48em)')
  const [activePage, setActivePage] = useState(1)
  const [pageSize, setPageSize] = useState<number>(defaultPageSize ?? 10)
  const userSetRef = useRef(false)

  // Default ikut device sampai user memilih sendiri lewat selector
  // (menangani useMediaQuery yang sempat false di render pertama).
  useEffect(() => {
    if (!userSetRef.current && defaultPageSize === undefined) {
      setPageSize(isMobile ? 5 : 10)
    }
  }, [isMobile, defaultPageSize])

  const totalPages = Math.max(1, Math.ceil(data.length / pageSize))
  const safePage = Math.min(activePage, totalPages)
  const pageData = data.slice((safePage - 1) * pageSize, safePage * pageSize)

  useEffect(() => {
    if (activePage !== safePage) {
      setActivePage(safePage)
    }
  }, [activePage, safePage])

  const changePageSize = (value: string | null) => {
    userSetRef.current = true
    setPageSize(Number(value) || (defaultPageSize ?? (isMobile ? 5 : 10)))
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

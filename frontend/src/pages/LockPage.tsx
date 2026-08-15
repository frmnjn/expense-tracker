import { useState } from 'react'
import { Box, Button, Container, Group, SimpleGrid, Stack, Text } from '@mantine/core'
import { useNavigate } from 'react-router-dom'
import apiClient from '../services/api'
import { setAccessCode } from '../utils/access'

const MAX_PIN = 12
const MIN_PIN = 4

const digits = ['1', '2', '3', '4', '5', '6', '7', '8', '9']

export default function LockPage() {
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const press = (d: string) => {
    setError(null)
    setPin((p) => (p.length >= MAX_PIN ? p : p + d))
  }

  const backspace = () => {
    setError(null)
    setPin((p) => p.slice(0, -1))
  }

  const submit = async () => {
    if (pin.length < MIN_PIN || loading) return
    setLoading(true)
    try {
      const res = await apiClient.get('/options', {
        headers: { 'X-Access-Code': pin },
        validateStatus: () => true,
      })
      if (res.status === 200) {
        setAccessCode(pin)
        navigate('/', { replace: true })
      } else {
        setError('PIN salah, coba lagi')
        setPin('')
      }
    } catch {
      setError('Terjadi kesalahan, coba lagi')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--mantine-color-body)',
        padding: 'var(--mantine-spacing-md)',
      }}
    >
      <Container size="xs" px={0}>
        <Stack align="center" gap="lg">
          <Stack align="center" gap={2}>
            <Text fw={800} fz="lg">
              Expense Tracker
            </Text>
            <Text size="sm" c="dimmed">
              Masukkan PIN
            </Text>
          </Stack>

          <Box w="100%" maw={300} style={{ border: '1px solid var(--mantine-color-default-border)', borderRadius: 'var(--mantine-radius-xl)', padding: 'var(--mantine-spacing-xl)' }}>
            <Stack align="center" gap="lg">
              <GroupPin pin={pin} error={!!error} />

              {error && (
                <Text size="sm" c="red" ta="center">
                  {error}
                </Text>
              )}

              <Box w="100%" maw={240}>
                <SimpleGrid cols={3} spacing="sm">
                  {digits.map((d) => (
                    <Button key={d} size="lg" variant="default" radius="md" onClick={() => press(d)} aria-label={`Digit ${d}`}>
                      {d}
                    </Button>
                  ))}
                  <Button size="lg" variant="subtle" radius="md" onClick={backspace} aria-label="Hapus digit">
                    ⌫
                  </Button>
                  <Button size="lg" variant="default" radius="md" onClick={() => press('0')} aria-label="Digit 0">
                    0
                  </Button>
                  <Button size="lg" color="blue" radius="md" onClick={submit} loading={loading} disabled={pin.length < MIN_PIN}>
                    Buka
                  </Button>
                </SimpleGrid>
              </Box>
            </Stack>
          </Box>
        </Stack>
      </Container>
    </Box>
  )
}

function GroupPin({ pin, error }: { pin: string; error: boolean }) {
  return (
    <Group gap={10} justify="center" align="center">
      {Array.from({ length: 6 }).map((_, i) => (
        <Box
          key={i}
          w={14}
          h={14}
          style={{
            borderRadius: '50%',
            background:
              i < pin.length
                ? 'var(--mantine-color-blue-6)'
                : 'var(--mantine-color-default-border)',
            boxShadow: error ? '0 0 0 1px var(--mantine-color-red-6)' : undefined,
          }}
        />
      ))}
    </Group>
  )
}

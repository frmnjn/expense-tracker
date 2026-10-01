import { Component, type ReactNode } from 'react'
import { Button, Code, Container, Stack, Text, Title } from '@mantine/core'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  error: Error | null
}

// React mewajibkan class component untuk error boundary.
export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error): void {
    console.error('Unhandled UI error', error)
  }

  render(): ReactNode {
    if (!this.state.error) {
      return this.props.children
    }

    return (
      <Container size="sm" py="xl">
        <Stack align="center" gap="md" mt="xl">
          <Title order={2}>Terjadi kesalahan</Title>
          <Text c="dimmed" ta="center">
            Halaman gagal ditampilkan. Muat ulang aplikasi untuk mencoba lagi.
          </Text>
          <Code block w="100%" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
            {this.state.error.message}
          </Code>
          <Button onClick={() => window.location.reload()}>Muat ulang</Button>
        </Stack>
      </Container>
    )
  }
}

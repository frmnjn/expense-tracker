import { useEffect, useState } from 'react'
import { Button, Modal, Stack, Text } from '@mantine/core'

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

function isIos() {
  if (typeof navigator === 'undefined') return false
  const ua = navigator.userAgent.toLowerCase()
  const isIPhone = ua.includes('iphone')
  const isIPad = ua.includes('ipad') || (ua.includes('macintosh') && navigator.maxTouchPoints > 1)
  return isIPhone || isIPad
}

function isStandalone() {
  return typeof window !== 'undefined' && window.matchMedia('(display-mode: standalone)').matches
}

function InstallButton() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null)
  const [opened, setOpened] = useState(false)

  useEffect(() => {
    const handlePrompt = (event: Event) => {
      event.preventDefault()
      setDeferredPrompt(event as BeforeInstallPromptEvent)
    }
    window.addEventListener('beforeinstallprompt', handlePrompt)
    return () => window.removeEventListener('beforeinstallprompt', handlePrompt)
  }, [])

  if (isStandalone()) {
    return null
  }

  const handleClick = async () => {
    if (deferredPrompt) {
      await deferredPrompt.prompt()
      const choice = await deferredPrompt.userChoice
      if (choice.outcome === 'accepted') {
        setDeferredPrompt(null)
      }
      return
    }
    setOpened(true)
  }

  return (
    <>
      <Button variant="subtle" size="xs" onClick={handleClick}>
        Install App
      </Button>

      <Modal opened={opened} onClose={() => setOpened(false)} title="Cara Install di HP" centered>
        <Stack>
          {isIos() ? (
            <Text size="sm">
              1. Buka Safari, lalu tap ikon <b>Share</b> (kotak dengan panah ke atas) di bagian bawah
              browser.
              <br />
              2. Pilih <b>Add to Home Screen</b>.
              <br />
              3. Tap <b>Add</b> di pojok kanan atas.
            </Text>
          ) : (
            <Text size="sm">
              1. Buka menu browser (titik tiga di pojok kanan atas).
              <br />
              2. Pilih <b>Install app</b> atau <b>Add to Home screen</b>.
              <br />
              3. Tap <b>Install</b>.
            </Text>
          )}
        </Stack>
      </Modal>
    </>
  )
}

export default InstallButton

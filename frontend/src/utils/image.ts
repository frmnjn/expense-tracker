const HEIC_EXT = /\.(heic|heif)$/i

function isHeic(file: File): boolean {
  const type = file.type.toLowerCase()
  return type === 'image/heic' || type === 'image/heif' || HEIC_EXT.test(file.name)
}

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('decode-failed'))
    }
    img.src = url
  })
}

/**
 * Backend hanya menerima jpeg/png/webp/gif. Foto galeri iPhone sering HEIC,
 * jadi konversi ke JPEG di browser sebelum upload.
 */
export async function toUploadableImage(file: File): Promise<File> {
  if (!isHeic(file)) return file

  let img: HTMLImageElement
  try {
    img = await loadImage(file)
  } catch {
    throw new Error('Format HEIC tidak didukung browser ini. Ubah ke JPEG terlebih dahulu.')
  }

  const canvas = document.createElement('canvas')
  canvas.width = img.naturalWidth
  canvas.height = img.naturalHeight
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Gagal mengonversi gambar HEIC.')

  ctx.drawImage(img, 0, 0)
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92))
  if (!blob) throw new Error('Gagal mengonversi gambar HEIC.')

  return new File([blob], file.name.replace(HEIC_EXT, '.jpg'), { type: 'image/jpeg' })
}

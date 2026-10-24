export interface TutorialStep {
  title: string
  body: string
}

export interface Tutorial {
  label: string
  steps: TutorialStep[]
}

export const demoTutorialStep: TutorialStep = {
  title: 'Mode demo',
  body: 'Data di halaman ini hanya contoh dan tidak bisa diubah. Badge DEMO di kanan atas menandakan mode read-only.',
}

export const tutorials: Record<string, Tutorial> = {
  '/catat': {
    label: 'Catat Pengeluaran',
    steps: [
      {
        title: 'Selamat datang',
        body: 'Expense Tracker membantu kamu mencatat pengeluaran, memantau budget, dan melihat laporan keuangan. Panduan singkat ini muncul sekali di tiap halaman dan bisa dilewati.',
      },
      {
        title: 'Kenali menunya',
        body: 'Di HP, menu ada di bagian bawah: Dashboard, Scan, Catat, Suara, Inbox, dan Riwayat. Di desktop, menu ada di sidebar kiri.',
      },
      {
        title: 'Tiga cara mencatat',
        body: 'Isi manual di halaman ini, foto struk lewat Scan, atau rekam suara lewat Suara. Transaksi email bank masuk ke Inbox untuk diperiksa dulu.',
      },
      {
        title: 'Isi form pengeluaran',
        body: 'Pilih waktu, lalu isi nama, budget, kategori, dan nominal. Sisa budget langsung terlihat sebelum disimpan, dan isian form tersimpan otomatis sebagai draft.',
      },
    ],
  },
  '/scan': {
    label: 'Scan Struk',
    steps: [
      {
        title: 'Scan struk dengan AI',
        body: 'Unggah foto atau PDF struk, lalu AI membaca merchant, tanggal, dan nominalnya.',
      },
      {
        title: 'Ambil atau pilih file',
        body: 'Tekan Ambil Foto untuk memotret struk, atau Galeri / PDF untuk memilih file yang sudah ada.',
      },
      {
        title: 'Periksa sebelum simpan',
        body: 'Struk berstatus Perlu Review wajib dicek: pilih budget/kategori dan koreksi nominal. Selesai berarti sudah jadi pengeluaran, sedangkan Bukan Invoice berarti bukan struk.',
      },
    ],
  },
  '/voice': {
    label: 'Catat dengan Suara',
    steps: [
      {
        title: 'Catat pakai suara',
        body: 'Tekan tombol rekam dan sebutkan pengeluaran, misalnya: "Makan siang 35 ribu, budget harian".',
      },
      {
        title: 'Dengar dulu, baru kirim',
        body: 'Setelah merekam, dengarkan hasilnya. Rekam ulang kalau kurang jelas, atau kirim ke AI untuk diisi otomatis.',
      },
      {
        title: 'Review dan izin mikrofon',
        body: 'Hasil AI berstatus Perlu Review perlu dicek sebelum disimpan. Mikrofon hanya aktif lewat HTTPS/localhost dan setelah kamu mengizinkan aksesnya.',
      },
    ],
  },
  '/inbox': {
    label: 'Inbox Email',
    steps: [
      {
        title: 'Transaksi dari email bank',
        body: 'Notifikasi transaksi dari email bank otomatis diubah menjadi kandidat pengeluaran di halaman ini.',
      },
      {
        title: 'Segarkan inbox',
        body: 'Tekan tombol refresh untuk mengambil email terbaru. Prosesnya berjalan di latar belakang, jadi kamu bisa lanjut memakai aplikasi.',
      },
      {
        title: 'Periksa sebelum impor',
        body: 'Transaksi Perlu Review bisa dicek lalu diimpor menjadi pengeluaran atau dibuang. Gagal bisa dicoba ulang, Selesai berarti sudah tersimpan.',
      },
    ],
  },
  '/dashboard': {
    label: 'Dashboard',
    steps: [
      {
        title: 'Ringkasan periode',
        body: 'Lihat total pengeluaran periode ini dan bandingkan dengan periode sebelumnya. Ganti periode lewat pilihan di kanan atas.',
      },
      {
        title: 'Kelola budget',
        body: 'Dari kartu budget kamu bisa menambah budget, top up saldo, mengatur kategori, edit, atau hapus. Warna indikator menunjukkan kesehatan pemakaian budget.',
      },
      {
        title: 'Tren, insight, dan laporan',
        body: 'Grafik tren dan insight membantu melihat pola belanja. Tombol Laporan membuka report PDF, dan Atur Gajian menyesuaikan periode dengan tanggal gajian.',
      },
    ],
  },
  '/riwayat': {
    label: 'Riwayat Pengeluaran',
    steps: [
      {
        title: 'Semua transaksi',
        body: 'Halaman ini menampilkan seluruh pengeluaran pada periode terpilih, lengkap dengan foto atau catatan suara terkait.',
      },
      {
        title: 'Cari dan filter',
        body: 'Cari berdasarkan nama, urutkan berdasarkan waktu/nominal, dan filter berdasarkan budget atau kategori. Pilihan kategori menyesuaikan budget yang dipilih.',
      },
      {
        title: 'Edit atau hapus',
        body: 'Buka detail untuk melihat lampiran, atau edit/hapus transaksi lewat tombol aksi. Di HP, aksi tampil di kartu agar tidak perlu scroll horizontal.',
      },
    ],
  },
  '/laporan': {
    label: 'Laporan Periode',
    steps: [
      {
        title: 'Report PDF per periode',
        body: 'Setiap periode yang sudah selesai punya laporan PDF berisi ringkasan pengeluaran.',
      },
      {
        title: 'Lihat atau unduh',
        body: 'Tekan Lihat untuk membuka PDF di tab baru, atau Unduh untuk menyimpannya. Laporan baru muncul setelah periode berikutnya dimulai.',
      },
    ],
  },
}
